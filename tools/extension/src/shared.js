export const DEFAULT_NETWORKS = [
  {
    id: "octra-mainnet",
    name: "Octra Mainnet",
    rpcUrl: "https://octra.network",
    networkType: "mainnet",
    explorerUrl: "https://octrascan.io",
    isDefault: true
  },
  {
    id: "octra-devnet",
    name: "Octra Devnet",
    rpcUrl: "http://165.227.225.79:8080",
    networkType: "devnet",
    explorerUrl: "https://devnet.octrascan.io",
    isDefault: true
  }
];

export const DEFAULT_PROVIDERS = [
  {
    id: "identity-mainnet-octra-id",
    name: "Octra Identity",
    contractAddress: "octH1GEiwunjEZyiBMjp1ETy1VUJJKEEnsMRZ8P2AvTC3et",
    network: "mainnet",
    isDefault: true
  },
  {
    id: "identity-devnet-octra-id",
    name: "Octra Identity",
    contractAddress: "oct8oCgpxvdEytE7tkfjyF8am3xvK28J6rJApkv3omRQnKH",
    network: "devnet",
    isDefault: true
  }
];

const DEFAULT_SETTINGS = {
  activeNetworkId: "octra-mainnet",
  activeProviderByNetwork: {
    mainnet: "identity-mainnet-octra-id",
    devnet: "identity-devnet-octra-id"
  },
  theme: "dark",
  networks: DEFAULT_NETWORKS,
  providers: DEFAULT_PROVIDERS
};

const OCTRA_ADDRESS_LENGTH = 47;
const CIRCLE_READ_CACHE_MS = 8000;
const circleCache = new Map();

export async function loadSettings() {
  const stored = await chrome.storage.local.get(["settings"]);
  const settings = mergeSettings(stored.settings || {});
  await chrome.storage.local.set({ settings });
  return settings;
}

export async function saveSettings(settings) {
  const merged = mergeSettings(settings);
  await chrome.storage.local.set({ settings: merged });
  return merged;
}

export function defaultSettings() {
  return structuredClone(DEFAULT_SETTINGS);
}

function mergeSettings(settings) {
  const networks = uniqueBy(
    [...DEFAULT_NETWORKS, ...(Array.isArray(settings.networks) ? settings.networks : [])],
    (item) => item.id || `${item.networkType}:${item.rpcUrl}`
  ).filter((item) => item.name && item.rpcUrl && item.networkType);
  const providers = uniqueBy(
    [...DEFAULT_PROVIDERS, ...(Array.isArray(settings.providers) ? settings.providers : [])],
    (item) => item.id || `${item.network}:${item.contractAddress}`
  ).filter((item) => item.name && item.contractAddress && item.network);
  const activeNetworkId = networks.some((network) => network.id === settings.activeNetworkId)
    ? settings.activeNetworkId
    : DEFAULT_SETTINGS.activeNetworkId;
  return {
    activeNetworkId,
    activeProviderByNetwork: {
      ...DEFAULT_SETTINGS.activeProviderByNetwork,
      ...(settings.activeProviderByNetwork || {})
    },
    theme: settings.theme === "light" ? "light" : "dark",
    networks,
    providers
  };
}

export function applyTheme(settings) {
  const theme = settings?.theme === "light" ? "light" : "dark";
  document.documentElement.dataset.theme = theme;
  document.documentElement.style.colorScheme = theme;
}

function uniqueBy(items, keyOf) {
  const seen = new Set();
  const out = [];
  for (const item of items) {
    const key = keyOf(item);
    if (!key || seen.has(key)) continue;
    seen.add(key);
    out.push(item);
  }
  return out;
}

export function activeNetwork(settings) {
  return settings.networks.find((network) => network.id === settings.activeNetworkId) || settings.networks[0];
}

export function activeProvider(settings) {
  const network = activeNetwork(settings);
  const providerId = settings.activeProviderByNetwork?.[network.networkType];
  return (
    settings.providers.find((provider) => provider.id === providerId && provider.network === network.networkType) ||
    settings.providers.find((provider) => provider.network === network.networkType) ||
    null
  );
}

export function isOctraAddress(value) {
  const clean = String(value || "").trim();
  return clean.startsWith("oct") && clean.length === OCTRA_ADDRESS_LENGTH;
}

export function shortAddress(value) {
  const clean = String(value || "").trim();
  if (clean.length <= 16) return clean;
  return `${clean.slice(0, 10)}...${clean.slice(-7)}`;
}

