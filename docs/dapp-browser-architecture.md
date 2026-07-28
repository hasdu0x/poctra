# Poctra dApp Browser Architecture

Poctra uses one Android WebView surface with four deliberately separate responsibilities.

| Layer | Owner | Responsibility |
| --- | --- | --- |
| Common web | Android WebView | HTTPS navigation, cookies, standard browser APIs, media, and file selection. |
| Circle resources | `circleWebResourceResponse` and Circle repositories | `oct://` routing, public/sealed asset reads, MIME inference, range requests, Circle runtime injection, and downloads. |
| Octra RFC | `PoctraProviderBridge.request` | RFC-O-1 discovery, permissions, approvals, and Octra wallet requests. |
| EVM RFC | `PoctraProviderBridge.evmRequest` | EIP-1193/EIP-6963 requests, EVM approvals, signing, and chain operations. |

`routeBrowserResource` owns network-resource dispatch. It gives RFC-O-1's in-page provider priority, routes Circle resources and restricted Circle subresources to the Circle handler, and returns ordinary web requests to WebView unchanged.

`PoctraProviderBridge` is the only JavaScript-interface boundary. Its methods keep separate validation and response channels for Octra, EVM, Circle management, downloads, and clipboard writes. Every privileged request remains bound to the current origin and per-tab bridge token.

## Circle content types

Circle assets use their declared content type when it is specific. Missing or generic `application/octet-stream` metadata is inferred from the canonical path:

| Extension | Content type |
| --- | --- |
| `.html`, `.htm` | `text/html; charset=utf-8` |
| `.css` | `text/css; charset=utf-8` |
| `.js`, `.mjs` | `application/javascript; charset=utf-8` |
| `.json` | `application/json; charset=utf-8` |
| `.xml` | `application/xml; charset=utf-8` |
| `.svg` | `image/svg+xml` |
| `.png` | `image/png` |
| `.jpg`, `.jpeg` | `image/jpeg` |
| `.gif` | `image/gif` |
| `.webp` | `image/webp` |

Unknown extensions remain `application/octet-stream`. HTML alone receives the Circle runtime injection; other previewable types are returned with their resolved MIME and charset.

## Change rules

- Do not add protocol-specific behavior directly to the Compose screen.
- Add network routing rules to `BrowserResourceRouter`.
- Add JavaScript wallet channels to `PoctraProviderBridge` and preserve origin/token validation.
- Add Circle MIME mappings to `CircleContentType` and its matching Circle Viewer module.
- Preserve common HTTPS handling by returning `null` from WebView interception.
- Cover new behavior with pure unit tests before changing WebView lifecycle code.
