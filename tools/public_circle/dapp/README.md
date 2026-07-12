# octra.id circle dApp copy

This is a source-only copy of the `octra.id` frontend for circle dApp packaging.

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

The copied env files are set to devnet:

```text
VITE_ONS_NETWORK=devnet
VITE_ONS_CONTRACT=oct8oCgpxvdEytE7tkfjyF8am3xvK28J6rJApkv3omRQnKH
VITE_ONS_RPC=
VITE_ONS_EXPLORER=https://devnet.octrascan.io
```

`VITE_ONS_RPC` is intentionally blank. For circle-native RPC calls, use the runtime-provided RPC endpoint pattern, for example:

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

The current copy still preserves the original frontend RPC helper structure so we can adapt it carefully when packaging/deploying this dApp circle.

## Devnet Deployment

```text
oct://octDKJ5vct7qDnbbAQdeq1SkCpt15V5uEb8XuEpyLWq851h/index.html
```

- Circle ID: `octDKJ5vct7qDnbbAQdeq1SkCpt15V5uEb8XuEpyLWq851h`
- Deploy tx: `fab79f797f2bf699f6d88a792e52fe0879a75f93efa2147ed576ebb8c13c2a23`
- Resource mode: `public_resources`
- Passphrase: not required

Deployment metadata is stored in `deployment.devnet.json`.

## Build and Deploy

```powershell
npm run build
node public_circle\dapp\deploy-devnet.cjs
```

The deploy script uploads `dist/` only. It does not upload source files, package files, or local logs.

## Circle RPC Fallbacks

`src/lib/rpc.ts` is circle-aware:

- uses `window.RPC_URL` / `window.OCTRA_RPC_URL` when the circle runtime provides one;
- can call `OctraCircle.request('program.view', ...)` for a matching circle program;
- tries `octra_callProgramView` for circle-native view reads;
- falls back to `contract_call` for current devnet compatibility.
