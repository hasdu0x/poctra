const crypto = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')
const {
  MAX_B64_BYTES,
  circleAssetOuFromB64Len,
  collectProjectFiles,
  manifestHash,
  planResourceChanges,
  printUsage,
  readJson,
  resolveContext,
  runProjectBuild,
  sameResource,
  validateBuildOutput,
  writeJsonAtomic,
} = require('./lib/circle-tooling.cjs')

const context = resolveContext(process.argv.slice(2))
if (context.help) {
  printUsage('update-circle.cjs')
  process.exit(0)
}

const OCTRA_ID_ROOT = context.toolchainRoot
const { buildConfig } = require(path.join(OCTRA_ID_ROOT, 'scripts/lib/env'))
const { setRpcUrl, getRpcUrl } = require(path.join(OCTRA_ID_ROOT, 'scripts/lib/rpc'))
const aml = require(path.join(OCTRA_ID_ROOT, 'scripts/lib/aml'))
const CHECKPOINT_PATH = path.join(
  context.deploymentsRoot,
  '.checkpoints',
  `${path.basename(context.deploymentPath, '.json')}.update.json`,
)

function log(label, value) {
  console.log(`${label.padEnd(14)} ${value}`)
}

function buildSignedCircleTx({ from, to, nonce, ou, message, encryptedData, keypair }) {
  return aml.buildSignedTx({
    from,
    to_: to,
    amount: '0',
    nonce,
    ou,
    timestamp: Date.now() / 1000,
    op_type: 'circle_asset_put',
    message,
    encrypted_data: encryptedData,
  }, keypair)
}

async function submitAndConfirm(tx, explorerHost) {
  const hash = await aml.submitTx(tx)
  log('asset_tx', hash)
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
  if (!fs.existsSync(context.deploymentPath)) {
    throw new Error(`deployment file not found: ${context.deploymentPath}`)
  }
  return readJson(context.deploymentPath)
}

function resourceRecord(local, ou, txHash) {
  return {
    path: local.path,
    contentType: local.contentType,
    bytes: local.bytes,
    keyId: null,
    paddingClass: null,
    plaintextHash: local.plaintextHash,
    ou,
    txHash,
  }
}

function printPaths(label, assets) {
  if (!assets.length) return
  console.log(`${label}:`)
  for (const asset of assets) console.log(`  ${asset.path}`)
}

function checkpointFor(circleId, targetManifestHash, forceAll) {
  const existing = readJson(CHECKPOINT_PATH, null)
  if (existing?.circleId === circleId && Boolean(existing.forceAll) === forceAll) {
    return {
      ...existing,
      targetManifestHash,
      uploaded: Array.isArray(existing.uploaded) ? existing.uploaded : [],
    }
  }
  return {
    circleId,
    project: context.projectName,
    projectType: context.projectType,
    network: context.network,
    targetManifestHash,
    forceAll,
    uploaded: [],
    startedAt: new Date().toISOString(),
  }
}

function removeCheckpoint() {
  if (fs.existsSync(CHECKPOINT_PATH)) fs.unlinkSync(CHECKPOINT_PATH)
}

