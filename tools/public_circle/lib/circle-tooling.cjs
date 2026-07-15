const crypto = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')
const { spawnSync } = require('node:child_process')

const PUBLIC_CIRCLE_ROOT = path.resolve(__dirname, '..')
const DEPLOYMENTS_ROOT = path.join(PUBLIC_CIRCLE_ROOT, 'deployments')
const PROJECT_TYPES = ['dapp', 'common']
const DEFAULT_TOOLCHAIN_ROOT = 'C:\\Users\\Administrator\\Documents\\Devs\\octra.id'
const MAX_RAW_BYTES = 33_554_432
const MAX_B64_BYTES = Math.ceil(MAX_RAW_BYTES / 3) * 4
const NETWORK_DEFAULTS = {
  devnet: {
    rpc: 'http://165.227.225.79:8080',
    explorer: 'https://devnet.octrascan.io',
  },
  mainnet: {
    rpc: 'https://octra.network',
    explorer: 'https://octrascan.io',
  },
}

function parseArgs(argv) {
  const args = {}
  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index]
    if (!token.startsWith('--')) continue
    const [rawKey, inlineValue] = token.slice(2).split('=', 2)
    if (inlineValue !== undefined) {
      args[rawKey] = inlineValue
      continue
    }
    const next = argv[index + 1]
    if (next && !next.startsWith('--')) {
      args[rawKey] = next
      index += 1
    } else {
      args[rawKey] = true
    }
  }
  return args
}

function assertSimpleName(value, label) {
  if (!value || !/^[a-z0-9][a-z0-9_-]*$/i.test(value)) {
    throw new Error(`${label} must contain only letters, numbers, dash, or underscore`)
  }
  return value
}

function assertInside(parent, candidate, label, allowParent = false) {
  const relative = path.relative(parent, candidate)
  if ((!allowParent && !relative) || relative.startsWith('..') || path.isAbsolute(relative)) {
    throw new Error(`${label} must be inside ${parent}`)
  }
  return candidate
}

function readJson(file, fallback = {}) {
  if (!fs.existsSync(file)) return fallback
  return JSON.parse(fs.readFileSync(file, 'utf8'))
}

function writeJsonAtomic(file, value) {
  fs.mkdirSync(path.dirname(file), { recursive: true })
  const temporary = `${file}.${process.pid}.tmp`
  fs.writeFileSync(temporary, `${JSON.stringify(value, null, 2)}\n`)
  fs.renameSync(temporary, file)
}

function resolveProjectType(projectName, requestedType) {
  if (requestedType) {
    const type = String(requestedType).toLowerCase()
    if (!PROJECT_TYPES.includes(type)) throw new Error('type must be dapp or common')
    return type
  }

  const matches = PROJECT_TYPES.filter((type) => {
    const candidate = path.join(PUBLIC_CIRCLE_ROOT, type, projectName)
    return fs.existsSync(candidate) && fs.statSync(candidate).isDirectory()
  })
  if (matches.length === 1) return matches[0]
  if (matches.length > 1) throw new Error(`project ${projectName} exists in dapp and common; pass --type`)
  throw new Error(`project not found in dapp or common: ${projectName}`)
}

