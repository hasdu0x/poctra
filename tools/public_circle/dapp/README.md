# Circle dApp projects

This directory contains wallet-connected Circle applications. Shared deploy and
update scripts live one level above in `tools/public_circle`.

```text
dapp/
  architecture.md
  sync-project.cjs
  octraid/
```

## Sync Octra Identity

```powershell
node tools\public_circle\dapp\sync-project.cjs `
  --project octraid `
  --source C:\Users\Administrator\Documents\Devs\octra.id\frontend
```

The sync preserves Circle-specific configuration, dependencies, and generated
output. Build and deployment remain separate operations.

## Build and update

```powershell
node tools\public_circle\update-circle.cjs `
  --project octraid --network devnet --build --dry-run

node tools\public_circle\update-circle.cjs `
  --project octraid --network devnet --build
```

The updater validates `dist/runtime-config.json`, requires an exact network
match, and uploads only files whose hash or content type changed.

For a new Circle ID, use the root `deploy-circle.cjs`. Mainnet writes require
`--confirm-mainnet`.
