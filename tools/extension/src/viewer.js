import {
  assetBlobUrl,
  applyTheme,
  bytesToDataUrl,
  bytesToText,
  circleDisplayUri,
  extractHtmlTitle,
  fetchCircleAsset,
  fetchSealedCircleAsset,
  forwardCircleRpcPayload,
  loadSettings,
  normalizeCirclePath,
  parseCircleInputSpec,
  resolveInputTarget
} from "./shared.js";

const params = new URLSearchParams(location.search);
const circleId = params.get("circleId") || "";
const path = normalizeCirclePath(params.get("path") || "/index.html");
const display = params.get("display") || `oct://${circleId}${path === "/index.html" ? "" : path}`;
const resourceMode = params.get("mode") || "";
const rawParam = params.get("raw");
const requestedDisplay = rawParam || display;

const displayUrl = document.getElementById("displayUrl");
const loadingState = document.getElementById("loadingState");
const passphraseState = document.getElementById("passphraseState");
const errorState = document.getElementById("errorState");
const errorMessage = document.getElementById("errorMessage");
const frame = document.getElementById("circleFrame");
const passphraseForm = document.getElementById("passphraseForm");
const passphraseInput = document.getElementById("passphraseInput");
const backButton = document.getElementById("backButton");
const refreshButton = document.getElementById("refreshButton");
const retryButton = document.getElementById("retryButton");
const copyButton = document.getElementById("copyButton");

let settings;
let activeObjectUrls = [];
const VIEWER_TITLE = "circle viewer";

bindControls();

if (rawParam) {
  resolveRawParam(rawParam);
} else {
  init();
}

