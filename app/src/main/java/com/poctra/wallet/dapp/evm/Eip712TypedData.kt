package com.poctra.wallet.dapp.evm

import com.poctra.wallet.core.EvmCrypto
import com.poctra.wallet.data.Wallet
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale

data class Eip712Field(
    val name: String,
    val type: String,
)

data class Eip712Payload(
    val primaryType: String,
    val types: Map<String, List<Eip712Field>>,
    val domain: Map<String, Any?>,
    val message: Map<String, Any?>,
)

data class Eip712Review(
    val primaryType: String,
    val domainSummary: String,
    val typeSummary: String,
    val messagePreview: String,
    val chainId: BigInteger?,
    val verifyingContract: String?,
    val signingHash: String,
    val warnings: List<String>,
)

data class Eip712TypedData(
    val primaryType: String,
    val domain: Map<String, Any?>,
    val message: Map<String, Any?>,
    val types: Map<String, List<Eip712Field>>,
    val signingHash: ByteArray,
    val review: Eip712Review,
)

/**
 * Small, bounded EIP-712 v4 encoder used only by the EVM dApp provider.
 * It deliberately has no access to Octra RPC, bridge, or swap state.
 */
object Eip712TypedDataCodec {
    private const val MaxJsonChars = 262_144
    private const val MaxTypeCount = 64
    private const val MaxFieldsPerType = 64
    private const val MaxArrayLength = 256
    private const val MaxStringBytes = 65_536
    private const val MaxPreviewChars = 4_096
    private const val MaxNestingDepth = 16

    private val TwoPow256 = BigInteger.ONE.shiftLeft(256)
    private val DomainFieldOrder = listOf("name", "version", "chainId", "verifyingContract", "salt")

    fun parse(raw: Any): Eip712TypedData {
        val root = when (raw) {
            is JSONObject -> {
                require(raw.toString().length <= MaxJsonChars) { "Typed data is too large to review safely." }
                raw
            }
            is String -> {
                require(raw.length <= MaxJsonChars) { "Typed data is too large to review safely." }
                runCatching { JSONObject(raw) }
                    .getOrElse { throw IllegalArgumentException("Typed data JSON is invalid.") }
            }
            else -> throw IllegalArgumentException("Typed data must be a JSON object.")
        }
        val typesObject = root.optJSONObject("types")
            ?: throw IllegalArgumentException("Typed data types are required.")
        val domain = root.optJSONObject("domain") ?: JSONObject()
        val message = root.optJSONObject("message")
            ?: root.optJSONObject("value")
            ?: throw IllegalArgumentException("Typed data message is required.")
        val primaryType = root.optString("primaryType").trim()
        val parsedTypes = parseTypes(typesObject)
        return parse(Eip712Payload(primaryType, parsedTypes, jsonObjectMap(domain), jsonObjectMap(message)))
    }

    fun parse(payload: Eip712Payload): Eip712TypedData {
        val primaryType = payload.primaryType.trim()
        require(isTypeName(primaryType) && primaryType != "EIP712Domain") {
            "Typed data primaryType is invalid."
        }
        val parsedTypes = payload.types
        require(parsedTypes.size in 1..MaxTypeCount) { "Typed data has too many type definitions." }
        require(parsedTypes.values.all { it.size <= MaxFieldsPerType }) { "Typed data type has too many fields." }
        val domain = payload.domain
        val message = payload.message
        val domainFields = parsedTypes["EIP712Domain"] ?: inferDomainFields(domain)
        validateDomainSchema(domainFields)
        val allTypes = LinkedHashMap(parsedTypes)
        allTypes["EIP712Domain"] = domainFields
        require(allTypes.containsKey(primaryType)) { "Typed data primaryType is not defined." }
        validateFields(allTypes)
        validateObjectShape("domain", domain, domainFields)
        validateObjectShape(primaryType, message, allTypes.getValue(primaryType))
        validateTypeGraph(primaryType, allTypes, emptySet())

        val domainSeparator = hashStruct("EIP712Domain", domain, allTypes)
        val messageHash = hashStruct(primaryType, message, allTypes)
        val signingHash = EvmCrypto.keccak(byteArrayOf(0x19, 0x01) + domainSeparator + messageHash)
        val chainId = domain["chainId"]
            ?.let { parseInteger(it, "domain.chainId") }
        val verifyingContract = (domain["verifyingContract"] as? String)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.also { require(isAddress(it)) { "Typed data verifyingContract is invalid." } }
        val warnings = buildList {
            if (primaryType.contains("permit", ignoreCase = true) ||
                allTypes.values.flatten().any { it.name.equals("spender", ignoreCase = true) }
            ) {
                add("This signature may grant token spending permission.")
            }
            if (chainId == null) add("This signature is not bound to a chain ID.")
            if (verifyingContract == null) add("This signature has no verifying contract.")
            if (domain["name"] !is String && domain["version"] !is String) {
                add("The signing domain has no name or version.")
            }
        }
        val review = Eip712Review(
            primaryType = primaryType,
            domainSummary = domainSummary(domain, chainId, verifyingContract),
            typeSummary = typeSummary(allTypes, primaryType),
            messagePreview = previewJson(message),
            chainId = chainId,
            verifyingContract = verifyingContract,
            signingHash = "0x${signingHash.toHex()}",
            warnings = warnings,
        )
        return Eip712TypedData(primaryType, domain, message, allTypes, signingHash, review)
    }