function resolveContext(argv) {
  const args = parseArgs(argv)
  if (args.help) return { help: true }

  const projectName = assertSimpleName(String(args.project || process.env.CIRCLE_PROJECT || ''), 'project')
  const projectType = resolveProjectType(projectName, args.type || process.env.CIRCLE_PROJECT_TYPE)
  const network = String(args.network || process.env.CIRCLE_NETWORK || process.env.AML_NETWORK || 'devnet').toLowerCase()
  if (network !== 'devnet' && network !== 'mainnet') throw new Error('network must be devnet or mainnet')
  const dryRun = args['dry-run'] === true
  if (
    network === 'mainnet'
    && !dryRun
    && args['confirm-mainnet'] !== true
    && process.env.CIRCLE_CONFIRM_MAINNET !== '1'
  ) {
    throw new Error('mainnet writes require --confirm-mainnet or CIRCLE_CONFIRM_MAINNET=1')
  }

  const typeRoot = path.join(PUBLIC_CIRCLE_ROOT, projectType)
  const projectRoot = assertInside(typeRoot, path.resolve(typeRoot, projectName), 'project')
  if (!fs.existsSync(projectRoot) || !fs.statSync(projectRoot).isDirectory()) {
    throw new Error(`project not found: ${projectRoot}`)
  }

  const projectConfigPath = path.join(projectRoot, 'circle.project.json')
  if (!fs.existsSync(projectConfigPath)) throw new Error(`project config not found: ${projectConfigPath}`)
  const projectConfig = readJson(projectConfigPath)
  const sourceDir = String(
    args['source-dir']
      || process.env.CIRCLE_SOURCE_DIR
      || projectConfig.sourceDir
      || (projectType === 'dapp' ? 'dist' : '.'),
  )
  const sourceRoot = assertInside(projectRoot, path.resolve(projectRoot, sourceDir), 'source directory', true)

  fs.mkdirSync(DEPLOYMENTS_ROOT, { recursive: true })
  const deploymentName = String(
    args.deployment
      || process.env.CIRCLE_DEPLOYMENT_FILE
      || `${projectType}.${projectName}.${network}.json`,
  )
  const deploymentPath = assertInside(
    DEPLOYMENTS_ROOT,
    path.resolve(DEPLOYMENTS_ROOT, deploymentName),
    'deployment file',
  )

  const toolchainRoot = path.resolve(
    String(args.toolchain || process.env.OCTRA_TOOLCHAIN_ROOT || process.env.OCTRA_ID_ROOT || DEFAULT_TOOLCHAIN_ROOT),
  )
  const buildScripts = projectConfig.buildScripts || {}
  const buildScript = String(args['build-script'] || buildScripts[network] || '')

  process.env.AML_NETWORK = network
  process.env.AML_RPC = String(args.rpc || process.env.CIRCLE_RPC || NETWORK_DEFAULTS[network].rpc)
  process.env.AML_EXPLORER = String(
    args.explorer || process.env.CIRCLE_EXPLORER || NETWORK_DEFAULTS[network].explorer,
  )
  process.env.OCTRA_ID_ROOT = toolchainRoot

  return {
    args,
    build: args.build === true,
    buildScript,
    deploymentPath,
    deploymentsRoot: DEPLOYMENTS_ROOT,
    dryRun,
    network,
    projectConfig,
    projectConfigPath,
    projectName,
    projectRoot,
    projectType,
    publicCircleRoot: PUBLIC_CIRCLE_ROOT,
    sourceRoot,
    toolchainRoot,
  }
}

function runProjectBuild(context) {
  if (!context.build) return
  if (!context.buildScript) throw new Error(`no ${context.network} build script is configured for ${context.projectName}`)
  if (!/^[a-z0-9:_-]+$/i.test(context.buildScript)) throw new Error('build script contains unsupported characters')
  const command = process.platform === 'win32'
    ? (process.env.ComSpec || 'C:\\Windows\\System32\\cmd.exe')
    : 'npm'
  const args = process.platform === 'win32'
    ? ['/d', '/s', '/c', `npm run ${context.buildScript}`]
    : ['run', context.buildScript]
  const result = spawnSync(command, args, {
    cwd: context.projectRoot,
    stdio: 'inherit',
    shell: false,
  })
  if (result.error) throw result.error
  if (result.status !== 0) throw new Error(`build failed: npm run ${context.buildScript}`)
}

function validateBuildOutput(context) {
  if (!fs.existsSync(context.sourceRoot) || !fs.statSync(context.sourceRoot).isDirectory()) {
    throw new Error(`project source not found: ${context.sourceRoot}`)
  }
  if (context.projectConfig.runtimeManifest === false) return {}

  const manifestName = String(context.projectConfig.runtimeManifest || 'runtime-config.json')
  const manifestPath = assertInside(
    context.sourceRoot,
    path.resolve(context.sourceRoot, manifestName),
    'runtime manifest',
  )
  if (!fs.existsSync(manifestPath)) {
    throw new Error(`runtime manifest not found: ${manifestPath}; rebuild with a Circle mode`)
  }
  const manifest = readJson(manifestPath)
  if (manifest.runtime !== 'circle') {
    throw new Error(`refusing ${manifest.runtime || 'unknown'} build; expected Circle runtime`)
  }
  if (manifest.network !== context.network) {
    throw new Error(`build network mismatch: build=${manifest.network}, requested=${context.network}`)
  }
  return manifest
}

function contentTypeFor(filePath) {
  const lower = filePath.toLowerCase()
  const ext = path.extname(lower)
  if (ext === '.html') return 'text/html; charset=utf-8'
  if (ext === '.css') return 'text/css; charset=utf-8'
  if (ext === '.js' || ext === '.mjs') return 'application/javascript; charset=utf-8'
  if (ext === '.json') return 'application/json; charset=utf-8'
  if (ext === '.svg') return 'image/svg+xml'
  if (ext === '.png') return 'image/png'
  if (ext === '.jpg' || ext === '.jpeg') return 'image/jpeg'
  if (ext === '.webp') return 'image/webp'
  if (ext === '.ico') return 'image/x-icon'
  if (ext === '.ttf') return 'font/ttf'
  if (ext === '.woff') return 'font/woff'
  if (ext === '.woff2') return 'font/woff2'
  if (ext === '.wasm') return 'application/wasm'
  if (ext === '.pdf') return 'application/pdf'
  if (ext === '.apk') return 'application/vnd.android.package-archive'
  if (ext === '.txt') return 'text/plain; charset=utf-8'
  return 'application/octet-stream'
}

