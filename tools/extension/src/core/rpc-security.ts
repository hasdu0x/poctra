const DEVELOPMENT_HTTP_HOSTS = new Set(["localhost", "127.0.0.1", "165.227.225.79"]);

export function normalizeRpcUrl(value: string): string {
  const url = parseHttpUrl(value, "RPC URL");
  if (url.protocol === "http:" && !DEVELOPMENT_HTTP_HOSTS.has(url.hostname)) {
    throw new Error("Use an HTTPS RPC endpoint. Plain HTTP is limited to localhost and the Octra devnet node.");
  }
  return stripTrailingSlash(url);
}

export function normalizeExplorerUrl(value: string): string {
  if (!value.trim()) return "";
  const url = parseHttpUrl(value, "Explorer URL");
  if (url.protocol !== "https:" && !DEVELOPMENT_HTTP_HOSTS.has(url.hostname)) {
    throw new Error("Use an HTTPS explorer URL.");
  }
  return stripTrailingSlash(url);
}

export async function verifyEvmRpc(rpcUrl: string, expectedChainId: number): Promise<void> {
  const controller = new AbortController();
  const timeout = globalThis.setTimeout(() => controller.abort(), 10_000);
  try {
    const response = await fetch(rpcUrl, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ jsonrpc: "2.0", id: Date.now(), method: "eth_chainId", params: [] }),
      signal: controller.signal,
      cache: "no-store"
    });
    if (!response.ok) throw new Error(`RPC returned HTTP ${response.status}.`);
    const payload = await response.json() as { result?: string; error?: { message?: string } };
    if (payload.error || !payload.result) throw new Error(payload.error?.message || "RPC did not return a chain ID.");
    const actualChainId = Number.parseInt(payload.result, 16);
    if (actualChainId !== expectedChainId) throw new Error(`RPC chain ID is ${actualChainId}, not ${expectedChainId}.`);
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") throw new Error("RPC verification timed out.");
    throw error;
  } finally {
    globalThis.clearTimeout(timeout);
  }
}

function parseHttpUrl(value: string, label: string): URL {
  let url: URL;
  try { url = new URL(value.trim()); } catch { throw new Error(`Enter a valid ${label}.`); }
  if (!["https:", "http:"].includes(url.protocol)) throw new Error(`${label} must use HTTPS or HTTP.`);
  if (url.username || url.password) throw new Error(`${label} must not contain embedded credentials.`);
  return url;
}

function stripTrailingSlash(url: URL): string {
  return url.toString().replace(/\/+$/, "");
}