    fun parseAndSign(wallet: Wallet, raw: Any): Pair<Eip712TypedData, String> {
        val typedData = parse(raw)
        return typedData to EvmCrypto().signRecoverableHash(wallet, typedData.signingHash)
    }

    private fun parseTypes(typesObject: JSONObject): Map<String, List<Eip712Field>> {
        val names = typesObject.keys().asSequence().toList()
        require(names.size in 1..MaxTypeCount) { "Typed data has too many type definitions." }
        val result = LinkedHashMap<String, List<Eip712Field>>()
        names.forEach { typeName ->
            require(isTypeName(typeName)) { "Typed data type name is invalid." }
            val fieldsJson = typesObject.optJSONArray(typeName)
                ?: throw IllegalArgumentException("Typed data fields for $typeName are invalid.")
            require(fieldsJson.length() in 0..MaxFieldsPerType) { "Typed data type $typeName has too many fields." }
            val fields = buildList {
                val seen = HashSet<String>()
                for (index in 0 until fieldsJson.length()) {
                    val field = fieldsJson.optJSONObject(index)
                        ?: throw IllegalArgumentException("Typed data field $typeName[$index] is invalid.")
                    val fieldName = field.optString("name").trim()
                    val fieldType = field.optString("type").trim()
                    require(isFieldName(fieldName) && seen.add(fieldName)) {
                        "Typed data field name is invalid in $typeName."
                    }
                    parseType(fieldType)
                    add(Eip712Field(fieldName, fieldType))
                }
            }
            result[typeName] = fields
        }
        return result
    }

    private fun jsonObjectMap(value: JSONObject, depth: Int = 0): Map<String, Any?> {
        require(depth <= MaxNestingDepth) { "Typed data nesting is too deep." }
        return value.keys().asSequence().associateWith { key -> jsonValue(value.opt(key), depth + 1) }
    }

    private fun jsonValue(value: Any?, depth: Int): Any? {
        require(depth <= MaxNestingDepth) { "Typed data nesting is too deep." }
        return when (value) {
        null,
        JSONObject.NULL -> null
        is JSONObject -> jsonObjectMap(value, depth)
        is JSONArray -> buildList {
            require(value.length() <= MaxArrayLength) { "Typed data array is too large." }
            for (index in 0 until value.length()) add(jsonValue(value.opt(index), depth + 1))
        }
        else -> value
        }
    }

    private fun inferDomainFields(domain: Map<String, Any?>): List<Eip712Field> {
        val keys = domain.keys
        require(keys.all { it in DomainFieldOrder }) {
            "Typed data domain contains an unknown field; define EIP712Domain explicitly."
        }
        return DomainFieldOrder.filter { domain.containsKey(it) }.map { key ->
            Eip712Field(key, when (key) {
                "name", "version" -> "string"
                "chainId" -> "uint256"
                "verifyingContract" -> "address"
                "salt" -> "bytes32"
                else -> error("Unsupported domain field.")
            })
        }
    }

    private fun validateFields(types: Map<String, List<Eip712Field>>) {
        types.values.flatten().forEach { field ->
            val spec = parseType(field.type)
            if (!isPrimitive(spec.base)) {
                require(types.containsKey(spec.base)) { "Typed data type ${spec.base} is not defined." }
            }
        }
    }

