const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const {
  MAX_B64_BYTES,
  circleAssetOuFromB64Len,
  collectProjectFiles,
  manifestHash,
  printUsage,
  resolveContext,
  runProjectBuild,
  validateBuildOutput,
} = require('./lib/circle-tooling.cjs')

const context = resolveContext(process.argv.slice(2))
if (context.help) {
  printUsage('deploy-circle.cjs')
  process.exit(0)
}

const OCTRA_ID_ROOT = context.toolchainRoot
const { buildConfig } = require(path.join(OCTRA_ID_ROOT, 'scripts/lib/env'))
const { setRpcUrl, getRpcUrl } = require(path.join(OCTRA_ID_ROOT, 'scripts/lib/rpc'))
const aml = require(path.join(OCTRA_ID_ROOT, 'scripts/lib/aml'))

const BASE58 = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'
const SEALED_MAGIC = Buffer.from('OCRS1', 'utf8')
const SEALED_KEY_ID = process.env.CIRCLE_KEY_ID || 'default'
const SEALED_PASSPHRASE = process.env.CIRCLE_PASSPHRASE || 'octra-circle-demo'
const SEALED_PADDING = process.env.CIRCLE_PADDING || '16k'
const CIRCLE_PRIVACY_CLASS = process.env.CIRCLE_PRIVACY_CLASS || context.projectConfig.privacyClass || 'public'
const CIRCLE_BROWSER_MODE = process.env.CIRCLE_BROWSER_MODE || context.projectConfig.browserMode || 'gateway_allowed'
const CIRCLE_RESOURCE_MODE = process.env.CIRCLE_RESOURCE_MODE || context.projectConfig.resourceMode || 'public_resources'
const CIRCLE_ASSET_MODE = process.env.CIRCLE_ASSET_MODE || context.projectConfig.assetMode || 'plain'
const CIRCLE_SHELL_ONLY = process.env.CIRCLE_SHELL_ONLY === '1'
const OUT_PATH = context.deploymentPath

function writeDeployment(deployment) {
  if (fs.existsSync(OUT_PATH) && context.args['force-new'] === true) {
    const archiveDir = path.join(path.dirname(OUT_PATH), 'archive')
    fs.mkdirSync(archiveDir, { recursive: true })
    const current = JSON.parse(fs.readFileSync(OUT_PATH, 'utf8'))
    const circleTag = String(current.circleId || 'unknown').replace(/[^a-z0-9_-]/gi, '')
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
    const archivePath = path.join(
      archiveDir,
      `${path.basename(OUT_PATH, '.json')}.${circleTag}.${timestamp}.json`,
    )
    fs.copyFileSync(OUT_PATH, archivePath)
    log('archived', archivePath)
  }

  const tempPath = `${OUT_PATH}.${process.pid}.tmp`
  fs.writeFileSync(tempPath, `${JSON.stringify(deployment, null, 2)}\n`)
  fs.renameSync(tempPath, OUT_PATH)
}

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
  runProjectBuild(context)
  const runtimeManifest = validateBuildOutput(context)
  const { config, missing } = buildConfig()
  if (missing.length) {
    throw new Error(`missing deployer config: ${missing.join(', ')}`)
  }
  if (config.network !== context.network) {
    throw new Error(`network mismatch: requested=${context.network}, toolchain=${config.network}`)
  }

  setRpcUrl(config.rpcUrl)
  const keypair = aml.keyPairFromBase64(config.privKey)
  const payload = buildCircleDeployPayload()
  const files = collectProjectFiles(context)

  console.log('--- circle deploy ---')
  log('network', config.network)
  log('type', context.projectType)
  log('project', context.projectName)
  log('contract', runtimeManifest.contract || 'not set')
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

  if (fs.existsSync(OUT_PATH)) {
    if (context.dryRun) {
      log('existing', 'deployment metadata already exists')
    } else if (context.args['force-new'] !== true) {
      throw new Error(`deployment metadata exists: ${OUT_PATH}; use update-circle.cjs or --force-new`)
    }
  }

  if (context.dryRun) {
    log('deployment', OUT_PATH)
    log('dry_run', 'no transactions submitted')
    return
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
      project: context.projectName,
      projectType: context.projectType,
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
      manifestHash: manifestHash([]),
      deployedAt: new Date().toISOString(),
      shellOnly: true,
    }
    writeDeployment(deployment)

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
      plaintextHash: sealed ? sealed.plaintextHash : asset.plaintextHash,
      ou,
      txHash: hash,
    })
    nonce += 1
  }

  const deployment = {
    project: context.projectName,
    projectType: context.projectType,
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
    manifestHash: manifestHash(assets),
    deployedAt: new Date().toISOString(),
  }
  writeDeployment(deployment)

  console.log('--- done ---')
  log('uri', deployment.uri)
  log('saved', OUT_PATH)
}

main().catch((err) => {
  console.error(`deploy failed: ${err.message}`)
  process.exit(1)
})