export function isIdentityNameInput(value) {
  const clean = String(value || "").trim().toLowerCase();
  if (!clean.endsWith(".oct")) return false;
  const path = clean
    .replace(/\.oct$/i, "")
    .replace(/\.octra$/i, "")
    .replace(/\.id$/i, "");
  const parts = path.split(".");
  return clean.includes(".") && parts.length > 0 && parts.length <= 2 && parts.every((part) => /^[a-z0-9]{3,63}$/.test(part));
}

export function parseCircleInputSpec(value) {
  const raw = String(value || "").trim();
  if (!raw) return null;
  if (/^https?:\/\//i.test(raw)) return null;

  const explicitScheme = /^oct:\/\//i.test(raw);
  const withoutScheme = explicitScheme ? raw.slice(6) : raw;
  const clean = withoutScheme.trim().split("?")[0].split("#")[0];
  if (!clean) return null;

  const slashIndex = clean.indexOf("/");
  const host = (slashIndex >= 0 ? clean.slice(0, slashIndex) : clean).trim();
  const path = slashIndex >= 0 ? normalizeCirclePath(clean.slice(slashIndex)) : "/index.html";
  if (!host) return null;
  if (isOctraAddress(host)) {
    return { host, path, identityName: null, explicitScheme };
  }
  const identity = host.toLowerCase();
  if (identity.endsWith(".oct") && isIdentityNameInput(identity)) {
    return { host: identity, path, identityName: identity, explicitScheme };
  }
  return null;
}

export function normalizeCirclePath(path) {
  const clean = String(path || "").trim();
  if (!clean) return "/index.html";
  return clean.startsWith("/") ? clean : `/${clean}`;
}

export function circleDisplayUri(spec, rawInput) {
  const parsed = spec || parseCircleInputSpec(rawInput);
  const fallback = String(rawInput || "").trim().replace(/^oct:\/\//i, "");
  const host = parsed?.identityName || parsed?.host || fallback.split("/")[0] || fallback;
  const path = parsed?.path && parsed.path !== "/index.html" ? parsed.path : "";
  return `oct://${host}${path}`;
}

export function normalizeWebUrl(input) {
  const raw = String(input || "").trim();
  if (!raw) return null;
  if (/^https?:\/\//i.test(raw)) return raw;
  if (/^[a-z0-9.-]+\.[a-z]{2,}(\/.*)?$/i.test(raw)) return `https://${raw}`;
  return null;
}

export async function resolveInputTarget(rawInput, settings) {
  const spec = parseCircleInputSpec(rawInput);
  if (!spec) {
    const webUrl = normalizeWebUrl(rawInput);
    if (!webUrl) throw new Error("Enter a dApp URL, Octra Identity, or Octra address.");
    return {
      kind: "web",
      title: new URL(webUrl).hostname,
      subtitle: webUrl,
      detail: "Web URL",
      url: webUrl,
      showOctProtocol: false
    };
  }

  const address = await resolveOctraInputAddress(spec, settings);
  const network = activeNetwork(settings);
  const displayName = spec.identityName || shortAddress(address);
  const resolvedLabel = spec.identityName ? `${spec.identityName} -> ${shortAddress(address)}` : shortAddress(address);
  const resourceMode = await circleResourceMode(address, settings);
  if (!resourceMode) {
    if (!network.explorerUrl) throw new Error("Explorer URL is not configured for the active network.");
    const explorerUrl = explorerAddressUrl(network.explorerUrl, address);
    return {
      kind: "explorer",
      title: "Open in Explorer",
      subtitle: resolvedLabel,
      detail: new URL(explorerUrl).hostname,
      url: explorerUrl,
      displayUrl: spec.identityName || address,
      showOctProtocol: false
    };
  }

  const displayUri = circleDisplayUri(spec, rawInput);
  const title = resourceMode === "sealed_read" ? displayName : (await previewCircleIndexTitle(address, settings)) || displayName;
  const viewerUrl = chrome.runtime.getURL(`viewer.html?circleId=${encodeURIComponent(address)}&path=${encodeURIComponent(spec.path)}&display=${encodeURIComponent(displayUri)}&mode=${encodeURIComponent(resourceMode)}`);
  return {
    kind: "circle",
    title,
    subtitle: resolvedLabel,
    detail: resourceMode === "sealed_read" ? "Sealed circle. Passphrase required." : `Public circle app on ${network.name}`,
    url: viewerUrl,
    displayUrl: displayUri,
    circleId: address,
    path: spec.path,
    resourceMode,
    showOctProtocol: true
  };
}

export async function resolveOctraInputAddress(spec, settings) {
  if (isOctraAddress(spec.host)) return spec.host;
  const provider = activeProvider(settings);
  if (!provider) throw new Error("Add an Octra Identity Provider first.");
  const resolved = await resolveIdentityName(provider, spec.host, settings);
  if (!resolved) throw new Error(`${spec.host} could not be resolved.`);
  return resolved;
}

export async function resolveIdentityName(provider, label, settings) {
  const cleanLabel = String(label || "")
    .trim()
    .toLowerCase()
    .replace(/\.oct$/i, "")
    .replace(/\.octra$/i, "")
    .replace(/\.id$/i, "");
  if (!isValidIdentityPath(cleanLabel)) return null;
  const method = cleanLabel.includes(".") ? "resolve_name" : "resolve";
  const primary = await contractCall(provider.contractAddress, method, [cleanLabel], settings).catch(() => null);
  const fallback = primary || (cleanLabel.includes(".") ? await contractCall(provider.contractAddress, "resolve", [cleanLabel], settings).catch(() => null) : null);
  return isOctraAddress(fallback) ? fallback : null;
}

function isValidIdentityPath(value) {
  const parts = String(value || "").split(".");
  return parts.length > 0 && parts.length <= 2 && parts.every((part) => /^[a-z0-9]{3,63}$/.test(part));
}

export async function contractCall(contractAddress, method, params, settings) {
  const response = await callOctraRpc(settings, {
    jsonrpc: "2.0",
    id: 1,
    method: "contract_call",
    params: [contractAddress, method, params]
  });
  return unwrapContractResult(response);
}

function unwrapContractResult(response) {
  if (response?.error) {
    throw new Error(response.error.message || "Contract call failed.");
  }
  const result = response?.result;
  if (result && typeof result === "object" && "result" in result) return result.result || null;
  if (typeof result === "string") return result || null;
  return result ?? null;
}

export async function circleResourceMode(circleId, settings) {
  const key = `${activeNetwork(settings).id}:${circleId}:info`;
  const cached = getCache(key);
  if (cached !== undefined) return cached?.resource_mode || null;
  try {
    const info = await fetchCircleInfo(circleId, settings);
    putCache(key, info);
    return info?.resource_mode || null;
  } catch {
    putCache(key, null);
    return null;
  }
}

export async function fetchCircleInfo(circleId, settings) {
  const response = await callOctraRpc(settings, {
    jsonrpc: "2.0",
    id: 1,
    method: "circle_info",
    params: [circleId]
  });
  if (response?.error) throw new Error(response.error.message || "Circle info lookup failed.");
  if (!response?.result) throw new Error("Circle info was not found.");
  return response.result;
}

export async function fetchCircleAsset(circleId, path, settings) {
  const response = await callOctraRpc(settings, {
    jsonrpc: "2.0",
    id: 1,
    method: "circle_asset",
    params: [circleId, normalizeCirclePath(path)]
  });
  if (response?.error) throw new Error(response.error.message || "Circle asset lookup failed.");
  const result = response?.result;
  if (!result?.body_b64) throw new Error("Circle asset was not found.");
  return decodeCircleAsset(result, circleId, path);
}

export async function fetchSealedCircleAsset(circleId, path, passphrase, settings) {
  const cleanPath = normalizeCirclePath(path);
  const ciphertext = await fetchCircleCiphertext(circleId, cleanPath, settings);
  if (!ciphertext?.ciphertext_b64 || !ciphertext?.key_id || !ciphertext?.plaintext_hash) {
    throw new Error("Sealed asset metadata is incomplete.");
  }
  const body = await decryptSealedCircleBytes(circleId, ciphertext.key_id, passphrase, ciphertext.ciphertext_b64, ciphertext.plaintext_hash);
  return {
    circleId: ciphertext.circle_id || circleId,
    canonicalPath: normalizeCirclePath(ciphertext.canonical_path || cleanPath),
    contentType: ciphertext.content_type || "application/octet-stream",
    body,
    sizeBytes: body.byteLength
  };
}

async function fetchCircleCiphertext(circleId, path, settings) {
  const resourceKey = await circleResourceKey(circleId, path);
  const byKey = await callOctraRpc(settings, {
    jsonrpc: "2.0",
    id: 1,
    method: "circle_asset_ciphertext_by_resource_key",
    params: [circleId, resourceKey]
  }).catch(() => null);
  if (byKey?.result && !byKey.error) return byKey.result;

  const response = await callOctraRpc(settings, {
    jsonrpc: "2.0",
    id: 1,
    method: "circle_asset_ciphertext",
    params: [circleId, path]
  });
  if (response?.error) throw new Error(response.error.message || "Circle sealed asset lookup failed.");
  if (!response?.result) throw new Error("Circle sealed asset was not found.");
  return response.result;
}

export async function circleReadRpc(method, params, settings) {
  if (method === "octra_callProgramView") {
    const request = params?.[0] || {};
    return circleProgramView(request.address, request.method, request.params || [], settings);
  }
  const response = await callOctraRpc(settings, {
    jsonrpc: "2.0",
    id: 1,
    method,
    params: params || []
  });
  if (response?.error) throw new Error(response.error.message || "Circle RPC failed.");
  return response.result ?? null;
}

export async function forwardCircleRpcPayload(payload, settings) {
  if (Array.isArray(payload)) {
    return Promise.all(payload.map((request) => forwardCircleRpcPayload(request, settings)));
  }
  if (!payload || typeof payload !== "object") {
    return { jsonrpc: "2.0", id: null, error: { code: -32600, message: "Invalid JSON-RPC request." } };
  }
  try {
    const method = payload.method;
    const params = Array.isArray(payload.params) ? payload.params : [];
    if (!CIRCLE_READ_METHODS.has(method)) {
      return { jsonrpc: "2.0", id: payload.id ?? null, error: { code: -32601, message: "RPC method is not allowed in circle browser." } };
    }
    const result = await circleReadRpc(method, params, settings);
    return { jsonrpc: "2.0", id: payload.id ?? null, result };
  } catch (error) {
    return { jsonrpc: "2.0", id: payload.id ?? null, error: { code: -32000, message: error.message || "Circle RPC failed." } };
  }
}

const CIRCLE_READ_METHODS = new Set([
  "octra_callProgramView",
  "contract_call",
  "circle_info",
  "circle_asset",
  "node_status",
  "epoch_current",
  "octra_balance",
  "octra_account",
  "octra_transaction",
  "contract_receipt",
  "octra_recommendedFee"
]);

async function circleProgramView(address, method, params, settings) {
  const response = await callOctraRpc(settings, {
    jsonrpc: "2.0",
    id: 1,
    method: "octra_callProgramView",
    params: [{ address, method, params: params || [] }]
  }).catch(async (error) => {
    if (!/method not found|-32601/i.test(error.message || "")) throw error;
    return null;
  });
  if (response && !response.error) return response.result ?? null;
  if (response?.error && !/method not found|-32601/i.test(response.error.message || "")) {
    throw new Error(response.error.message || "Circle program view failed.");
  }
  return contractCall(address, method, params || [], settings);
}

export async function callOctraRpc(settings, payload) {
  const network = activeNetwork(settings);
  const url = rpcEndpoint(network.rpcUrl);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 30000);
  try {
    const response = await fetch(url, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload),
      signal: controller.signal
    });
    const text = await response.text();
    if (!response.ok) throw new Error(`RPC HTTP ${response.status}`);
    return JSON.parse(text);
  } finally {
    clearTimeout(timeout);
  }
}

