# poctra

poctra is a native Android wallet for the Octra ecosystem. It focuses on a compact
consumer mobile experience for Octra accounts, assets, identity, dApps, bridge, and trade.

## What poctra Covers

- Multi-wallet management with create, import, rename, reorder, and active wallet switching.
- Native OCT dashboard with balance, estimated USD value, send, receive, bridge, and recent activity.
- Octra asset management for manually added Octra-chain assets.
- Derived EVM Account from the same Octra key, including ERC20 and NFT handling.
- Octra Identity lookup for `.oct` recipients, wallet display names, and activity labels.
- Address Book and recent recipients for faster send flows.
- In-app dApp browser with poctra provider injection, approval sheets, and connected dApp management.
- Octra circle routing through `oct://`, Octra Identity names, and Octra address fallback behavior.
- Bridge flow for OCT to wOCT and wOCT to OCT with review, gas options, progress, history, recover, and claim.
- Trade flow for wOCT, ETH, and USDT with chart view, quote review, gas options, and swap progress.
- Portfolio and activity center views for a wider account overview.
- Local backup key reveal with security confirmation and hidden sensitive values by default.
- RPC, EVM network, and Octra Identity Provider management.

## Security Model

poctra keeps custody local to the device.

- Wallet vault data is encrypted locally.
- Users can secure the vault with biometric authentication or a PIN.
- PIN encryption uses AES-GCM with PBKDF2-HMAC-SHA256 key derivation and a BouncyCastle fallback for older Android providers.
- Mnemonic seed derivation uses PBKDF2-HMAC-SHA512 with a BouncyCastle fallback for compatibility on older Android/emulator images.
- Sensitive key and mnemonic reveal flows require security confirmation.
- dApp connections, signatures, and transaction requests are shown through explicit approval dialogs.
- Android app backup is disabled for wallet data.

## Android Compatibility

The app is configured for:

- `minSdk 23`
- `targetSdk 34`

The project includes compatibility guards around biometric APIs and older Android provider behavior.
On Android versions below the biometric prompt flow, users should use PIN-based security.

## Public Circle Landing

The static marketing/circle package lives in:

```text
public_circle/landing/
```

It contains a dependency-free landing page with poctra styling, local Fira Code fonts,
the poctra logo, and product screenshots copied from:

```text
temp/screenshot/
```

The package is designed to be served with `/index.html` as the default circle resource.

Devnet circle deployment:

```text
oct://oct3A5bhpktxb9y2gepQeGxSbkJspq3Pv178Zyi9tkM1oJx/index.html
```

The circle is deployed as `public_resources` with plain assets, so no sealed read key
or passphrase is required.
Deployment metadata is stored at:

```text
public_circle/landing/deployment.devnet.json
```

Devnet Octra Identity dApp circle:

```text
oct://octDKJ5vct7qDnbbAQdeq1SkCpt15V5uEb8XuEpyLWq851h/index.html
```

Its metadata is stored at:

```text
public_circle/dapp/deployment.devnet.json
```

## Build

Debug APK:

```powershell
C:\Users\Administrator\.gradle\wrapper\dists\gradle-8.6-bin\afr5mpiioh2wthjmwnkmdsd5w\gradle-8.6\bin\gradle.bat --offline --console=plain :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```text
app/src/main/java/com/poctra/wallet/
  core/        wallet, mnemonic, key, and calldata logic
  data/        RPC, EVM, bridge, trade, identity, session, and persistence repositories
  security/    vault storage, dApp policy, and device security helpers
  ui/          Jetpack Compose screens, components, theme, icons, and wallet visuals

public_circle/
  landing/
    index.html   public circle landing page
    styles.css   poctra web styling
    app.js       screenshot switcher
    assets/      logo, fonts, and screenshots
  dapp/
    src/         copied octra.id frontend source
    public/      copied public assets
```

## Current Status

poctra is an alpha mobile wallet build. The current about label is `alpha v1.0.0`.
Mainnet and devnet flows are present, with network selection managed from in-app settings.
