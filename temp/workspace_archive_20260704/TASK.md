# TASK: Build Poctra — Android Crypto Wallet (Simple Mobile Version)

## Overview
Build a simple Android crypto wallet app named **Poctra** from scratch.
Reference the existing core logic from the base web & extension wallet at:
`C:\Users\Administrator\Documents\Devs\Octra\OctWa\main\src`

Extract only the essential wallet logic needed for these three screens.
Do NOT port the full codebase — keep it minimal and mobile-first.

---

## Design System Reference
Apply the FastAuth design tokens as the foundation:
- Font stack: `-apple-system, BlinkMacSystemFont, SF Pro Text, Segoe UI, system-ui, sans-serif`
- Base font size: 16px, weight 400
- Colors: surface.base=#000000 | text.secondary=#757571 | text.tertiary=#ffffff | surface.raised=#00f29b | surface.strong=#f5f4ed
- Radius: xs=8px | sm=12px | md=16px | lg=100px (pill)
- Motion: instant=120ms | fast=160ms
- Spacing: 2/4/6/8/10/12/16/20px scale
- All component states required: default, hover, focus-visible, active, disabled, loading, error

---

## Screen 1: Welcome Screen

### Layout
- Centered vertical layout, full screen
- Top area: POCTRA logo + tagline/slogan
- Bottom area: two CTA buttons

### Components
1. **Logo** — App name "POCTRA" in bold + short slogan below (e.g. "Your keys. Your crypto.")
2. **Create Wallet** button — Primary style, full-width, pill shape (radius.lg), bg=color.surface.raised (#00f29b), text=color.surface.base (#000000)
3. **Import Wallet** button — Secondary/ghost style, full-width, border only, text=color.text.tertiary (#ffffff)

### Import Wallet Flow (modal or new screen)
Triggered from "Import Wallet" button. User chooses import method:
- **Option A: Mnemonic phrase** — textarea input, 12/24 word seed phrase
- **Option B: Private Key (Base64)** — single-line text input, accepts B64-encoded private key
- Validate input before proceeding; show inline error on invalid format
- On success → proceed to Security Setup screen

### Logic to extract from base code
- Wallet generation (keypair creation)
- Mnemonic → keypair derivation
- Private key (B64) → keypair import
- Keystore/storage abstraction (adapt for Android Keystore)

---

## Screen 2: Security Setup

### Trigger
Shown immediately after wallet creation or import, before entering dashboard.

### Logic
- Detect device capability: if biometric (fingerprint/face) available → offer fingerprint option
- Fallback or alternative: PIN setup (6-digit numeric PIN)
- User must complete at least one security method before proceeding
- Store encrypted wallet key secured by chosen method (Android Keystore + BiometricPrompt or PIN-derived key)

### Components
1. **Method selector** — two cards: "Use Fingerprint" (if available) | "Use PIN"
2. **Fingerprint flow** — trigger Android BiometricPrompt, on success → save & continue
3. **PIN flow** — 6-digit PIN input screen → confirm PIN screen → on match → save & continue
4. **Error states** — biometric failure fallback to PIN, PIN mismatch retry

### Logic to extract from base code
- Key encryption/decryption abstraction
- Secure storage interface (adapt for Android Keystore)

---

## Screen 3: Dashboard Wallet

### Reference
Visual layout based on `sample_design/dashboard.png` with these changes:
- Replace **Swap** button → **Bridge** button
- Add **Settings** entry point (gear icon, top-right or bottom nav)

### Layout
- Header: wallet address (truncated) + network badge + total balance (large)
- Action buttons row: **Send** | **Receive** | **Bridge** (was Swap)
- Asset/token list below
- Bottom navigation or top-right: Settings icon

### Settings Screen (sub-screen of Dashboard)
Accessible from dashboard. Contains:

#### 1. RPC Settings
- Show current RPC endpoint URL
- Allow user to input custom RPC URL
- Validate URL format on save
- Show connection status (connected / failed)

#### 2. Update Security
- Allow switching between Fingerprint ↔ PIN
- Re-authenticate with current method before changing
- Same flow as Screen 2 Security Setup

#### 3. Connected dApps
- List of connected dApps (name, URL, date connected)
- Each entry has a **Revoke** button
- Empty state: "No connected dApps"
- Revoke action requires confirmation dialog

### Logic to extract from base code
- RPC provider configuration
- dApp connection registry (WalletConnect or custom)
- Session/permission management for dApp revoke

---

## Technical Constraints
- Platform: Android (minSdk 26+)
- Language: Kotlin (preferred) or React Native / Flutter if base code is JS-based
- Storage: Android Keystore for sensitive key material; SharedPreferences/Room for app state
- No unnecessary dependencies — keep it lean
- Offline-capable for core wallet functions (key management, address display)
- Network required only for: balance fetch, send tx, bridge, RPC config

---

## Deliverables Expected
1. Project folder structure
2. Screen implementations (Welcome, Security Setup, Dashboard + Settings)
3. Extracted core logic modules (wallet gen, import, encrypt/decrypt, RPC, dApp sessions)
4. README with setup instructions and how to connect to the existing base code reference

---

## Quality Gates
- All screens must handle loading, error, and empty states
- Security setup must be non-bypassable on first launch
- No private key or mnemonic ever stored in plaintext
- All buttons must have disabled state while async operations are in-flight
- WCAG AA contrast compliance on all text elements