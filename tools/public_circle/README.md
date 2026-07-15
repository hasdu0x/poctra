# poctra Circle projects

Circle projects share one deployment toolchain while keeping application code
separated by runtime type.

```text
public_circle/
  common/                 Static sites and regular web apps
    landing/
  dapp/                   Wallet-connected Circle dApps
    octraid/
  deployments/            Active deployment metadata and update checkpoints
  lib/                     Shared deployment implementation
  deploy-circle.cjs        Fresh deployments
  update-circle.cjs        Hash-tracked updates
```

Every project contains `circle.project.json`. A dApp normally publishes a
validated `dist/` Circle build, while a common project can publish its project
root without a runtime manifest.

## Common landing

Synchronize the public landing and package its APK first:

```powershell
node tools\public_circle\common\landing\sync-landing.js
```

Preview and apply only changed resources:

```powershell
node tools\public_circle\update-circle.cjs --project landing --network devnet --dry-run
node tools\public_circle\update-circle.cjs --project landing --network devnet
```

Current deployment:

```text
oct://oct3A5bhpktxb9y2gepQeGxSbkJspq3Pv178Zyi9tkM1oJx/index.html
```

## Octra Identity dApp

```powershell
node tools\public_circle\update-circle.cjs `
  --project octraid --network devnet --build --dry-run

node tools\public_circle\update-circle.cjs `
  --project octraid --network devnet --build
```

Current deployment:

```text
oct://oct2EPYSkqjD6f1zLuWhyP6G2rDGB8rZp1nNDirVCqp3drD/index.html
```

## Fresh deployment

Fresh deployment always uploads the complete initial resource set:

```powershell
node tools\public_circle\deploy-circle.cjs `
  --project <name> --network devnet [--build]
```

Existing deployment metadata blocks accidental redeployment. Use
`--force-new` only when a new Circle ID is intentional.

Mainnet writes require `--confirm-mainnet`. Both commands support `--rpc`,
`--explorer`, `--toolchain`, and `--deployment` overrides.

## Changed-only updates

`update-circle.cjs` compares each local resource's SHA-256 and content type with
the active deployment manifest. Unchanged resources are not submitted. Every
confirmed upload is written to a checkpoint, so an interrupted update resumes
without repeating completed transactions. Pass `--force-all` only when every
resource intentionally needs replacement.

If a file disappears locally, it is removed from the active local manifest and
recorded under `orphanedAssets`. The old path remains on-chain because the
current Circle tooling does not expose a documented asset-delete operation.

Signing credentials are resolved from the external Octra toolchain. Never put
private keys in a Circle project, frontend environment, or deployment metadata.
