# dApp Protocol Boundaries

poctra keeps the three dApp surfaces separate at the provider boundary. A request is classified before any wallet or RPC action is performed.

## Octra RFC-O-1

The Octra provider handles RFC-O-1 account discovery, permissions, Octra reads, contract views, and Octra transaction review. It uses the active Octra RPC and never receives EVM private-key material.

Entry point: `PoctraController.handleOctraDAppBridgeRequest`.

## Circle runtime

Circle apps use the `OctraCircle` runtime injected into a Circle document. Circle read calls are proxied through the selected Circle target and active Octra RPC. Circle resource loading, downloads, and runtime RPC are separate from wallet-provider signing.

Entry points: `PoctraController.handleCircleBridgeRequest` and the Circle WebView runtime in `ui/Screens.kt`.

Circle program calls, public child deployment, and public asset uploads use dedicated reviewed methods. Public deployment is restricted to the OCTB public profile. Asset uploads require canonical metadata, a target Circle owned by the active wallet, and a 1 MiB browser-runtime budget. Circle code never receives a generic transaction or raw `op_type` signing primitive; atomic `circle_spawn` remains inside the WASM host runtime.

## EVM-compatible provider

EVM dApps use an EIP-1193-compatible provider and EIP-6963 announcement. The EVM provider owns account permissions, chain switching, network additions, asset watching, transaction review, personal signatures, and EIP-712 v4 typed-data signatures.

Entry point: `PoctraController.handleEvmDAppBridgeRequest`.

EIP-712 encoding is isolated in `dapp/evm/Eip712TypedData.kt`. It is bounded and validates:

- typed-data size, type count, field count, array length, and string/bytes size;
- type references, field shape, supported atomic types, and recursive types;
- active account ownership;
- `domain.chainId` against the active EVM network before signing;
- domain, primary type, schema, message, signing hash, and warnings in the approval sheet.

EIP-712 signing does not submit a transaction. It must never reuse the Octra RFC-O-1 transaction builder, bridge flow, or Trade swap state.

## RPC and security rules

- read-only EVM RPC methods are allowlisted;
- signing and broadcast methods are handled explicitly, never forwarded as arbitrary RPC;
- dApp requests are origin-scoped and require user approval for account access;
- unsupported opaque signing methods remain rejected;
- provider payloads and typed-data payloads are size-limited;
- active network changes are validated against the RPC-reported chain ID.

The approval UI is the security boundary: a dApp may request data, but no signature or transaction is produced before the user approves the reviewed request.
