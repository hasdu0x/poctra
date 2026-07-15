# Frontend environments

The frontend is the shared source for both the standard web3 deployment and
the Circle dApp build. Select the runtime and Octra network through an explicit
Vite mode.

| Mode | Runtime | Network | RPC behavior |
| --- | --- | --- | --- |
| `web3-devnet` | HTTP(S) web dApp | Devnet | Direct configured RPC in production; Vite `/rpc` proxy in development |
| `web3-mainnet` | HTTP(S) web dApp | Mainnet | Host `/rpc` reverse proxy |
| `circle-devnet` | Circle resource | Devnet | Runtime-injected RPC, then Circle-aware fallback |
| `circle-mainnet` | Circle resource | Mainnet | Runtime-injected RPC, then Circle-aware fallback |

## Development

```powershell
npm run dev:web3:devnet
npm run dev:web3:mainnet
npm run dev:circle:devnet
npm run dev:circle:mainnet
```

Circle development still uses Vite's `/rpc` proxy when opened as a normal
localhost page. The generated Circle build switches to relative assets and
uses the injected Circle runtime when opened through `oct://`.

## Builds

```powershell
npm run build:web3:devnet
npm run build:web3:mainnet
npm run build:circle:devnet
npm run build:circle:mainnet
```

`npm run build` is an alias for `build:web3:mainnet`. Do not rely on a generic
build command when publishing a Circle; always select the Circle network
explicitly.

Environment files contain public network and contract configuration only.
Wallet keys, deployment credentials, and private RPC credentials must never be
added to Vite variables because every `VITE_*` value is bundled into client
JavaScript.
