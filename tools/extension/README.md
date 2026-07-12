# circle viewer Chrome extension

This extension gives Chrome a circle viewer flow for Octra Explore:

- open `oct://<circle>/index.html` public or sealed circle resources
- input `foo.oct`, `app.foo.oct`, or an Octra address and resolve it with the active Octra Identity Provider
- route normal Octra addresses to the configured explorer
- route circle addresses to the extension circle viewer
- intercept clicked `oct://` links on regular web pages
- use the omnibox keyword `oct`, for example `oct root.oct`
- manage Octra RPC networks and Identity Provider contracts inside the popup
- switch dark/light theme inside the popup

## Install for development

1. Open `chrome://extensions`.
2. Enable `Developer mode`.
3. Click `Load unpacked`.
4. Select `C:\Users\Administrator\Documents\Devs\poctra\extension`.
5. Pin the extension, then open the popup.

## Default settings

Mainnet:

- RPC: `https://octra.network`
- Explorer: `https://octrascan.io`
- Identity Provider: `octH1GEiwunjEZyiBMjp1ETy1VUJJKEEnsMRZ8P2AvTC3et`

Devnet:

- RPC: `http://165.227.225.79:8080`
- Explorer: `https://devnet.octrascan.io`
- Identity Provider: `oct8oCgpxvdEytE7tkfjyF8am3xvK28J6rJApkv3omRQnKH`

## Chrome protocol limitation

Chrome extensions cannot fully register a custom browser-level protocol such as `oct://` for direct address-bar navigation without a native protocol handler installed at the OS level. This extension supports the practical browser flows Chrome allows:

- popup input
- omnibox keyword `oct`
- context menu
- clicked `oct://` links on pages where content scripts can run

Chrome also does not allow an extension to wrap, replace, or inject custom UI into the native address bar. The closest supported flow is the omnibox keyword, so users can type `oct root.oct` or `oct oct://...` directly in Chrome's address bar.

## Circle RPC inside apps

The viewer renders circle HTML inside a Chrome extension sandbox page. This avoids normal extension CSP blocking dApp inline scripts while keeping the page away from privileged `chrome.*` APIs.

The viewer injects a small read-only RPC bridge into rendered circle HTML. A circle app can call the active Octra RPC by using:

```js
await fetch("", {
  method: "POST",
  headers: { "content-type": "application/json" },
  body: JSON.stringify({
    jsonrpc: "2.0",
    id: 1,
    method: "octra_callProgramView",
    params: [{
      address: circleId,
      method: "get_counter",
      params: []
    }]
  })
});
```

Allowed read methods are kept intentionally small: `octra_callProgramView`, `contract_call`, `circle_info`, `circle_asset`, `node_status`, `epoch_current`, `octra_balance`, `octra_account`, `octra_transaction`, `contract_receipt`, and `octra_recommendedFee`.

## Notes

The extension viewer is sandboxed so circle content cannot access extension storage or privileged APIs. Public HTML circles with common `script`, `link rel=stylesheet`, and `img` relative assets are inlined before rendering. More advanced browser APIs may need a dedicated native app or a hosted web gateway later.
