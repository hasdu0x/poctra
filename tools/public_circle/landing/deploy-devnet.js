const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')

process.env.AML_NETWORK = process.env.AML_NETWORK || 'devnet'

const OCTRA_ID_ROOT = process.env.OCTRA_ID_ROOT || 'C:\\Users\\Administrator\\Documents\\Devs\\octra.id'
const { buildConfig } = require(path.join(OCTRA_ID_ROOT, 'scripts/lib/env'))
const { setRpcUrl, getRpcUrl } = require(path.join(OCTRA_ID_ROOT, 'scripts/lib/rpc'))
const aml = require(path.join(OCTRA_ID_ROOT, 'scripts/lib/aml'))

const ROOT = __dirname
const MAX_RAW_BYTES = 33554432
const MAX_B64_BYTES = Math.ceil(MAX_RAW_BYTES / 3) * 4
const BASE58 = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'
const SEALED_MAGIC = Buffer.from('OCRS1', 'utf8')
const SEALED_KEY_ID = process.env.CIRCLE_KEY_ID || 'default'
const SEALED_PASSPHRASE = process.env.CIRCLE_PASSPHRASE || 'octra-circle-demo'
const SEALED_PADDING = process.env.CIRCLE_PADDING || '16k'
const CIRCLE_PRIVACY_CLASS = process.env.CIRCLE_PRIVACY_CLASS || 'public'
const CIRCLE_BROWSER_MODE = process.env.CIRCLE_BROWSER_MODE || 'gateway_allowed'
const CIRCLE_RESOURCE_MODE = process.env.CIRCLE_RESOURCE_MODE || 'public_resources'
const CIRCLE_ASSET_MODE = process.env.CIRCLE_ASSET_MODE || 'plain'
const CIRCLE_SHELL_ONLY = process.env.CIRCLE_SHELL_ONLY === '1'
const CIRCLE_DEPLOY_LABEL = process.env.CIRCLE_DEPLOY_LABEL || 'devnet'
const OUT_PATH = path.join(ROOT, `deployment.${CIRCLE_DEPLOY_LABEL}.json`)

function taggedSha256(tag, parts) {
  const chunks = [Buffer.from(tag, 'utf8'), Buffer.from([0])]
  for (const part of parts) {
    const buf = Buffer.isBuffer(part) ? part : Buffer.from(String(part), 'utf8')
    const len = Buffer.alloc(4)
    len.writeUInt32BE(buf.length)
    chunks.push(len, buf)
  }
  return crypto.createHash('sha256').update(Buffer.concat(chunks)).digest()
}

function u64be(value) {
  let n = BigInt(value)
  const out = Buffer.alloc(8)
  for (let i = 7; i >= 0; i -= 1) {
    out[i] = Number(n & 0xffn)
    n >>= 8n
  }
  return out
}

function base58Encode(bytes) {
  let digits = [0]
  for (const byte of bytes) {
    let carry = byte
    for (let j = 0; j < digits.length; j += 1) {
      carry += digits[j] << 8
      digits[j] = carry % 58
      carry = Math.floor(carry / 58)
    }
    while (carry > 0) {
      digits.push(carry % 58)
      carry = Math.floor(carry / 58)
    }
  }

  let encoded = digits.reverse().map((digit) => BASE58[digit]).join('')
  let leadingZeros = 0
  while (leadingZeros < bytes.length && bytes[leadingZeros] === 0) {
    encoded = `1${encoded}`
    leadingZeros += 1
  }
  return encoded
}

function buildCircleDeployPayload() {
  const payload = {
    runtime: 'octb',
    privacy_class: CIRCLE_PRIVACY_CLASS,
    browser_mode: CIRCLE_BROWSER_MODE,
    resource_mode: CIRCLE_RESOURCE_MODE,
    code_b64: null,
    policy_hash: null,
    members_root: null,
    export_policy: null,
    limits: {
      max_stable_bytes: '33554432',
      max_assets_bytes: '33554432',
      max_inline_value: '65536',
      max_wasm_bytes: '33554432',
    },
  }
  if (CIRCLE_PRIVACY_CLASS === '__omit') delete payload.privacy_class
  if (CIRCLE_BROWSER_MODE === '__omit') delete payload.browser_mode
  if (CIRCLE_RESOURCE_MODE === '__omit') delete payload.resource_mode
  return payload
}

function u32be(value) {
  const out = Buffer.alloc(4)
  out.writeUInt32BE(value)
  return out
}

function padTargetBytes(paddingClass) {
  if (paddingClass === '4k') return 4096
  if (paddingClass === '16k') return 16384
  if (paddingClass === '32k') return 32768
  if (paddingClass === '128k') return 131072
  return 0
}

function paddedFrame(plaintext, paddingClass) {
  const bare = Buffer.concat([u32be(plaintext.length), plaintext])
  const target = padTargetBytes(paddingClass)
  if (!target) return bare

  const aligned = Math.ceil(bare.length / target) * target
  if (aligned <= bare.length) return bare
  return Buffer.concat([bare, crypto.randomBytes(aligned - bare.length)])
}