    private fun validateDomainSchema(fields: List<Eip712Field>) {
        val expected = mapOf(
            "name" to "string",
            "version" to "string",
            "chainId" to "uint256",
            "verifyingContract" to "address",
            "salt" to "bytes32",
        )
        fields.forEach { field ->
            val expectedType = expected[field.name]
                ?: throw IllegalArgumentException("EIP712Domain contains an unsupported field: ${field.name}.")
            require(parseType(field.type).canonical() == expectedType) {
                "EIP712Domain.${field.name} must use type $expectedType."
            }
        }
    }

    private fun validateTypeGraph(
        typeName: String,
        types: Map<String, List<Eip712Field>>,
        stack: Set<String>,
    ) {
        require(stack.size <= MaxNestingDepth) { "Typed data type nesting is too deep." }
        require(typeName !in stack) { "Recursive EIP-712 types are not supported." }
        val nextStack = stack + typeName
        types.getValue(typeName).forEach { field ->
            val base = parseType(field.type).base
            if (!isPrimitive(base)) validateTypeGraph(base, types, nextStack)
        }
    }

    private fun validateObjectShape(name: String, value: Map<String, Any?>, fields: List<Eip712Field>) {
        val allowed = fields.map { it.name }.toSet()
        value.keys.forEach { key ->
            require(key in allowed) { "Typed data contains an unexpected $name field: $key." }
        }
        fields.forEach { field ->
            require(value.containsKey(field.name) && value[field.name] != null) {
                "Typed data field $name.${field.name} is missing."
            }
        }
    }

    private fun hashStruct(
        typeName: String,
        value: Map<String, Any?>,
        types: Map<String, List<Eip712Field>>,
    ): ByteArray {
        val encoded = ByteArrayOutputStream()
        encoded.write(typeHash(typeName, types))
        types.getValue(typeName).forEach { field ->
            encoded.write(encodeValue(parseType(field.type), value[field.name], types, "$typeName.${field.name}"))
        }
        return EvmCrypto.keccak(encoded.toByteArray())
    }

    private fun encodeValue(
        spec: TypeSpec,
        raw: Any?,
        types: Map<String, List<Eip712Field>>,
        path: String,
    ): ByteArray {
        require(raw != null) { "Typed data field $path is null." }
        if (spec.dimensions.isNotEmpty()) {
            val array = raw as? List<*> ?: throw IllegalArgumentException("Typed data field $path must be an array.")
            val dimension = spec.dimensions.last()
            if (dimension != null) require(array.size == dimension) { "Typed data array $path has an invalid length." }
            require(array.size <= MaxArrayLength) { "Typed data array $path is too large." }
            val inner = spec.copy(dimensions = spec.dimensions.dropLast(1))
            val bytes = ByteArrayOutputStream()
            array.forEachIndexed { index, item ->
                bytes.write(encodeValue(inner, item, types, "$path[$index]"))
            }
            return EvmCrypto.keccak(bytes.toByteArray())
        }
        if (!isPrimitive(spec.base)) {
            val structValue = raw as? Map<*, *>
                ?: throw IllegalArgumentException("Typed data struct $path is invalid.")
            val normalized = structValue.entries.associate { (key, value) ->
                require(key is String) { "Typed data struct $path has an invalid field name." }
                key to value
            }
            validateObjectShape(spec.base, normalized, types.getValue(spec.base))
            return hashStruct(spec.base, normalized, types)
        }
        return encodeAtomic(spec.base, raw, path)
    }

