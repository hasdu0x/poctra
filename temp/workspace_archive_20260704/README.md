# Poctra Android Wallet

Poctra is a lean native Android implementation of the requested three-screen crypto wallet flow:

- Welcome screen with create/import wallet actions.
- Mandatory security setup with platform biometric support on Android 9+ and a 6-digit PIN fallback.
- Dashboard with multi-wallet management, balance, Send/Receive/Bridge actions, Octra OCS01 assets, settings, RPC configuration, security update, encrypted backup export, and connected dApp revocation.

## Project Structure

```text
app/src/main/java/com/poctra/wallet
  MainActivity.kt                 Compose entry point
  PoctraController.kt             App state, navigation, async orchestration
  core/
    Base58.kt                     Octra Base58 address encoding
    Bip39Mnemonic.kt              Local BIP39 generation, validation, seed derivation
    WalletCrypto.kt               Wallet generate/import and Octra address derivation
  data/
    Models.kt                     Wallet, RPC, token, dApp models
    RpcRepository.kt              RPC endpoint storage, status, balance fetch
    DAppSessionRepository.kt      Connected dApp list/revoke storage
    AssetRepository.kt            Saved Octra OCS01 token contracts
    BackupExporter.kt             Encrypted backup download writer
  security/
    DeviceSecurity.kt             Platform BiometricPrompt wrapper
    SecureWalletStore.kt          Android Keystore and PIN AES-GCM storage
  ui/
    Components.kt                 Token-driven buttons, inputs, cards, rows
    Glyphs.kt                     Lightweight canvas icons
    Screens.kt                    Welcome, security, dashboard, settings
    Theme.kt                      FastAuth/Poctra tokens
```

The BIP39 English wordlist is bundled at `app/src/main/assets/bip39_english.json`.

## Base Code Reference

The implementation extracts only the essential ideas from:

```text
C:\Users\Administrator\Documents\Devs\Octra\OctWa\main\src
```

Mapped logic:

- `utils/crypto.ts` -> `Bip39Mnemonic.kt`, `WalletCrypto.kt`
- `utils/wallet.ts` -> wallet generation, mnemonic import, Base64 private key import
- `utils/password.ts` / `utils/walletManager.ts` -> AES-GCM encrypted storage and session-free secure unlock
- `utils/rpc*.ts` -> default RPC URL, URL validation, node status checks
- `components/ConnectedDAppsManager.tsx` -> `DAppSessionRepository.kt` and Settings revoke flow
- `components/OctraAssets.tsx` / `services/octraTokens.ts` -> OCS01 token list, lookup, and local saved token registry

## Build

Open the folder in Android Studio, or run Gradle with Android SDK API 34 installed:

```powershell
gradle assembleDebug
```

This workspace was validated with the locally cached Gradle 8.6 distribution:

```powershell
C:\Users\Administrator\.gradle\wrapper\dists\gradle-8.6-bin\afr5mpiioh2wthjmwnkmdsd5w\gradle-8.6\bin\gradle.bat --offline --console=plain assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Security Notes

- Private key and mnemonic are never written to preferences before security setup.
- Biometric mode encrypts wallet JSON with an Android Keystore AES-GCM key requiring biometric authentication.
- PIN mode encrypts wallet JSON with AES-GCM using PBKDF2-HMAC-SHA256 with 310,000 iterations, 32-byte salt, and 12-byte IV.
- PIN unlock is rate-limited after 5 failed attempts for 5 minutes.
- Address and public key metadata may be stored outside the encrypted payload; mnemonic/private key remain inside encrypted storage only.
- Settings > Download Backup writes an encrypted JSON backup to Downloads on Android 10+.

## RPC And dApps

Default RPC is:

```text
https://octra.network
```

Settings lets users save a custom HTTP/HTTPS endpoint, checks `node_stats`, and fetches balance with `octra_balance`. Connected dApps are stored as a canonical origin map under `connectedDApps` and can be revoked individually.