function deriveReadKey(circleId, keyId, passphrase) {
  const salt = Buffer.from(`octra:circle:sealed_read:v1:${circleId}:${keyId}`, 'utf8')
  return crypto.pbkdf2Sync(passphrase, salt, 120000, 32, 'sha256')
}

function encryptSealedBytes(circleId, keyId, passphrase, plaintext, paddingClass) {
  const key = deriveReadKey(circleId, keyId, passphrase)
  const nonce = crypto.randomBytes(12)
  const cipher = crypto.createCipheriv('aes-256-gcm', key, nonce)
  const frame = paddedFrame(plaintext, paddingClass)
  const ciphertext = Buffer.concat([cipher.update(frame), cipher.final()])
  const tag = cipher.getAuthTag()
  return {
    ciphertextB64: Buffer.concat([SEALED_MAGIC, nonce, ciphertext, tag]).toString('base64'),
    plaintextHash: crypto.createHash('sha256').update(plaintext).digest('hex'),
  }
}

function circleIdOfDeploy(deployer, nonce, payload) {
  const payloadJson = JSON.stringify(payload)
  const payloadHash = taggedSha256('octra:circle_deploy_payload:v1', [payloadJson]).toString('hex')
  const seed = taggedSha256('octra:circle_deploy_id:v1', [
    deployer,
    u64be(nonce),
    payloadHash,
  ])
  const b58 = base58Encode(seed)
  const suffix = b58.length >= 44
    ? b58.slice(0, 44)
    : b58.length === 0
      ? '1'.repeat(44)
      : (b58 + b58.repeat(Math.ceil((44 - b58.length) / b58.length))).slice(0, 44)
  return `oct${suffix}`
}

function contentTypeFor(filePath) {
  const ext = path.extname(filePath).toLowerCase()
  if (ext === '.html') return 'text/html; charset=utf-8'
  if (ext === '.css') return 'text/css; charset=utf-8'
  if (ext === '.js') return 'application/javascript; charset=utf-8'
  if (ext === '.json') return 'application/json; charset=utf-8'
  if (ext === '.svg') return 'image/svg+xml'
  if (ext === '.png') return 'image/png'
  if (ext === '.jpg' || ext === '.jpeg') return 'image/jpeg'
  if (ext === '.webp') return 'image/webp'
  if (ext === '.ttf') return 'font/ttf'
  return 'application/octet-stream'
}

function circleAssetOuFromB64Len(wireLen) {
  const rawUpperBound = Math.ceil(wireLen / 4) * 3
  if (rawUpperBound <= 4096) return 5000
  if (rawUpperBound <= 16384) return 10000
  if (rawUpperBound <= 32768) return 20000
  if (rawUpperBound <= 131072) return 40000
  if (rawUpperBound <= 524288) return 80000
  if (rawUpperBound <= 2097152) return 160000
  if (rawUpperBound <= 8388608) return 320000
  return 640000
}

function collectFiles(dir, baseDir = dir) {
  const out = []
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      out.push(...collectFiles(fullPath, baseDir))
    } else if (entry.isFile()) {
      const relative = path.relative(baseDir, fullPath).replaceAll(path.sep, '/')
      if (
        !relative.endsWith('.md')
        && relative !== 'deploy-devnet.js'
        && !/^deployment\..*\.json$/.test(relative)
      ) {
        out.push({
          path: `/${relative}`,
          fullPath,
          contentType: contentTypeFor(fullPath),
        })
      }
    }
  }
  return out.sort((a, b) => a.path.localeCompare(b.path))
}

function buildSignedCircleTx({ from, to, nonce, ou, opType, message, encryptedData, keypair }) {
  return aml.buildSignedTx({
    from,
    to_: to,
    amount: '0',
    nonce,
    ou,
    timestamp: Date.now() / 1000,
    op_type: opType,
    message,
    encrypted_data: encryptedData,
  }, keypair)
}

function log(label, value) {
  console.log(`${label.padEnd(14)} ${value}`)
}

async function submitAndConfirm(label, tx, explorerHost) {
  const hash = await aml.submitTx(tx)
  log(label, hash)
  log('explorer', `${explorerHost}/tx.html?hash=${hash}`)
  const { tx: txInfo } = await aml.waitForTx(hash, {
    timeoutMs: 240000,
    intervalMs: 2500,
    checkReceipt: false,
  })
  log('status', txInfo.status || 'confirmed')
  return hash
}

