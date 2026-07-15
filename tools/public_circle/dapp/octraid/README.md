# octra.id Circle dApp

This project packages the `octra.id` frontend as a public Circle dApp.

Copied from:

```text
C:\Users\Administrator\Documents\Devs\octra.id\frontend
```

Excluded from the copy:

- `node_modules/`
- `dist/`
- Vite dev logs
- Git metadata

## Environment

The shared frontend provides explicit `circle-devnet` and `circle-mainnet`
modes. Both leave `VITE_ONS_RPC` blank in the production bundle so the Circle
host can provide the active RPC. Contract and explorer values remain specific
to the selected network.

For Circle-native RPC calls, use the runtime-provided RPC endpoint pattern, for
example:

```ts
await fetch(RPC_URL, {
  method: 'POST',
  headers: { 'content-type': 'application/json' },
  body: JSON.stringify({
    jsonrpc: '2.0',
    id: 1,
    method: 'octra_callProgramView',
    params: [{
      address: circleId,
      method: 'get_counter',
      params: [],
    }],
  }),
})
```

The RPC helper is runtime-aware and preserves compatibility with both Circle
program views and standard Octra contract views. See `../architecture.md` before
syncing transport or deployment files from the web frontend.

## Devnet Deployment

```text
oct://oct2EPYSkqjD6f1zLuWhyP6G2rDGB8rZp1nNDirVCqp3drD/index.html
```

- Circle ID: `oct2EPYSkqjD6f1zLuWhyP6G2rDGB8rZp1nNDirVCqp3drD`
- Resource mode: `public_resources`
- Passphrase: not required

Deployment metadata is stored outside the project at
`../../deployments/dapp.octraid.devnet.json`.

## Build and Deploy

```powershell
cd tools\public_circle\dapp\octraid
npm run build:circle:devnet

cd ..\..\..\..
node tools\public_circle\deploy-circle.cjs --project octraid --network devnet
```

The deploy script uploads `dist/` only. It does not upload source files,
package files, or local logs. If deployment metadata already exists, use the
update script. A deliberate second fresh deployment requires `--force-new`.

For a frontend-only update to the recorded Circle ID, run:

```powershell
node tools\public_circle\update-circle.cjs --project octraid --network devnet --build
```

Use `--dry-run` first to validate the build, RPC selection, deployer, and
deployment metadata without submitting transactions.

## Circle RPC Fallbacks

`src/lib/rpc.ts` is circle-aware:

- uses `window.RPC_URL` / `window.OCTRA_RPC_URL` when the circle runtime provides one;
- can call `OctraCircle.request('program.view', ...)` for a matching circle program;
- tries `octra_callProgramView` for circle-native view reads;
- falls back to `contract_call` for node/runtime compatibility.