    private fun encodeAtomic(base: String, raw: Any, path: String): ByteArray {
        val normalized = normalizeBase(base)
        return when {
            normalized == "address" -> {
                val address = raw.toString()
                require(isAddress(address)) { "Typed data address $path is invalid." }
                word(BigInteger(address.drop(2), 16))
            }
            normalized == "bool" -> {
                val value = when (raw) {
                    is Boolean -> raw
                    is String -> raw.toBooleanStrictOrNull()
                    else -> null
                } ?: throw IllegalArgumentException("Typed data boolean $path is invalid.")
                word(if (value) BigInteger.ONE else BigInteger.ZERO)
            }
            normalized == "string" -> {
                val bytes = (raw as? String)?.toByteArray(Charsets.UTF_8)
                    ?: throw IllegalArgumentException("Typed data string $path is invalid.")
                require(bytes.size <= MaxStringBytes) { "Typed data string $path is too large." }
                EvmCrypto.keccak(bytes)
            }
            normalized == "bytes" -> {
                val bytes = parseHex(raw, path)
                require(bytes.size <= MaxStringBytes) { "Typed data bytes $path is too large." }
                EvmCrypto.keccak(bytes)
            }
            normalized.startsWith("bytes") -> {
                val size = normalized.removePrefix("bytes").toInt()
                val bytes = parseHex(raw, path)
                require(bytes.size == size) { "Typed data $path must contain exactly $size bytes." }
                bytes + ByteArray(32 - bytes.size)
            }
            normalized.startsWith("uint") || normalized.startsWith("int") -> {
                val unsigned = normalized.startsWith("uint")
                val width = normalized.removePrefix(if (unsigned) "uint" else "int").toInt()
                val value = parseInteger(raw, path)
                if (unsigned) {
                    require(value >= BigInteger.ZERO && value < BigInteger.ONE.shiftLeft(width)) {
                        "Typed data unsigned integer $path is out of range."
                    }
                    word(value)
                } else {
                    val min = BigInteger.ONE.shiftLeft(width - 1).negate()
                    val max = BigInteger.ONE.shiftLeft(width - 1).subtract(BigInteger.ONE)
                    require(value in min..max) { "Typed data signed integer $path is out of range." }
                    word(value.mod(TwoPow256))
                }
            }
            else -> throw IllegalArgumentException("Typed data type $base is not supported.")
        }
    }

    private fun typeHash(typeName: String, types: Map<String, List<Eip712Field>>): ByteArray =
        EvmCrypto.keccak(encodeType(typeName, types).toByteArray(Charsets.UTF_8))

    private fun encodeType(typeName: String, types: Map<String, List<Eip712Field>>): String {
        val dependencies = linkedSetOf<String>()
        collectDependencies(typeName, types, dependencies)
        val ordered = listOf(typeName) + dependencies.filter { it != typeName }.sorted()
        return ordered.joinToString("") { name ->
            val fields = types.getValue(name).joinToString(",") { "${parseType(it.type).canonical()} ${it.name}" }
            "$name($fields)"
        }
    }

    private fun collectDependencies(typeName: String, types: Map<String, List<Eip712Field>>, result: MutableSet<String>) {
        types.getValue(typeName).forEach { field ->
            val base = parseType(field.type).base
            if (!isPrimitive(base) && result.add(base)) collectDependencies(base, types, result)
        }
    }

    private fun parseType(raw: String): TypeSpec {
        var base = raw.trim()
        val dimensions = mutableListOf<Int?>()
        while (base.endsWith("]")) {
            val open = base.lastIndexOf('[')
            require(open >= 0) { "Typed data type $raw is invalid." }
            val length = base.substring(open + 1, base.length - 1)
            val dimension = if (length.isEmpty()) null else length.toIntOrNull()
            require(length.isEmpty() || dimension != null && dimension > 0) { "Typed data array type $raw is invalid." }
            dimensions.add(0, dimension)
            base = base.substring(0, open)
        }
        require(dimensions.size <= MaxNestingDepth) { "Typed data array nesting is too deep." }
        require(isTypeName(base) || isPrimitive(base)) { "Typed data type $raw is invalid." }
        if (base == "uint") base = "uint256"
        if (base == "int") base = "int256"
        return TypeSpec(base, dimensions)
    }

    private fun isPrimitive(base: String): Boolean {
        val normalized = normalizeBase(base)
        return normalized == "address" || normalized == "bool" || normalized == "string" || normalized == "bytes" ||
            normalized.matches(Regex("bytes([1-9]|[12]\\d|3[0-2])")) ||
            normalized.matches(Regex("uint(8|16|24|32|40|48|56|64|72|80|88|96|104|112|120|128|136|144|152|160|168|176|184|192|200|208|216|224|232|240|248|256)")) ||
            normalized.matches(Regex("int(8|16|24|32|40|48|56|64|72|80|88|96|104|112|120|128|136|144|152|160|168|176|184|192|200|208|216|224|232|240|248|256)"))
    }

