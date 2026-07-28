# Poctra Security

Poctra is a self-custody wallet. APK obfuscation increases reverse-engineering cost, but it is not a security boundary. Private keys, API secrets, policy decisions, and transaction authorization must remain secure even when an attacker can inspect or modify the client.

## Current controls

- Release builds use R8 code shrinking and resource shrinking.
- Native code uses hidden visibility, section garbage collection, RELRO, and immediate symbol binding.
- Release WebView debugging and cleartext traffic are disabled.
- The in-app browser blocks file/content access, mixed content, SSL errors, unsafe browsing hits, and untrusted main-frame schemes.
- Biometric vault keys live in Android Keystore.
- PIN vaults use PBKDF2-HMAC-SHA256 and are device-bound with an Android Keystore HMAC key when the device supports it.
- Plain PIN values are not retained after unlock; only an in-memory derived session key is held and zeroed on lock/dispose.
- Backups and Android device transfer exclude secure SharedPreferences.
- Runtime signals surface signer mismatch, debugger/hooking indicators, root indicators, and emulator use without silently destroying user access.
- Official backend transaction broadcasts can carry a request-bound Google Play Integrity token. Custom RPC endpoints remain usable and are never silently redirected.

## Production release configuration

Pass these values through protected CI/Gradle properties, never source control:

```properties
POCTRA_PLAY_CLOUD_PROJECT_NUMBER=<linked Google Cloud project number>
POCTRA_ALLOWED_SIGNER_SHA256=<Play App Signing certificate SHA-256>
POCTRA_SECURITY_BACKEND_URL=https://rpc.xme.my.id
```

Run the strict configuration check before signing:

```powershell
.\gradlew.bat :app:verifyReleaseSecurity :app:bundleRelease
```

Use Play App Signing and keep the upload key outside the repository. The runtime allowlist must contain the **app signing** certificate, not only the upload certificate. Store each release's `mapping.txt` and native symbol archive in private release storage so crashes can be decoded without shipping symbols in the APK.

## Backend rollout

Configure `tools/backend` with its key pools and a Google service account stored outside the web root. Roll Play Integrity out in this order:

1. `PLAY_INTEGRITY_MODE=off` while configuring credentials.
2. `PLAY_INTEGRITY_MODE=observe` to measure valid production traffic without blocking users.
3. `PLAY_INTEGRITY_MODE=enforce` after Play-distributed builds consistently pass.

The backend verifies the exact JSON-RPC body hash, package name, token age, Play recognition, app signing certificate, and device integrity before forwarding `eth_sendRawTransaction`. Read-only RPC calls do not incur attestation latency.

## Release checklist

1. Run `powershell -File tools/security/scan-secrets.ps1`.
2. Run unit tests and Android lint.
3. Run `:app:verifyReleaseSecurity :app:bundleRelease` with CI-injected properties.
4. Inspect the signed artifact for unexpected endpoints, credentials, debuggable flags, and cleartext policy.
5. Archive R8 mapping/native symbols privately and publish the APK/AAB SHA-256 through a trusted channel.
6. Test fresh install, vault migration, biometric/PIN unlock, custom RPC, dApp approval, and transaction broadcast on a physical production-like device.

## Reporting

Do not include mnemonic phrases, private keys, raw vault exports, signed raw transactions, or service-account data in bug reports. Revoke and rotate any credential that may have been exposed.