export function rpcEndpoint(rpcUrl) {
  const clean = String(rpcUrl || "").trim().replace(/\/+$/, "");
  if (!clean) return "";
  return clean.endsWith("/rpc") ? clean : `${clean}/rpc`;
}

export function explorerAddressUrl(explorerUrl, address) {
  return `${String(explorerUrl || "").replace(/\/+$/, "")}/address.html?addr=${encodeURIComponent(address)}`;
}

export async function previewCircleIndexTitle(circleId, settings) {
  try {
    const asset = await fetchCircleAsset(circleId, "/index.html", settings);
    return extractHtmlTitle(asset) || null;
  } catch {
    return null;
  }
}

export function extractHtmlTitle(asset) {
  if (!String(asset?.contentType || "").toLowerCase().includes("html")) return null;
  const html = bytesToText(asset.body);
  const match = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i);
  if (!match) return null;
  return decodeBasicHtmlEntities(match[1].replace(/\s+/g, " ").trim()).slice(0, 48) || null;
}

export function bytesToText(bytes) {
  return new TextDecoder("utf-8").decode(bytes);
}

export function decodeCircleAsset(result, fallbackCircleId, fallbackPath) {
  return {
    circleId: result.circle_id || fallbackCircleId,
    canonicalPath: normalizeCirclePath(result.canonical_path || fallbackPath),
    contentType: result.content_type || "application/octet-stream",
    body: base64ToBytes(result.body_b64),
    sizeBytes: Number(result.size_bytes || 0)
  };
}

