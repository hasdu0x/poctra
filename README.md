# poctra

poctra is a native Android wallet for the Octra ecosystem. It delivers a focused consumer mobile experience covering accounts, assets, privacy, identity, dApps, bridge, and trade — all on a single device without external custody.

## Features

- **Multi-wallet management** — create, import, rename, reorder, and switch active wallets.
- **OCT dashboard** — live balance with USD estimate, send, receive, bridge, and recent activity.
- **Privacy operations** — encrypt, decrypt, stealth send, and claim private transfers using on-device ZK proof generation.
- **EVM account** — derived EVM account from the same Octra key, with ETH, ERC20, and NFT support.
- **Octra Identity** — `.oct` name resolution for recipients, wallet labels, and activity display names.
- **dApp browser** — in-app WebView with poctra provider injection, approval sheets, session management, and phishing protection.
- **Bridge** — OCT ↔ wOCT with review, gas selection, progress tracking, history, and claim recovery.
- **Trade** — wOCT / ETH / USDT swap via Uniswap v4 with candlestick chart, quote review, gas options, and swap history.
- **Portfolio & Activity Center** — unified timeline across Octra, EVM, bridge, and dApp events with CSV export.
- **Address Book** — saved contacts with optional Octra Identity names.
- **Backup & Security** — biometric (Android Keystore) or PIN vault encryption, weak PIN detection, security center, and guarded seed/key reveal.
- **RPC & network management** — configurable Octra RPC endpoints, EVM networks, and Octra Identity providers.

## Security Model

poctra keeps wallet custody entirely on the device.

- Vault data is encrypted locally using AES-GCM.
- Biometric authentication uses an Android Keystore-bound AES key with biometric invalidation on enrollment changes.
- PIN encryption uses PBKDF2-HMAC-SHA256 with 310,000 iterations plus a Keystore-bound device binding key.
- Privacy keys are derived on demand from the wallet seed and never persisted separately.
- dApp connections and transaction requests require explicit per-action approval.
- Android app backup is disabled. Cleartext network traffic is disabled.

## Requirements

- Android 10 (API 29) or later
- 64-bit device (arm64-v8a) required for privacy operations

## Build

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Project Structure

```
app/src/main/java/com/poctra/wallet/
  core/        Wallet, mnemonic, key derivation, and calldata utilities
  data/        RPC, EVM, bridge, trade, identity, session, and asset repositories
  privacy/     Privacy crypto engine, stealth scanner/sender, and ZK bridge
  security/    Vault storage, biometric, dApp policy, and runtime security helpers
  ui/          Jetpack Compose screens, components, theme, glyphs, and chart views
app/src/main/cpp/
  pvac/        Native PVAC privacy library (C++17 + Rust)
```

## License

Copyright © 2026 poctra. All rights reserved.