    private fun normalizeBase(base: String): String = base.lowercase(Locale.US).let {
        when (it) {
            "uint" -> "uint256"
            "int" -> "int256"
            else -> it
        }
    }

    private fun parseInteger(raw: Any, path: String): BigInteger {
        val text = when (raw) {
            is Double -> {
                require(raw.isFinite() && raw % 1.0 == 0.0) { "Typed data integer $path is invalid." }
                BigDecimal(raw.toString()).toBigIntegerExact().toString()
            }
            is Float -> {
                require(raw.isFinite() && raw % 1f == 0f) { "Typed data integer $path is invalid." }
                BigDecimal(raw.toString()).toBigIntegerExact().toString()
            }
            else -> raw.toString().trim()
        }
        return runCatching {
            if (text.startsWith("0x", ignoreCase = true)) {
                require(text.length > 2) { "Typed data integer $path is invalid." }
                BigInteger(text.drop(2), 16)
            } else {
                BigInteger(text)
            }
        }.getOrElse { throw IllegalArgumentException("Typed data integer $path is invalid.") }
    }

    private fun parseHex(raw: Any, path: String): ByteArray {
        val text = raw.toString()
        require(text.startsWith("0x", ignoreCase = true) && text.length % 2 == 0) {
            "Typed data bytes $path must be 0x-prefixed even-length hex."
        }
        val clean = text.drop(2)
        require(clean.all { it in "0123456789abcdefABCDEF" }) { "Typed data bytes $path are invalid." }
        return ByteArray(clean.length / 2) { index -> clean.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }

    private fun word(value: BigInteger): ByteArray {
        val bytes = value.toByteArray().let { if (it.firstOrNull() == 0.toByte()) it.drop(1).toByteArray() else it }
        require(bytes.size <= 32) { "Typed data value does not fit in 256 bits." }
        return ByteArray(32 - bytes.size) + bytes
    }

    private fun isAddress(value: String): Boolean = value.matches(Regex("^0x[0-9a-fA-F]{40}$"))
    private fun isTypeName(value: String): Boolean = value.matches(Regex("^[A-Za-z_][A-Za-z0-9_]{0,63}$"))
    private fun isFieldName(value: String): Boolean = value.matches(Regex("^[A-Za-z_][A-Za-z0-9_]{0,127}$"))

    private fun domainSummary(domain: Map<String, Any?>, chainId: BigInteger?, verifyingContract: String?): String =
        buildList {
            (domain["name"] as? String)?.takeIf { it.isNotBlank() }?.let { add("name=$it") }
            (domain["version"] as? String)?.takeIf { it.isNotBlank() }?.let { add("version=$it") }
            chainId?.let { add("chainId=${it.toString()}") }
            verifyingContract?.let { add("verifyingContract=$it") }
            (domain["salt"] as? String)?.takeIf { it.isNotBlank() }?.let { add("salt=$it") }
        }.joinToString(" | ").ifBlank { "No signing domain metadata" }

    private fun typeSummary(types: Map<String, List<Eip712Field>>, primaryType: String): String {
        val ordered = listOf(primaryType) + types.keys.filter { it != "EIP712Domain" && it != primaryType }.sorted()
        return ordered.joinToString("; ") { name ->
                val fields = types.getValue(name).joinToString(", ") { "${it.type} ${it.name}" }
                "$name($fields)"
            }
    }

    private fun previewJson(value: Map<String, Any?>): String {
        val preview = prettyValue(value, 0)
        return if (preview.length <= MaxPreviewChars) preview else "${preview.take(MaxPreviewChars - 18)}... [truncated]"
    }

    private fun prettyValue(value: Any?, depth: Int): String {
        if (depth > 8) return "..."
        return when (value) {
            null -> "null"
            is Map<*, *> -> value.entries.joinToString(", ", "{", "}") { (key, item) ->
                "\"$key\": ${prettyValue(item, depth + 1)}"
            }
            is List<*> -> value.joinToString(", ", "[", "]") { prettyValue(it, depth + 1) }
            is String -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            else -> value.toString()
        }
    }

    private data class TypeSpec(val base: String, val dimensions: List<Int?>)

    private fun TypeSpec.canonical(): String = base + dimensions.joinToString("") { length ->
        if (length == null) "[]" else "[$length]"
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
