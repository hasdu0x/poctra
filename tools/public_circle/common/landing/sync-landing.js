const crypto = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')

const SOURCE_ROOT = path.resolve(__dirname, '..', '..', '..', 'landing')
const TARGET_ROOT = __dirname
const SOURCE_ASSETS = path.join(SOURCE_ROOT, 'assets')
const TARGET_ASSETS = path.join(TARGET_ROOT, 'assets')
const APK_PART_SIZE = 4 * 1024 * 1024

function requireFile(filePath) {
  if (!fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) {
    throw new Error(`required file not found: ${filePath}`)
  }
}

function sha256(buffer) {
  return crypto.createHash('sha256').update(buffer).digest('hex')
}

function findApk(indexHtml) {
  const match = indexHtml.match(/href=["']\.\/assets\/([^"']+\.apk)["']/i)
  if (!match) throw new Error('landing index does not contain an APK download link')
  const fileName = path.basename(match[1])
  const filePath = path.join(SOURCE_ASSETS, fileName)
  requireFile(filePath)
  return { fileName, filePath }
}

function copyLandingAssets() {
  const resolvedTarget = path.resolve(TARGET_ASSETS)
  if (resolvedTarget !== path.join(path.resolve(TARGET_ROOT), 'assets')) {
    throw new Error(`refusing to replace unexpected asset directory: ${resolvedTarget}`)
  }
  fs.rmSync(resolvedTarget, { recursive: true, force: true })
  fs.mkdirSync(resolvedTarget, { recursive: true })
  fs.cpSync(SOURCE_ASSETS, resolvedTarget, {
    recursive: true,
    filter: (sourcePath) => path.extname(sourcePath).toLowerCase() !== '.apk',
  })
}

function splitApk(apk) {
  const bytes = fs.readFileSync(apk.filePath)
  const apkDirectory = path.join(TARGET_ASSETS, 'apk')
  fs.mkdirSync(apkDirectory, { recursive: true })
  const parts = []

  for (let offset = 0, index = 1; offset < bytes.length; offset += APK_PART_SIZE, index += 1) {
    const partName = `${apk.fileName}.part${String(index).padStart(2, '0')}`
    const partPath = path.join(apkDirectory, partName)
    fs.writeFileSync(partPath, bytes.subarray(offset, Math.min(offset + APK_PART_SIZE, bytes.length)))
    parts.push(`./assets/apk/${partName}`)
  }

  return { bytes, parts, hash: sha256(bytes) }
}

function circleDownloadAnchor(apk, build) {
  return `<a
            class="button button-primary"
            href="#"
            data-apk-download
            data-circle-download-parts="${build.parts.join(',')}"
            data-circle-download-name="${apk.fileName}"
            data-circle-download-type="application/vnd.android.package-archive"
            data-circle-download-sha256="${build.hash}"
          >Download APK</a>`
}

function createCircleIndex(sourceIndex, apk, build) {
  const escapedName = apk.fileName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const apkLink = new RegExp(
    `<a\\b[^>]*href=["']\\.\\/assets\\/${escapedName}["'][^>]*>\\s*Download APK\\s*<\\/a>`,
    'i',
  )
  if (!apkLink.test(sourceIndex)) throw new Error('unable to adapt the APK download link for Circle')
  return sourceIndex.replace(apkLink, circleDownloadAnchor(apk, build))
}

function circleDownloadAdapter(apk, parts) {
  const partList = parts.map((part) => `  ${JSON.stringify(part)},`).join('\n')
  return `

// Circle resources keep the APK in small parts to avoid large RPC responses.
const apkDownload = document.querySelector("[data-apk-download]");
const apkParts = [
${partList}
];

apkDownload?.addEventListener("click", async (event) => {
  event.preventDefault();
  if (apkDownload.dataset.loading === "true") return;

  const originalLabel = apkDownload.textContent;
  apkDownload.dataset.loading = "true";
  apkDownload.setAttribute("aria-disabled", "true");

  try {
    const chunks = [];
    for (let index = 0; index < apkParts.length; index += 1) {
      apkDownload.textContent = \`Preparing \${index + 1}/\${apkParts.length}\`;
      const response = await fetch(apkParts[index]);
      if (!response.ok) throw new Error(\`APK part \${index + 1} is unavailable\`);
      chunks.push(await response.arrayBuffer());
    }

    const url = URL.createObjectURL(new Blob(chunks, {
      type: "application/vnd.android.package-archive",
    }));
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = ${JSON.stringify(apk.fileName)};
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (error) {
    console.error(error);
    apkDownload.textContent = "Retry download";
    return;
  } finally {
    apkDownload.dataset.loading = "false";
    apkDownload.removeAttribute("aria-disabled");
  }

  apkDownload.textContent = originalLabel;
});
`
}

function updateReadme(build) {
  const readmePath = path.join(TARGET_ROOT, 'README.md')
  if (!fs.existsSync(readmePath)) return
  const sizeMiB = (build.bytes.length / 1024 / 1024).toFixed(1)
  const next = fs.readFileSync(readmePath, 'utf8')
    .replace(/The [\d.]+ MiB APK/, `The ${sizeMiB} MiB APK`)
    .replace(/`[0-9A-Fa-f]{64}`(?=\s*\n\s*## Fresh deployment)/, `\`${build.hash.toUpperCase()}\``)
  fs.writeFileSync(readmePath, next)
}

function main() {
  const sourceIndexPath = path.join(SOURCE_ROOT, 'index.html')
  const sourceScriptPath = path.join(SOURCE_ROOT, 'script.js')
  const sourceStylesPath = path.join(SOURCE_ROOT, 'styles.css')
  requireFile(sourceIndexPath)
  requireFile(sourceScriptPath)
  requireFile(sourceStylesPath)

  const sourceIndex = fs.readFileSync(sourceIndexPath, 'utf8')
  const apk = findApk(sourceIndex)
  copyLandingAssets()
  const build = splitApk(apk)

  fs.writeFileSync(path.join(TARGET_ROOT, 'index.html'), createCircleIndex(sourceIndex, apk, build))
  fs.writeFileSync(
    path.join(TARGET_ROOT, 'script.js'),
    `${fs.readFileSync(sourceScriptPath, 'utf8').trimEnd()}${circleDownloadAdapter(apk, build.parts)}`,
  )
  fs.copyFileSync(sourceStylesPath, path.join(TARGET_ROOT, 'styles.css'))
  updateReadme(build)

  console.log(`synced ${SOURCE_ROOT}`)
  console.log(`apk       ${apk.fileName}`)
  console.log(`parts     ${build.parts.length} x up to ${APK_PART_SIZE / 1024 / 1024} MiB`)
  console.log(`sha256    ${build.hash}`)
}

try {
  main()
} catch (error) {
  console.error(`sync failed: ${error.message}`)
  process.exitCode = 1
}
