const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')

process.env.AML_NETWORK = process.env.AML_NETWORK || 'devnet'

const OCTRA_ID_ROOT = process.env.OCTRA_ID_ROOT || 'C:\\Users\\Administrator\\Documents\\Devs\\octra.id'
const { buildConfig } = require(path.join(OCTRA_ID_ROOT, 'scripts/lib/env'))
const { setRpcUrl, getRpcUrl } = require(path.join(OCTRA_ID_ROOT, 'scripts/lib/rpc'))
const aml = require(path.join(OCTRA_ID_ROOT, 'scripts/lib/aml'))

const PROJECT_ROOT = __dirname
const ROOT = path.resolve(PROJECT_ROOT, process.env.CIRCLE_SOURCE_DIR || 'dist')
const DEPLOYMENT_PATH = path.join(PROJECT_ROOT, process.env.CIRCLE_DEPLOYMENT_FILE || 'deployment.devnet.json')
const MAX_RAW_BYTES = 33554432
const MAX_B64_BYTES = Math.ceil(MAX_RAW_BYTES / 3) * 4

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
      continue
    }
    if (!entry.isFile()) continue

    const relative = path.relative(baseDir, fullPath).replaceAll(path.sep, '/')
    out.push({
      path: `/${relative}`,
      fullPath,
      contentType: contentTypeFor(fullPath),
    })
  }

  return out.sort((a, b) => {
    if (a.path === '/index.html') return 1
    if (b.path === '/index.html') return -1
    return a.path.localeCompare(b.path)
  })
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

function readDeployment() {
  if (!fs.existsSync(DEPLOYMENT_PATH)) {
    throw new Error(`deployment file not found: ${DEPLOYMENT_PATH}`)
  }
  return JSON.parse(fs.readFileSync(DEPLOYMENT_PATH, 'utf8'))
}

async function main() {
  if (!fs.existsSync(ROOT) || !fs.statSync(ROOT).isDirectory()) {
    throw new Error(`build output not found: ${ROOT}. Run npm run build first.`)
  }

  const deployment = readDeployment()
  const circleId = process.env.CIRCLE_ID || deployment.circleId
  if (!circleId) throw new Error('missing circleId in deployment.devnet.json')

  const { config, missing } = buildConfig()
  if (missing.length) throw new Error(`missing deployer config: ${missing.join(', ')}`)
  if (config.network !== 'devnet') {
    throw new Error(`refusing to update circle on ${config.network}; set AML_NETWORK=devnet`)
  }
  if (deployment.network && deployment.network !== 'devnet') {
    throw new Error(`deployment file is for ${deployment.network}, not devnet`)
  }
  if (deployment.deployer && deployment.deployer !== config.deployer) {
    throw new Error(`deployer mismatch: deployment=${deployment.deployer}, env=${config.deployer}`)
  }

  setRpcUrl(config.rpcUrl)
  const keypair = aml.keyPairFromBase64(config.privKey)
  const files = collectFiles(ROOT)

  console.log('--- circle asset update ---')
  log('network', config.network)
  log('rpc', getRpcUrl())
  log('deployer', config.deployer)
  log('circle_id', circleId)
  log('files', String(files.length))
  log('mode', 'public asset replace')

  const account = await aml.getAccountState(config.deployer)
  let nonce = account.pendingNonce + 1
  const assets = []

  for (const asset of files) {
    const body = fs.readFileSync(asset.fullPath)
    const bodyB64 = body.toString('base64')
    if (bodyB64.length > MAX_B64_BYTES) {
      throw new Error(`${asset.path} exceeds circle max asset size`)
    }

    const ou = circleAssetOuFromB64Len(bodyB64.length)
    const metadata = {
      path: asset.path,
      content_type: asset.contentType,
      encoding: 'identity',
    }
    const tx = buildSignedCircleTx({
      from: config.deployer,
      to: circleId,
      nonce,
      ou,
      opType: 'circle_asset_put',
      message: JSON.stringify(metadata),
      encryptedData: bodyB64,
      keypair,
    })

    console.log(`--- upload ${asset.path} ---`)
    const hash = await submitAndConfirm('asset_tx', tx, config.explorerHost)
    assets.push({
      path: asset.path,
      contentType: asset.contentType,
      bytes: fs.statSync(asset.fullPath).size,
      keyId: null,
      paddingClass: null,
      plaintextHash: crypto.createHash('sha256').update(body).digest('hex'),
      ou,
      txHash: hash,
    })
    nonce += 1
  }

  const update = {
    network: config.network,
    rpc: config.rpcUrl,
    explorerHost: config.explorerHost,
    deployer: config.deployer,
    circleId,
    uri: `oct://${circleId}/index.html`,
    assets,
    updatedAt: new Date().toISOString(),
  }

  deployment.network = config.network
  deployment.rpc = config.rpcUrl
  deployment.explorerHost = config.explorerHost
  deployment.deployer = config.deployer
  deployment.circleId = circleId
  deployment.uri = update.uri
  deployment.assets = assets
  deployment.updatedAt = update.updatedAt
  deployment.updates = [...(deployment.updates || []), update]
  fs.writeFileSync(DEPLOYMENT_PATH, `${JSON.stringify(deployment, null, 2)}\n`)

  console.log('--- done ---')
  log('uri', update.uri)
  log('saved', DEPLOYMENT_PATH)
}

main().catch((err) => {
  console.error(`update failed: ${err.message}`)
  process.exit(1)
})