async function init() {
  displayUrl.textContent = display.replace(/^oct:\/\//i, "");
  settings = await loadSettings();
  applyTheme(settings);
  document.title = VIEWER_TITLE;
  await openCircle();
}

function bindControls() {
  passphraseForm.addEventListener("submit", openWithPassphrase);
  backButton.addEventListener("click", () => history.length > 1 ? history.back() : window.close());
  refreshButton.addEventListener("click", () => openCircle());
  retryButton.addEventListener("click", () => openCircle());
  copyButton.addEventListener("click", () => navigator.clipboard.writeText(requestedDisplay));
  window.addEventListener("message", handleCircleMessage);
}

async function resolveRawParam(rawInput) {
  try {
    const spec = parseCircleInputSpec(rawInput);
    const displayUri = spec ? circleDisplayUri(spec, rawInput) : rawInput;
    const target = await resolveInputTarget(rawInput, await loadSettings());
    location.replace(target.url || displayUri);
  } catch (error) {
    displayUrl.textContent = rawInput.replace(/^oct:\/\//i, "");
    showError(error.message || "Unable to open oct:// URL.");
  }
}

async function openCircle(passphrase = "") {
  showState("loading");
  revokeObjectUrls();
  try {
    if (resourceMode === "sealed_read" && !passphrase) {
      const saved = await sessionGet(`circle-passphrase:${circleId}`);
      if (!saved) {
        showState("passphrase");
        return;
      }
      passphrase = saved;
    }
    const asset = resourceMode === "sealed_read"
      ? await fetchSealedCircleAsset(circleId, path, passphrase, settings)
      : await fetchCircleAsset(circleId, path, settings);
    if (passphrase) await sessionSet(`circle-passphrase:${circleId}`, passphrase);
    await renderAsset(asset);
  } catch (error) {
    if (resourceMode === "sealed_read") await sessionRemove(`circle-passphrase:${circleId}`);
    showError(error.message || "Unable to open circle.");
  }
}

async function openWithPassphrase(event) {
  event.preventDefault();
  const passphrase = passphraseInput.value.trim();
  if (!passphrase) {
    showError("Enter the sealed read passphrase.");
    return;
  }
  await openCircle(passphrase);
}

async function renderAsset(asset) {
  const contentType = String(asset.contentType || "").toLowerCase();
  if (contentType.includes("html")) {
    const html = await prepareHtml(bytesToText(asset.body), asset.canonicalPath);
    document.title = extractHtmlTitle(asset) || VIEWER_TITLE;
    await renderHtmlInSandbox(html);
    showState("frame");
    return;
  }
  const url = assetBlobUrl(asset);
  activeObjectUrls.push(url);
  frame.src = url;
  showState("frame");
}

async function prepareHtml(html, currentPath) {
  const parser = new DOMParser();
  const doc = parser.parseFromString(html, "text/html");
  doc.querySelectorAll("meta[http-equiv]").forEach((meta) => {
    if ((meta.getAttribute("http-equiv") || "").toLowerCase() === "content-security-policy") {
      meta.remove();
    }
  });
  const base = doc.createElement("base");
  base.target = "_blank";
  doc.head.prepend(base);

  const bridge = doc.createElement("script");
  bridge.textContent = circleBridgeScript(await circleStorageSnapshot());
  doc.head.prepend(bridge);

  await inlineAssets(doc, currentPath);
  rewriteAnchors(doc, currentPath);
  return `<!doctype html>\n${doc.documentElement.outerHTML}`;
}

async function renderHtmlInSandbox(html) {
  await new Promise((resolve) => {
    const onLoad = () => {
      frame.removeEventListener("load", onLoad);
      resolve();
    };
    frame.addEventListener("load", onLoad);
    frame.src = chrome.runtime.getURL("sandbox.html");
  });
  frame.contentWindow?.postMessage({ source: "poctra-viewer-render", html }, "*");
}

async function inlineAssets(doc, currentPath) {
  const jobs = [];
  doc.querySelectorAll("img[src], script[src], link[href]").forEach((node) => {
    const attr = node.tagName.toLowerCase() === "link" ? "href" : "src";
    const raw = node.getAttribute(attr);
    const resolvedPath = resolveCircleRelativePath(raw, currentPath);
    if (!resolvedPath) return;
    jobs.push((async () => {
      try {
        const asset = await fetchCircleAsset(circleId, resolvedPath, settings);
        const tag = node.tagName.toLowerCase();
        if (tag === "img") {
          node.setAttribute(attr, bytesToDataUrl(asset));
          return;
        }
        if (tag === "link") {
          const rel = (node.getAttribute("rel") || "").toLowerCase();
          if (rel.includes("icon")) {
            node.setAttribute(attr, bytesToDataUrl(asset));
            return;
          }
          if (!rel.includes("stylesheet")) return;
          const style = doc.createElement("style");
          style.textContent = await inlineCssAssetUrls(bytesToText(asset.body), resolvedPath);
          node.replaceWith(style);
          return;
        }
        if (tag === "script") {
          const script = doc.createElement("script");
          copyScriptAttributes(node, script);
          script.textContent = bytesToText(asset.body);
          node.replaceWith(script);
        }
      } catch {
        node.setAttribute("data-poctra-missing-asset", raw);
      }
    })());
  });
  await Promise.allSettled(jobs);
}

async function inlineCssAssetUrls(css, cssPath) {
  const matches = [...css.matchAll(/url\((['"]?)(.*?)\1\)/gi)]
    .map((match) => ({ raw: match[0], value: match[2] }))
    .filter((item) => resolveCircleRelativePath(item.value, cssPath));
  if (!matches.length) return css;
  let next = css;
  for (const item of matches) {
    const resolvedPath = resolveCircleRelativePath(item.value, cssPath);
    if (!resolvedPath) continue;
    try {
      const asset = await fetchCircleAsset(circleId, resolvedPath, settings);
      next = next.replaceAll(item.raw, `url("${bytesToDataUrl(asset)}")`);
    } catch {
      // Keep the original URL if the circle asset cannot be fetched.
    }
  }
  return next;
}

function copyScriptAttributes(source, target) {
  for (const attr of source.attributes) {
    const name = attr.name.toLowerCase();
    if (name !== "src" && name !== "integrity" && name !== "crossorigin") {
      target.setAttribute(attr.name, attr.value);
    }
  }
}

function rewriteAnchors(doc, currentPath) {
  doc.querySelectorAll("a[href]").forEach((link) => {
    const raw = link.getAttribute("href") || "";
    if (!raw || raw.startsWith("#") || /^(mailto|tel|javascript):/i.test(raw)) return;
    if (/^https?:\/\//i.test(raw)) {
      link.target = "_blank";
      link.rel = "noopener noreferrer";
      return;
    }
    if (/^oct:\/\//i.test(raw)) {
      link.href = chrome.runtime.getURL(`viewer.html?raw=${encodeURIComponent(raw)}`);
      link.target = "_blank";
      return;
    }
    const nextPath = resolveCircleRelativePath(raw, currentPath);
    if (!nextPath) return;
    link.href = chrome.runtime.getURL(`viewer.html?circleId=${encodeURIComponent(circleId)}&path=${encodeURIComponent(nextPath)}&display=${encodeURIComponent(displayForPath(nextPath))}&mode=${encodeURIComponent(resourceMode)}`);
    link.target = "_blank";
  });
}

function resolveCircleRelativePath(rawUrl, currentPath) {
  if (!rawUrl || /^[a-z][a-z0-9+.-]*:/i.test(rawUrl) || rawUrl.startsWith("//") || rawUrl.startsWith("#")) return null;
  const clean = rawUrl.split("?")[0].split("#")[0];
  if (!clean) return null;
  if (clean.startsWith("/")) return normalizeCirclePath(clean);
  const parts = normalizeCirclePath(currentPath).split("/");
  parts.pop();
  for (const segment of clean.split("/")) {
    if (!segment || segment === ".") continue;
    if (segment === "..") {
      if (parts.length > 1) parts.pop();
    } else {
      parts.push(segment);
    }
  }
  return normalizeCirclePath(parts.join("/"));
}

function displayForPath(nextPath) {
  const host = display.replace(/^oct:\/\//i, "").split("?")[0].split("#")[0].split("/")[0];
  return nextPath === "/index.html" ? `oct://${host}` : `oct://${host}${nextPath}`;
}

async function handleCircleMessage(event) {
  const message = event.data;
  if (message?.source === "poctra-circle-open") {
    await openCircleLink(message.href);
    return;
  }
  if (message?.source === "poctra-circle-storage") {
    await persistCircleStorage(message);
    return;
  }
  if (message?.source === "poctra-circle-asset" && message.id) {
    await handleCircleAssetMessage(message);
    return;
  }
  if (message?.source !== "poctra-circle-rpc" || !message.id) return;
  try {
    const payload = JSON.parse(message.body || "null");
    const response = await forwardCircleRpcPayload(payload, settings);
    frame.contentWindow?.postMessage({ source: "poctra-circle-rpc-result", id: message.id, ok: true, body: JSON.stringify(response) }, "*");
  } catch (error) {
    frame.contentWindow?.postMessage({ source: "poctra-circle-rpc-result", id: message.id, ok: false, message: error.message || "Circle RPC failed." }, "*");
  }
}

async function handleCircleAssetMessage(message) {
  try {
    const path = normalizeCirclePath(message.path || "/index.html");
    const asset = await fetchCircleAsset(circleId, path, settings);
    frame.contentWindow?.postMessage({
      source: "poctra-circle-asset-result",
      id: message.id,
      ok: true,
      contentType: asset.contentType || "application/octet-stream",
      body: bytesToBase64ForBridge(asset.body)
    }, "*");
  } catch (error) {
    frame.contentWindow?.postMessage({
      source: "poctra-circle-asset-result",
      id: message.id,
      ok: false,
      message: error.message || "Circle asset failed."
    }, "*");
  }
}

function bytesToBase64ForBridge(bytes) {
  let out = "";
  const chunkSize = 0x8000;
  for (let index = 0; index < bytes.length; index += chunkSize) {
    out += String.fromCharCode(...bytes.subarray(index, index + chunkSize));
  }
  return btoa(out);
}

async function circleStorageSnapshot() {
  const local = await storageAreaGet(chrome.storage.local, circleLocalStorageKey());
  const session = await storageAreaGet(chrome.storage.session, circleSessionStorageKey());
  return {
    local: local || {},
    session: session || {}
  };
}

async function persistCircleStorage(message) {
  const areaName = message.area === "session" ? "session" : "local";
  const key = areaName === "session" ? circleSessionStorageKey() : circleLocalStorageKey();
  const area = areaName === "session" ? chrome.storage.session : chrome.storage.local;
  if (!area) return;
  const next = sanitizeStorageSnapshot(message.snapshot);
  await area.set({ [key]: next });
}

function circleLocalStorageKey() {
  return `circle-local-storage:${circleId}`;
}

function circleSessionStorageKey() {
  return `circle-session-storage:${circleId}`;
}

async function storageAreaGet(area, key) {
  if (!area) return null;
  const result = await area.get([key]);
  return sanitizeStorageSnapshot(result[key]);
}

function sanitizeStorageSnapshot(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return {};
  const out = {};
  for (const [key, item] of Object.entries(value)) {
    out[String(key)] = String(item);
  }
  return out;
}

async function openCircleLink(href) {
  try {
    const target = await resolveInputTarget(href, settings);
    window.open(target.url, "_blank", "noopener");
  } catch {
    window.open(href, "_blank", "noopener");
  }
}

function circleBridgeScript(storageSnapshot) {
  const storageJson = JSON.stringify(storageSnapshot || { local: {}, session: {} }).replace(/</g, "\\u003c");
  return `
(() => {
  const storageSnapshot = ${storageJson};
  function createStorage(area, initialValues) {
    const values = new Map(Object.entries(initialValues || {}).map(([key, value]) => [String(key), String(value)]));
    const notify = () => {
      try {
        parent.postMessage({
          source: 'poctra-circle-storage',
          area,
          snapshot: Object.fromEntries(values)
        }, '*');
      } catch (_) {}
    };
    return {
      get length() {
        return values.size;
      },
      key(index) {
        const keys = Array.from(values.keys());
        return keys[index] ?? null;
      },
      getItem(key) {
        key = String(key);
        return values.has(key) ? values.get(key) : null;
      },
      setItem(key, value) {
        values.set(String(key), String(value));
        notify();
      },
      removeItem(key) {
        values.delete(String(key));
        notify();
      },
      clear() {
        values.clear();
        notify();
      }
    };
  }
  function installStorage(name, area, values) {
    try {
      void window[name];
      return;
    } catch (_) {
      try {
        Object.defineProperty(window, name, {
          value: createStorage(area, values),
          configurable: false,
          enumerable: true
        });
      } catch (_) {}
    }
  }
  installStorage('localStorage', 'local', storageSnapshot.local);
  installStorage('sessionStorage', 'session', storageSnapshot.session);

  const originalFetch = window.fetch.bind(window);
  const pending = new Map();
  function requestId() {
    return Math.random().toString(36).slice(2) + Date.now().toString(36);
  }
  function inputUrl(input) {
    return typeof input === 'string' ? input : (input && input.url) || '';
  }
  function inputMethod(input, init) {
    return ((init && init.method) || (input && input.method) || 'GET').toUpperCase();
  }
  function requestPath(url) {
    if (!url || url === window.location.href) return '';
    try {
      const parsed = new URL(url, window.location.href);
      if (parsed.protocol === 'chrome-extension:') return parsed.pathname;
      if (parsed.origin === window.location.origin) return parsed.pathname;
      if (String(url).startsWith('/')) return parsed.pathname;
      return '';
    } catch (_) {
      return url.startsWith('/') ? url : '/' + url;
    }
  }
  function isRpcRequest(url, method) {
    if (method !== 'POST') return false;
    const path = requestPath(url).replace(/\\/+/g, '/');
    return !url || url === '/' || url === 'rpc' || url === '/rpc' || url === 'octra-rpc:' || path === '/rpc' || path.endsWith('/rpc');
  }
  function isCircleAssetRequest(url, method) {
    if (method !== 'GET' && method !== 'HEAD') return false;
    if (!url || /^https?:\\/\\//i.test(url) || url.startsWith('data:') || url.startsWith('blob:')) return false;
    return !!requestPath(url);
  }
  async function requestBody(input, init) {
    if (init && init.body !== undefined) return String(init.body || '');
    if (input && typeof input.clone === 'function') {
      try {
        return await input.clone().text();
      } catch (_) {}
    }
    return '';
  }
  function awaitMessageResult(source, id) {
    return new Promise((resolve, reject) => {
      pending.set(id, { resolve, reject, source });
    });
  }
  window.fetch = function(input, init) {
    const url = typeof input === 'string' ? input : (input && input.url) || '';
    const method = inputMethod(input, init);
    if (isCircleAssetRequest(url, method)) {
      const id = requestId();
      parent.postMessage({ source: 'poctra-circle-asset', id, path: requestPath(url) }, '*');
      return awaitMessageResult('poctra-circle-asset-result', id).then((message) => {
        const bytes = Uint8Array.from(atob(message.body || ''), (char) => char.charCodeAt(0));
        return new Response(bytes, {
          status: 200,
          headers: { 'content-type': message.contentType || 'application/octet-stream' }
        });
      });
    }
    if (!isRpcRequest(url, method)) return originalFetch(input, init);
    const id = requestId();
    return requestBody(input, init).then((body) => {
      parent.postMessage({ source: 'poctra-circle-rpc', id, body: String(body || '') }, '*');
      return awaitMessageResult('poctra-circle-rpc-result', id).then((message) => new Response(message.body || 'null', {
        status: 200,
        headers: { 'content-type': 'application/json' }
      }));
    });
  };
  window.addEventListener('message', (event) => {
    const message = event.data;
    if (!message || (message.source !== 'poctra-circle-rpc-result' && message.source !== 'poctra-circle-asset-result')) return;
    const callbacks = pending.get(message.id);
    if (!callbacks) return;
    pending.delete(message.id);
    if (!message.ok) {
      callbacks.reject(new Error(message.message || 'Circle RPC failed.'));
      return;
    }
    callbacks.resolve(message);
  });
  function needsCircleAssetUrl(url) {
    if (!url || /^https?:\\/\\//i.test(url) || url.startsWith('data:') || url.startsWith('blob:')) return false;
    return !!requestPath(url);
  }
  function blobToDataUrl(blob) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result || ''));
      reader.onerror = () => reject(reader.error || new Error('Asset read failed.'));
      reader.readAsDataURL(blob);
    });
  }
  async function circleAssetDataUrl(url) {
    const response = await window.fetch(url);
    return blobToDataUrl(await response.blob());
  }
  function hydrateElementAsset(node) {
    if (!node || node.nodeType !== 1) return;
    const tag = node.tagName.toLowerCase();
    const attr = tag === 'link' ? 'href' : 'src';
    if (tag !== 'img' && tag !== 'source' && tag !== 'video' && tag !== 'audio' && tag !== 'link') return;
    if (tag === 'link' && !(node.getAttribute('rel') || '').toLowerCase().includes('icon')) return;
    const url = node.getAttribute(attr) || '';
    if (!needsCircleAssetUrl(url)) return;
    circleAssetDataUrl(url)
      .then((dataUrl) => {
        if (dataUrl) node.setAttribute(attr, dataUrl);
      })
      .catch(() => {});
  }
  function hydrateExistingAssets() {
    document.querySelectorAll('img[src],source[src],video[src],audio[src],link[href]').forEach(hydrateElementAsset);
  }
  const observer = new MutationObserver((records) => {
    for (const record of records) {
      if (record.type === 'attributes') {
        hydrateElementAsset(record.target);
      } else {
        record.addedNodes.forEach((node) => {
          hydrateElementAsset(node);
          if (node && node.querySelectorAll) {
            node.querySelectorAll('img[src],source[src],video[src],audio[src],link[href]').forEach(hydrateElementAsset);
          }
        });
      }
    }
  });
  const startObserver = () => {
    hydrateExistingAssets();
    observer.observe(document.documentElement, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['src', 'href']
    });
  };
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', startObserver, { once: true });
  } else {
    startObserver();
  }
  document.addEventListener('click', (event) => {
    const link = event.target && event.target.closest && event.target.closest('a[href]');
    if (!link) return;
    const href = link.getAttribute('href') || '';
    if (!href.startsWith('oct://')) return;
    event.preventDefault();
    parent.postMessage({ source: 'poctra-circle-open', href }, '*');
  }, true);
})();
`;
}

function showState(name) {
  loadingState.classList.toggle("hidden", name !== "loading");
  passphraseState.classList.toggle("hidden", name !== "passphrase");
  errorState.classList.toggle("hidden", name !== "error");
  frame.classList.toggle("hidden", name !== "frame");
}

function showError(message) {
  errorMessage.textContent = message;
  showState("error");
}

function revokeObjectUrls() {
  for (const url of activeObjectUrls) URL.revokeObjectURL(url);
  activeObjectUrls = [];
  frame.removeAttribute("src");
  frame.removeAttribute("srcdoc");
}

async function sessionGet(key) {
  if (!chrome.storage.session) return null;
  const result = await chrome.storage.session.get([key]);
  return result[key] || null;
}

async function sessionSet(key, value) {
  if (!chrome.storage.session) return;
  await chrome.storage.session.set({ [key]: value });
}

async function sessionRemove(key) {
  if (!chrome.storage.session) return;
  await chrome.storage.session.remove([key]);
}