async function main() {
  runProjectBuild(context)
  const runtimeManifest = validateBuildOutput(context)
  const deployment = readDeployment()
  if (deployment.sealedRead || context.projectConfig.assetMode === 'sealed') {
    throw new Error('changed-only updates currently support public Circle assets only')
  }

  const circleId = process.env.CIRCLE_ID || deployment.circleId
  if (!circleId) throw new Error(`missing circleId in ${context.deploymentPath}`)
  if (deployment.network && deployment.network !== context.network) {
    throw new Error(`deployment file is for ${deployment.network}, not ${context.network}`)
  }
  if (deployment.project && deployment.project !== context.projectName) {
    throw new Error(`deployment file is for ${deployment.project}, not ${context.projectName}`)
  }
  if (deployment.projectType && deployment.projectType !== context.projectType) {
    throw new Error(`deployment file is for ${deployment.projectType}, not ${context.projectType}`)
  }

  const { config, missing } = buildConfig()
  if (missing.length) throw new Error(`missing deployer config: ${missing.join(', ')}`)
  if (config.network !== context.network) {
    throw new Error(`network mismatch: requested=${context.network}, toolchain=${config.network}`)
  }
  if (deployment.deployer && deployment.deployer !== config.deployer) {
    throw new Error(`deployer mismatch: deployment=${deployment.deployer}, env=${config.deployer}`)
  }

  setRpcUrl(config.rpcUrl)
  const localAssets = collectProjectFiles(context)
  const targetManifestHash = manifestHash(localAssets)
  const deployedAssets = deployment.assets || []
  const deployedByPath = new Map(deployedAssets.map((asset) => [asset.path, asset]))
  const localPaths = new Set(localAssets.map((asset) => asset.path))
  const forceAll = context.args['force-all'] === true
  const { changed, unchanged, removed } = planResourceChanges(localAssets, deployedAssets, forceAll)

  console.log('--- circle update plan ---')
  log('network', config.network)
  log('type', context.projectType)
  log('project', context.projectName)
  log('contract', runtimeManifest.contract || 'not set')
  log('rpc', getRpcUrl())
  log('deployer', config.deployer)
  log('circle_id', circleId)
  log('local_files', String(localAssets.length))
  log('changed', String(changed.length))
  log('unchanged', String(unchanged.length))
  log('removed', String(removed.length))
  log('mode', forceAll ? 'force all' : 'hash tracked')
  printPaths('upload', changed)
  printPaths('orphaned on chain', removed)

  if (context.dryRun) {
    log('deployment', context.deploymentPath)
    log('manifest', targetManifestHash)
    log('dry_run', 'no transactions submitted')
    return
  }

  const checkpoint = checkpointFor(circleId, targetManifestHash, forceAll)
  const checkpointByPath = new Map(
    (checkpoint.uploaded || []).map((asset) => [asset.path, asset]),
  )
  const uploadedByPath = new Map()

  if (changed.length) {
    fs.mkdirSync(path.dirname(CHECKPOINT_PATH), { recursive: true })
    writeJsonAtomic(CHECKPOINT_PATH, checkpoint)
    const keypair = aml.keyPairFromBase64(config.privKey)
    const account = await aml.getAccountState(config.deployer)
    let nonce = account.pendingNonce + 1

    for (const asset of changed) {
      const resumed = checkpointByPath.get(asset.path)
      if (sameResource(asset, resumed)) {
        console.log(`--- resume ${asset.path} ---`)
        log('status', 'already confirmed')
        uploadedByPath.set(asset.path, resumed)
        continue
      }

      const body = fs.readFileSync(asset.fullPath)
      const currentHash = crypto.createHash('sha256').update(body).digest('hex')
      if (currentHash !== asset.plaintextHash) {
        throw new Error(`${asset.path} changed while the update was running; restart the update`)
      }
      const bodyB64 = body.toString('base64')
      if (bodyB64.length > MAX_B64_BYTES) throw new Error(`${asset.path} exceeds Circle max asset size`)
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
        message: JSON.stringify(metadata),
        encryptedData: bodyB64,
        keypair,
      })

      console.log(`--- upload ${asset.path} ---`)
      const txHash = await submitAndConfirm(tx, config.explorerHost)
      const record = resourceRecord(asset, ou, txHash)
      uploadedByPath.set(asset.path, record)
      checkpoint.uploaded = [
        ...(checkpoint.uploaded || []).filter((item) => item.path !== asset.path),
        record,
      ]
      checkpoint.updatedAt = new Date().toISOString()
      writeJsonAtomic(CHECKPOINT_PATH, checkpoint)
      nonce += 1
    }
  }

  const assets = localAssets.map((asset) => {
    const uploaded = uploadedByPath.get(asset.path) || checkpointByPath.get(asset.path)
    if (sameResource(asset, uploaded)) return uploaded
    const previous = deployedByPath.get(asset.path)
    if (sameResource(asset, previous)) return previous
    throw new Error(`missing confirmed metadata for ${asset.path}`)
  })
  const now = new Date().toISOString()
  const priorOrphans = Array.isArray(deployment.orphanedAssets) ? deployment.orphanedAssets : []
  const orphanedByPath = new Map(
    [...priorOrphans, ...removed]
      .filter((asset) => !localPaths.has(asset.path))
      .map((asset) => [asset.path, asset]),
  )
  const updateRecord = {
    changedPaths: changed.map((asset) => asset.path),
    unchangedCount: unchanged.length,
    removedPaths: removed.map((asset) => asset.path),
    manifestHash: targetManifestHash,
    updatedAt: now,
  }

  Object.assign(deployment, {
    project: context.projectName,
    projectType: context.projectType,
    network: config.network,
    rpc: config.rpcUrl,
    explorerHost: config.explorerHost,
    deployer: config.deployer,
    circleId,
    uri: `oct://${circleId}/index.html`,
    assets,
    orphanedAssets: [...orphanedByPath.values()].sort((left, right) => left.path.localeCompare(right.path)),
    manifestHash: targetManifestHash,
    updatedAt: now,
    updates: [...(deployment.updates || []), updateRecord].slice(-50),
  })
  writeJsonAtomic(context.deploymentPath, deployment)
  removeCheckpoint()

  console.log('--- done ---')
  log('uploaded', String(changed.length))
  log('uri', deployment.uri)
  log('saved', context.deploymentPath)
  if (removed.length) {
    console.warn('Removed local paths remain addressable on-chain until Circle exposes an asset delete operation.')
  }
}

main().catch((error) => {
  console.error(`update failed: ${error.message}`)
  process.exit(1)
})
