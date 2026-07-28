param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
)

$ErrorActionPreference = "Stop"

$patterns = @(
    @{ Name = "Private key PEM"; Regex = "-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----" },
    @{ Name = "Infura project key in URL"; Regex = "infura\.io/v3/[A-Za-z0-9_-]{16,}" },
    @{ Name = "Etherscan key assignment"; Regex = '(?i)(etherscan(_api)?_?key|ETHERSCAN_KEYS)\s*[:=]\s*[''"]?[A-Za-z0-9]{24,}' },
    @{ Name = "Google service-account private key"; Regex = '"private_key"\s*:\s*"-----BEGIN PRIVATE KEY' }
)

$blockedNames = @(".env", "keystore.properties")
$blockedExtensions = @(".jks", ".keystore", ".p12", ".pfx")
$textExtensions = @(
    ".c", ".cc", ".cpp", ".css", ".gradle", ".html", ".java", ".js", ".json",
    ".kts", ".kt", ".md", ".mjs", ".properties", ".ps1", ".sh", ".toml", ".ts",
    ".tsx", ".txt", ".xml", ".yaml", ".yml"
)

$candidateFiles = & git -C $Root ls-files --cached --others --exclude-standard
if ($LASTEXITCODE -ne 0) { throw "Unable to enumerate repository files." }

$findings = [System.Collections.Generic.List[string]]::new()
foreach ($relativePath in $candidateFiles) {
    if ($relativePath -eq "tools/security/scan-secrets.ps1") { continue }
    $fullPath = Join-Path $Root $relativePath
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) { continue }

    $item = Get-Item -LiteralPath $fullPath
    if ($item.Name -in $blockedNames -or $item.Extension.ToLowerInvariant() -in $blockedExtensions) {
        $findings.Add("Blocked secret-bearing file: $relativePath")
        continue
    }
    if ($item.Extension.ToLowerInvariant() -notin $textExtensions) { continue }

    foreach ($pattern in $patterns) {
        $matches = Select-String -LiteralPath $fullPath -Pattern $pattern.Regex -AllMatches
        foreach ($match in $matches) {
            $findings.Add("$($pattern.Name): ${relativePath}:$($match.LineNumber)")
        }
    }
}

if ($findings.Count -gt 0) {
    $findings | Sort-Object -Unique | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host "Secret scan passed for $($candidateFiles.Count) tracked and untracked paths."
