# poctra public Circle landing

Circle-ready copy of `tools/landing`, deployed as public resources without a
passphrase.

## Devnet

- Circle URI: `oct://oct3A5bhpktxb9y2gepQeGxSbkJspq3Pv178Zyi9tkM1oJx/index.html`
- Circle ID: `oct3A5bhpktxb9y2gepQeGxSbkJspq3Pv178Zyi9tkM1oJx`
- Resource mode: `public_resources`
- Passphrase: not required

## Synchronize

Generate this Circle-ready copy from `tools/landing`:

```powershell
node tools\public_circle\common\landing\sync-landing.js
```

The sync copies the current HTML, CSS, JavaScript, catalog, and visual assets.
It splits the APK into resources of at most 4 MiB and updates the part list and
SHA-256 download metadata automatically.

## Update the existing Circle

Review the changed-resource plan, then upload it:

```powershell
node tools\public_circle\update-circle.cjs --project landing --network devnet --dry-run
node tools\public_circle\update-circle.cjs --project landing --network devnet
```

Unchanged APK parts and static assets are skipped. Confirmed uploads are stored
in a resumable checkpoint under `tools/public_circle/deployments/.checkpoints`.

Deployment metadata is stored at:

```text
tools/public_circle/deployments/common.landing.devnet.json
```

## Fresh deployment

Use the shared root deployer only when a new Circle ID is intentional:

```powershell
node tools\public_circle\deploy-circle.cjs --project landing --network devnet
```