function matchesExclude(relativePath, rules) {
  const normalized = relativePath.replaceAll('\\', '/')
  const segments = normalized.split('/')
  if (segments.some((segment) => segment === '.git' || segment === 'node_modules')) return true
  if (normalized === 'circle.project.json' || normalized === '.gitignore') return true
  if (normalized.endsWith('.md') || normalized.endsWith('.log')) return true
  if (segments.some((segment) => segment.startsWith('.env'))) return true

  return rules.some((rawRule) => {
    const rule = String(rawRule || '').replaceAll('\\', '/').replace(/^\.\//, '')
    if (!rule) return false
    if (rule.endsWith('/**')) return normalized === rule.slice(0, -3) || normalized.startsWith(rule.slice(0, -2))
    if (rule.endsWith('/')) return normalized.startsWith(rule)
    return normalized === rule
  })
}

function collectProjectFiles(context) {
  const rules = Array.isArray(context.projectConfig.exclude) ? context.projectConfig.exclude : []
  const out = []

  function visit(directory) {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const fullPath = path.join(directory, entry.name)
      const relative = path.relative(context.sourceRoot, fullPath).replaceAll(path.sep, '/')
      if (matchesExclude(relative, rules)) continue
      if (entry.isDirectory()) {
        visit(fullPath)
        continue
      }
      if (!entry.isFile()) continue
      const bytes = fs.statSync(fullPath).size
      if (bytes > MAX_RAW_BYTES) throw new Error(`/${relative} exceeds Circle max asset size`)
      const body = fs.readFileSync(fullPath)
      out.push({
        path: `/${relative}`,
        fullPath,
        contentType: contentTypeFor(fullPath),
        bytes,
        plaintextHash: crypto.createHash('sha256').update(body).digest('hex'),
      })
    }
  }

  visit(context.sourceRoot)
  return out.sort((left, right) => {
    if (left.path === '/index.html') return 1
    if (right.path === '/index.html') return -1
    return left.path.localeCompare(right.path)
  })
}

function circleAssetOuFromB64Len(wireLength) {
  const rawUpperBound = Math.ceil(wireLength / 4) * 3
  if (rawUpperBound <= 4096) return 5000
  if (rawUpperBound <= 16384) return 10000
  if (rawUpperBound <= 32768) return 20000
  if (rawUpperBound <= 131072) return 40000
  if (rawUpperBound <= 524288) return 80000
  if (rawUpperBound <= 2097152) return 160000
  if (rawUpperBound <= 8388608) return 320000
  return 640000
}

function manifestHash(assets) {
  const stable = assets
    .map((asset) => ({ path: asset.path, contentType: asset.contentType, plaintextHash: asset.plaintextHash }))
    .sort((left, right) => left.path.localeCompare(right.path))
  return crypto.createHash('sha256').update(JSON.stringify(stable)).digest('hex')
}

function sameResource(local, deployed) {
  return Boolean(
    deployed
    && deployed.plaintextHash === local.plaintextHash
    && deployed.contentType === local.contentType,
  )
}

function planResourceChanges(localAssets, deployedAssets, forceAll = false) {
  const deployedByPath = new Map(deployedAssets.map((asset) => [asset.path, asset]))
  const localPaths = new Set(localAssets.map((asset) => asset.path))
  return {
    changed: localAssets.filter((asset) => forceAll || !sameResource(asset, deployedByPath.get(asset.path))),
    unchanged: localAssets.filter((asset) => !forceAll && sameResource(asset, deployedByPath.get(asset.path))),
    removed: deployedAssets.filter((asset) => !localPaths.has(asset.path)),
  }
}

function printUsage(command) {
  console.log(`Usage: node tools/public_circle/${command} --project <name> --network <devnet|mainnet> [options]`)
  console.log('')
  console.log('Options:')
  console.log('  --type <dapp|common>    Optional when the project name is unique')
  console.log('  --build                 Build the project before deployment/update')
  console.log('  --dry-run               Show the plan without sending transactions')
  console.log('  --force-new             Allow a fresh deploy when metadata exists')
  console.log('  --force-all             Re-upload every asset during update')
  console.log('  --build-script <name>   Override the npm build script')
  console.log('  --source-dir <path>     Override project source directory')
  console.log('  --deployment <file>     Override file inside public_circle/deployments')
  console.log('  --rpc <url>             Override AML RPC for the selected network')
  console.log('  --explorer <url>        Override explorer host')
  console.log('  --toolchain <path>      Workspace containing Octra scripts/lib')
  console.log('  --confirm-mainnet       Required for mainnet writes')
}

module.exports = {
  DEPLOYMENTS_ROOT,
  MAX_B64_BYTES,
  PUBLIC_CIRCLE_ROOT,
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
}
