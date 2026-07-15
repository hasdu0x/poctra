const fs = require('node:fs')
const path = require('node:path')
const { resolveContext } = require('../lib/circle-tooling.cjs')

const context = resolveContext(process.argv.slice(2))
if (context.help) {
  console.log('Usage: node sync-project.cjs --project <name> --source <frontend-path>')
  process.exit(0)
}
if (context.projectType !== 'dapp') throw new Error('sync-project.cjs only accepts dapp projects')

const source = path.resolve(String(context.args.source || process.env.CIRCLE_SYNC_SOURCE || ''))
if (!source || !fs.existsSync(source) || !fs.statSync(source).isDirectory()) {
  throw new Error('missing or invalid --source <frontend-path>')
}

const ignoredFiles = new Set([
  '.env',
  '.env.production',
  'README.md',
  'circle.project.json',
  'tsconfig.tsbuildinfo',
])
const ignoredDirectories = new Set(['dist', 'node_modules', '.git'])
const allowedEnvFiles = new Set([
  '.env.circle-devnet',
  '.env.circle-mainnet',
  '.env.web3-devnet',
  '.env.web3-mainnet',
])

function copyTree(from, to) {
  fs.mkdirSync(to, { recursive: true })
  for (const entry of fs.readdirSync(from, { withFileTypes: true })) {
    if (entry.isDirectory() && ignoredDirectories.has(entry.name)) continue
    if (entry.isFile() && ignoredFiles.has(entry.name)) continue
    if (entry.isFile() && entry.name.startsWith('.env') && !allowedEnvFiles.has(entry.name)) continue
    const sourcePath = path.join(from, entry.name)
    const targetPath = path.join(to, entry.name)
    if (entry.isDirectory()) {
      copyTree(sourcePath, targetPath)
    } else if (entry.isFile()) {
      fs.copyFileSync(sourcePath, targetPath)
    }
  }
}

copyTree(source, context.projectRoot)
console.log(`synced ${source}`)
console.log(`to     ${path.relative(context.publicCircleRoot, context.projectRoot)}`)
