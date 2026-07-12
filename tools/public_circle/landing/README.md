# poctra public circle landing

This folder contains a static landing page package for poctra.

## Devnet Deployment

- Circle URI: `oct://oct3A5bhpktxb9y2gepQeGxSbkJspq3Pv178Zyi9tkM1oJx/index.html`
- Circle ID: `oct3A5bhpktxb9y2gepQeGxSbkJspq3Pv178Zyi9tkM1oJx`
- Network: Octra devnet
- Deploy tx: `de18461986be8c46251ccd410b0fd0f6e76ba336c20d021ead38f5811e01dfa5`
- Resource mode: `public_resources`
- Passphrase: not required

The landing is packaged as a public circle with plain assets. It should open from
`oct://.../index.html` without a sealed read key or passphrase.

## Files

- `index.html` - Circle-ready landing page entry.
- `styles.css` - poctra-styled responsive UI.
- `app.js` - Small screenshot switcher.
- `assets/poctra.svg` - Web SVG logo.
- `assets/screenshots/*.png` - Product screenshots from `temp/screenshot`.
- `assets/fonts/*.ttf` - Local Fira Code fonts.

## Circle Paths

The package is designed to be uploaded with `/index.html` as the default resource.
All asset links are relative, so the page can be served from a circle address or from a
regular static host during preview.

## Deploy

```powershell
node public_circle\landing\deploy-devnet.js
```

The script reads deployer credentials from `C:\Users\Administrator\Documents\Devs\octra.id\wallet_deployer.txt`
through the existing `octra.id` deployment helpers. It does not print the deployer private key.

### Sealed Circle Override

The script defaults to public/no-passphrase deployment. To deploy a sealed demo again,
override the mode explicitly:

```powershell
$env:CIRCLE_PRIVACY_CLASS='sealed'
$env:CIRCLE_BROWSER_MODE='native_sealed'
$env:CIRCLE_RESOURCE_MODE='sealed_read'
$env:CIRCLE_ASSET_MODE='sealed'
$env:CIRCLE_PASSPHRASE='octra-circle-demo'
node public_circle\landing\deploy-devnet.js
```