export function assetBlobUrl(asset) {
  const blob = new Blob([asset.body], { type: asset.contentType || "application/octet-stream" });
  return URL.createObjectURL(blob);
}

export function base64ToBytes(value) {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

function bytesToBase64(bytes) {
  let out = "";
  const chunkSize = 0x8000;
  for (let index = 0; index < bytes.length; index += chunkSize) {
    out += String.fromCharCode(...bytes.subarray(index, index + chunkSize));
  }
  return btoa(out);
}

export function bytesToDataUrl(asset) {
  return `data:${asset.contentType || "application/octet-stream"};base64,${bytesToBase64(asset.body)}`;
}

async function decryptSealedCircleBytes(circleId, keyId, passphrase, ciphertextB64, plaintextHash) {
  const envelope = base64ToBytes(ciphertextB64);
  const magic = new TextEncoder().encode("OCRS1");
  if (envelope.length <= magic.length + 12 || !bytesEqual(envelope.slice(0, magic.length), magic)) {
    throw new Error("Invalid sealed circle envelope.");
  }
  const nonce = envelope.slice(magic.length, magic.length + 12);
  const cipherBytes = envelope.slice(magic.length + 12);
  const key = await deriveCircleReadKey(circleId, keyId, passphrase);
  let plainFrame;
  try {
    plainFrame = new Uint8Array(await crypto.subtle.decrypt({ name: "AES-GCM", iv: nonce, tagLength: 128 }, key, cipherBytes));
  } catch {
    throw new Error("Sealed read passphrase did not match this circle.");
  }
  if (plainFrame.length < 4) throw new Error("Invalid sealed circle payload.");
  const plainSize = readU32Be(plainFrame, 0);
  if (plainSize < 0 || plainSize > plainFrame.length - 4) throw new Error("Invalid sealed circle payload length.");
  const plaintext = plainFrame.slice(4, 4 + plainSize);
  const actualHash = await sha256Hex(plaintext);
  if (actualHash.toLowerCase() !== String(plaintextHash || "").trim().toLowerCase()) {
    throw new Error("Sealed circle plaintext hash mismatch.");
  }
  return plaintext;
}

async function deriveCircleReadKey(circleId, keyId, passphrase) {
  const salt = new TextEncoder().encode(`octra:circle:sealed_read:v1:${circleId}:${keyId}`);
  const rawKey = await crypto.subtle.importKey("raw", new TextEncoder().encode(passphrase), "PBKDF2", false, ["deriveKey"]);
  return crypto.subtle.deriveKey(
    { name: "PBKDF2", hash: "SHA-256", salt, iterations: 120000 },
    rawKey,
    { name: "AES-GCM", length: 256 },
    false,
    ["decrypt"]
  );
}

async function circleResourceKey(circleId, canonicalPath) {
  const encoder = new TextEncoder();
  const frames = [
    encoder.encode("octra:circle_resource_key:v1"),
    new Uint8Array([0]),
    frameBytes(encoder.encode(circleId)),
    frameBytes(encoder.encode(normalizeCirclePath(canonicalPath)))
  ];
  const total = frames.reduce((sum, bytes) => sum + bytes.length, 0);
  const out = new Uint8Array(total);
  let offset = 0;
  for (const bytes of frames) {
    out.set(bytes, offset);
    offset += bytes.length;
  }
  return sha256Hex(out);
}

function frameBytes(bytes) {
  const out = new Uint8Array(bytes.length + 4);
  out[0] = (bytes.length >>> 24) & 0xff;
  out[1] = (bytes.length >>> 16) & 0xff;
  out[2] = (bytes.length >>> 8) & 0xff;
  out[3] = bytes.length & 0xff;
  out.set(bytes, 4);
  return out;
}

function readU32Be(bytes, offset) {
  return ((bytes[offset] & 0xff) << 24) | ((bytes[offset + 1] & 0xff) << 16) | ((bytes[offset + 2] & 0xff) << 8) | (bytes[offset + 3] & 0xff);
}

async function sha256Hex(bytes) {
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", bytes));
  return [...digest].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function bytesEqual(a, b) {
  if (a.length !== b.length) return false;
  return a.every((value, index) => value === b[index]);
}

function getCache(key) {
  const entry = circleCache.get(key);
  if (!entry) return undefined;
  if (entry.expiresAt < Date.now()) {
    circleCache.delete(key);
    return undefined;
  }
  return entry.value;
}

function putCache(key, value) {
  if (circleCache.size > 512) circleCache.clear();
  circleCache.set(key, { value, expiresAt: Date.now() + CIRCLE_READ_CACHE_MS });
}

function decodeBasicHtmlEntities(value) {
  return String(value || "")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, "\"")
    .replace(/&#39;/g, "'")
    .replace(/&apos;/g, "'");
}
