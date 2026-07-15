# Circle dApp architecture

This directory is a container for Circle-hosted dApps. Each dApp must live in
its own project directory, for example:

```text
dapp/
  architecture.md
  octraid/
  another-dapp/
```

The current `octraid/` project is derived from the web3 frontend at:

```text
C:\Users\Administrator\Documents\Devs\octra.id\frontend
```

## Two execution environments

The web3 dApp and Circle dApp share React components, ONS domain logic, wallet
discovery, and transaction construction. Their read transport and deployment
environment are different.

### Standard web3 dApp

The standard frontend is served over HTTP(S). Contract reads use the RPC URL
configured by `VITE_ONS_RPC`, and the browser sends JSON-RPC requests directly
to that endpoint. The HTTP server or reverse proxy must therefore provide the
correct `/rpc` route and CORS policy.

Write operations do not use the public read transport. They go through the
selected RFC-O-1 wallet provider and `ProviderClient`, allowing the wallet to
show approval, sign the transaction, broadcast it, and report progress.

### Circle dApp

The Circle build is packaged as Circle assets and opened through an `oct://`
resource. The host wallet/browser injects Circle runtime context and may expose
an RPC base through `window.RPC_URL`, `window.OCTRA_RPC_URL`, or
`window.OctraCircle`.

For contract views, `src/lib/rpc.ts` uses this order:

1. Use `OctraCircle.request('program.view', ...)` when the requested program is
   the currently opened Circle itself.
2. In Circle runtime, call `octra_callProgramView` through the runtime RPC for
   another Octra program such as the Octra Identity contract.
3. Fall back to `contract_call` when `octra_callProgramView` is unsupported by
   the current runtime or node.

RPC endpoint selection uses this order:

1. Runtime-injected `window.RPC_URL` or `window.OCTRA_RPC_URL`.
2. The configured `VITE_ONS_RPC` value when one is intentionally supplied.
3. The default Octra network RPC while running in Circle context.
4. Relative `/rpc` only for an ordinary web deployment without another RPC
   source.

The Circle runtime is a read and resource execution environment. It is not a
replacement for wallet authorization. Writes still go through the injected
RFC-O-1 wallet provider, exactly as they do in the standard web3 dApp.

## Performance behavior

The shared RPC helper provides:

- a short-lived view cache to avoid repeating identical contract reads;
- promise deduplication while the same view is already in flight;
- bounded view concurrency to prevent request bursts;
- JSON-RPC batching within a small collection window;
- timeout, retry, exponential backoff, and jitter for transient gateway errors.

Keep these controls in the shared helper. Do not add unbounded `Promise.all`
fan-out in UI components, especially for owner indexes, subdomains, or market
listings.

## Synchronization rules

The standard frontend can now build both runtimes itself. Sync it with:

```powershell
node tools\public_circle\dapp\sync-project.cjs `
  --project octraid `
  --source C:\Users\Administrator\Documents\Devs\octra.id\frontend
```

The sync tool copies shared source and mode-specific public environment files,
while preserving `circle.project.json`, the project README, `node_modules`, and
generated output.

Every build emits `dist/runtime-config.json`. Deployment tools require
`runtime=circle` and an exact network match before reading account state or
submitting a transaction. This prevents a stale mainnet build from being
uploaded to a devnet Circle, and vice versa.

Safe candidates to sync from the standard frontend are:

- `src/App.tsx` and presentation components;
- `src/index.css` and visual assets;
- pure formatting utilities;
- ONS domain logic when its RPC API contract has not changed;
- wallet interfaces that remain RFC-O-1 compatible.

Review these files instead of copying them blindly:

- legacy `.env` and `.env.production`; use the explicit `circle-*` and
  `web3-*` modes instead;
- `circle.project.json`, which belongs to the Circle project wrapper;
- deployment credentials and private RPC configuration;
- generated `dist/`, `tsconfig.tsbuildinfo`, and deployment metadata.

After every UI sync, run the build from the Circle project directory and test
the generated assets through the Circle viewer. A normal Vite preview validates
layout and JavaScript, but it does not validate runtime injection, `oct://`
resource loading, or wallet approval inside the Circle host.

## New Circle dApp checklist

1. Create a dedicated child directory under `dapp/`.
2. Use relative Vite asset paths and keep the build self-contained.
3. Define network, contract, and explorer values explicitly.
4. Keep read calls behind a runtime-aware RPC adapter.
5. Keep writes behind the wallet provider; never ship private keys.
6. Bound concurrency and cache immutable or short-lived views.
7. Treat `deployments/dapp.<project>.<network>.json` as the deployment source
   of truth.
8. Build locally, test in the Circle viewer, then update assets on the existing
   Circle ID when performing a frontend-only release.

## Generic deployment tools

Fresh deployment and asset updates are intentionally outside every project:

```powershell
node tools\public_circle\deploy-circle.cjs `
  --project octraid --network devnet --build --dry-run

node tools\public_circle\update-circle.cjs `
  --project octraid --network devnet --build --dry-run
```

Remove `--dry-run` only when ready to submit transactions. Mainnet additionally
requires `--confirm-mainnet`. Use `--rpc <url>` and `--explorer <url>` for an
explicit endpoint override. Deployment tools resolve the signing implementation
from `OCTRA_TOOLCHAIN_ROOT` and never copy private keys into a Circle project or
frontend bundle.

Fresh deploy refuses to overwrite existing deployment metadata. Use the update
tool for an existing Circle, or pass `--force-new` only when a distinct Circle
is intentional. A successful forced deployment archives the previous metadata
before updating the active deployment file. The updater handles public/plain
assets, skips resources with matching hashes, and rejects sealed deployment
metadata instead of publishing incompatible data.
