# poctra Chrome Extension

Password-secured Octra and EVM wallet for Chromium browsers. The extension follows the Poctra consumer UI and implements the RFC-O-1 provider interface for dApp connectivity.

## Development

Requirements: Node.js 20 or newer and a Chromium browser.

```powershell
npm install
npm run test
npm run build
```

Load `tools/extension/dist` from `chrome://extensions` using **Load unpacked**. Always load the built `dist` directory, not the source directory.

## RFC-O-1 Provider

Poctra injects an RFC-O-1 provider at `window.octra`, participates in the `window.octraProviders` multi-wallet registry, and implements the `octra:requestProvider` / `octra:announceProvider` discovery handshake.

Supported Octra methods:

- Accounts, permissions, disconnect, network information, and network switching
- Message signing, transaction signing/submission, OCT transfers
- Contract views, contract transactions, and contract receipts
- Read-only Octra RPC methods from the RFC allowlist

Supported EVM methods:

- Derived address, chain/network information, balances, calls, gas, and token reads
- Chain switching, native transfers, message/EIP-712 signing, ERC-20 transfers and approvals

Privacy provider methods intentionally return RFC error `4200` with reason `privacy_not_supported`; privacy operations are excluded from this extension build.

Provider requests use a long-lived MV3 port while approval is open. Errors preserve RFC codes and `data.reason`, and all signing or write operations require an origin-scoped permission plus explicit review.

## Security Model

- Wallet material is encrypted with AES-256-GCM.
- Password derivation uses PBKDF2-HMAC-SHA256 with 600,000 iterations.
- Decrypted vault data is stored only in trusted `chrome.storage.session` and is removed on lock or timeout.
- dApps receive no account until the user approves an origin-scoped connection.
- Every signature, network switch, token approval, and transaction has a dedicated review window.
- The content bridge verifies same-origin page messages and derives the requesting origin from Chrome's sender metadata.
- The extension contains no Infura or Etherscan API keys.

The `<all_urls>` host scope is required to inject the RFC-O-1 provider into HTTPS production dApps and HTTP development dApps. It does not grant a dApp wallet access; account and signing capabilities remain gated by explicit Poctra permissions.

## Verification

```powershell
npm run typecheck
npm run test
npm run build
npm run qa:visual
npm audit
```