async function main() {
  const { config, missing } = buildConfig()
  if (missing.length) {
    throw new Error(`missing deployer config: ${missing.join(', ')}`)
  }
  if (config.network !== 'devnet') {
    throw new Error(`refusing to deploy circle on ${config.network}; set AML_NETWORK=devnet`)
  }

  setRpcUrl(config.rpcUrl)
  const keypair = aml.keyPairFromBase64(config.privKey)
  const payload = buildCircleDeployPayload()
  const files = collectFiles(ROOT)

  console.log('--- circle deploy ---')
  log('network', config.network)
  log('rpc', getRpcUrl())
  log('deployer', config.deployer)
  log('files', String(files.length))
  log('privacy', CIRCLE_PRIVACY_CLASS)
  log('browser', CIRCLE_BROWSER_MODE)
  log('resource', CIRCLE_RESOURCE_MODE)
  log('asset_mode', CIRCLE_ASSET_MODE)
  if (CIRCLE_ASSET_MODE === 'sealed') {
    log('key_id', SEALED_KEY_ID)
    log('padding', SEALED_PADDING)
  }

  const account = await aml.getAccountState(config.deployer)
  let nonce = account.pendingNonce + 1
  const circleId = circleIdOfDeploy(config.deployer, nonce, payload)
  log('circle_id', circleId)

  const deployTx = buildSignedCircleTx({
    from: config.deployer,
    to: circleId,
    nonce,
    ou: 200000,
    opType: 'deploy_circle',
    message: JSON.stringify(payload),
    keypair,
  })
  const deployHash = await submitAndConfirm('deploy_tx', deployTx, config.explorerHost)

  if (CIRCLE_SHELL_ONLY) {
    const deployment = {
      network: config.network,
      rpc: config.rpcUrl,
      explorerHost: config.explorerHost,
      deployer: config.deployer,
      circleId,
      uri: `oct://${circleId}/index.html`,
      sealedRead: null,
      publicRead: CIRCLE_ASSET_MODE === 'sealed'
        ? null
        : {
            passphraseRequired: false,
            resourceMode: CIRCLE_RESOURCE_MODE,
          },
      payload,
      deployTxHash: deployHash,
      assets: [],
      deployedAt: new Date().toISOString(),
      shellOnly: true,
    }
    fs.writeFileSync(OUT_PATH, `${JSON.stringify(deployment, null, 2)}\n`)

    console.log('--- done ---')
    log('uri', deployment.uri)
    log('saved', OUT_PATH)
    return
  }

  const assets = []
  nonce += 1
  for (const asset of files) {
    const body = fs.readFileSync(asset.fullPath)
    const sealed = CIRCLE_ASSET_MODE === 'sealed'
      ? encryptSealedBytes(circleId, SEALED_KEY_ID, SEALED_PASSPHRASE, body, SEALED_PADDING)
      : null
    const bodyB64 = sealed ? sealed.ciphertextB64 : body.toString('base64')
    if (bodyB64.length > MAX_B64_BYTES) {
      throw new Error(`${asset.path} exceeds circle max asset size`)
    }
    const ou = circleAssetOuFromB64Len(bodyB64.length)
    const metadata = {
      path: asset.path,
      content_type: asset.contentType,
      encoding: 'identity',
    }
    if (sealed) {
      metadata.key_id = SEALED_KEY_ID
      metadata.plaintext_hash = sealed.plaintextHash
      metadata.padding_class = SEALED_PADDING
    }
    const message = JSON.stringify(metadata)
    const tx = buildSignedCircleTx({
      from: config.deployer,
      to: circleId,
      nonce,
      ou,
      opType: sealed ? 'circle_asset_put_encrypted' : 'circle_asset_put',
      message,
      encryptedData: bodyB64,
      keypair,
    })

    console.log(`--- upload ${asset.path} ---`)
    const hash = await submitAndConfirm('asset_tx', tx, config.explorerHost)
    assets.push({
      path: asset.path,
      contentType: asset.contentType,
      bytes: fs.statSync(asset.fullPath).size,
      keyId: sealed ? SEALED_KEY_ID : null,
      paddingClass: sealed ? SEALED_PADDING : null,
      plaintextHash: sealed ? sealed.plaintextHash : crypto.createHash('sha256').update(body).digest('hex'),
      ou,
      txHash: hash,
    })
    nonce += 1
  }

  const deployment = {
    network: config.network,
    rpc: config.rpcUrl,
    explorerHost: config.explorerHost,
    deployer: config.deployer,
    circleId,
    uri: `oct://${circleId}/index.html`,
    sealedRead: CIRCLE_ASSET_MODE === 'sealed'
      ? {
          keyId: SEALED_KEY_ID,
          passphraseHint: SEALED_PASSPHRASE === 'octra-circle-demo' ? 'octra-circle-demo' : 'set via CIRCLE_PASSPHRASE',
          paddingClass: SEALED_PADDING,
        }
      : null,
    publicRead: CIRCLE_ASSET_MODE === 'sealed'
      ? null
      : {
          passphraseRequired: false,
          resourceMode: CIRCLE_RESOURCE_MODE,
        },
    payload,
    deployTxHash: deployHash,
    assets,
    deployedAt: new Date().toISOString(),
  }
  fs.writeFileSync(OUT_PATH, `${JSON.stringify(deployment, null, 2)}\n`)

  console.log('--- done ---')
  log('uri', deployment.uri)
  log('saved', OUT_PATH)
}

main().catch((err) => {
  console.error(`deploy failed: ${err.message}`)
  process.exit(1)
})
