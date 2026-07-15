export async function fetchOctUsdPrice(): Promise<number | null> {
  return (await fetchUsdPrices()).OCT ?? null;
}

export async function fetchUsdPrices(): Promise<Record<string, number>> {
  const cacheKey = "poctraOctPrice";
  const cached = readSessionCache(cacheKey);
  if (cached) {
    try {
      const parsed = JSON.parse(cached) as { value: number | Record<string, number>; expiresAt: number };
      if (parsed.expiresAt > Date.now()) return typeof parsed.value === "number" ? { OCT: parsed.value } : parsed.value;
    } catch { /* Ignore malformed cache and fetch fresh prices. */ }
  }
  const prices: Record<string, number> = { USDT: 1 };
  try {
    const response = await fetch("https://api.coingecko.com/api/v3/simple/price?ids=octra,ethereum,tether&vs_currencies=usd", { cache: "force-cache" });
    if (response.ok) {
      const json = await response.json() as Record<string, { usd?: number }>;
      putPrice(prices, "OCT", json.octra?.usd);
      putPrice(prices, "ETH", json.ethereum?.usd);
      putPrice(prices, "USDT", json.tether?.usd);
    }
  } catch {
    // Public price APIs are optional; the portfolio still shows on-chain balances.
  }
  if (!prices.OCT) {
    try {
      const response = await fetch("https://api.dexscreener.com/latest/dex/search?q=wOCT", { cache: "force-cache" });
      const json = response.ok ? await response.json() as { pairs?: Array<{ priceUsd?: string }> } : {};
      putPrice(prices, "OCT", Number(json.pairs?.[0]?.priceUsd));
    } catch {
      // Leave OCT unpriced when both public sources are unavailable.
    }
  }
  if (prices.OCT) prices.wOCT = prices.OCT;
  writeSessionCache(cacheKey, JSON.stringify({ value: prices, expiresAt: Date.now() + 60_000 }));
  return prices;
}

function readSessionCache(key: string): string | null {
  try { return typeof sessionStorage === "undefined" ? null : sessionStorage.getItem(key); }
  catch { return null; }
}

function writeSessionCache(key: string, value: string): void {
  try { if (typeof sessionStorage !== "undefined") sessionStorage.setItem(key, value); }
  catch { /* Price data remains available in the current snapshot. */ }
}

function putPrice(target: Record<string, number>, symbol: string, value: unknown): void {
  const price = Number(value);
  if (Number.isFinite(price) && price > 0) target[symbol] = price;
}
