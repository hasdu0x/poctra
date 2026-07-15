import { Contract, Wallet, formatEther, formatUnits, id, parseUnits } from "ethers";
import { evmProvider } from "./evm-rpc";
import { evmPrivateKey } from "./wallet";
import type { EvmNetwork, WalletRecord } from "./types";

export type TradeAssetId = "wOCT" | "ETH" | "USDT";

export interface TradeQuote {
  amountInRaw: bigint;
  amountOutRaw: bigint;
  amountOut: string;
  minimumOutRaw: bigint;
  minimumOut: string;
  gasEstimate: bigint;
  gasPrice: bigint;
  gasFeeEth: string;
  route: string;
  liquidityUsd: number | null;
  priceImpactLabel: string;
}

export interface TradeExecution {
  stages: Array<{ label: string; hash: string }>;
  hash: string;
}

export interface TradeCandle {
  timestamp: number;
  open: number;
  high: number;
  low: number;
  close: number;
}

export interface TradeCandleResult {
  candles: TradeCandle[];
  stale: boolean;
  warning?: string;
}

const NATIVE = "0x0000000000000000000000000000000000000000";
const WOCT = "0x4647e1fE715c9e23959022C2416C71867F5a6E80";
const USDT = "0xdAC17F958D2ee523a2206206994597C13D831ec7";
const UNIVERSAL_ROUTER = "0x66a9893cC07D91D95644AEDD05D03f95e1dBA8Af";
const PERMIT2 = "0x000000000022D473030F116dDEE9F6B43aC78BA3";
const V4_QUOTER = "0x52f0e24d1c21c8a0cb1e5a5dd6198556bd9e1203";
const WOCT_ETH_HOOK = "0x890681CfF5AD2069F020027f41f5f68F6a292000";
const ZERO = "0x0000000000000000000000000000000000000000";
const MAX_UINT256 = "f".repeat(64);
const FEE = 3000;
const TICK_SPACING = 60;
const CANDLE_CACHE_PREFIX = "poctra:trade-candles:v1:";
const candleMemory = new Map<string, { fetchedAt: number; candles: TradeCandle[] }>();
const candleRequests = new Map<string, Promise<TradeCandleResult>>();
let marketCache: { fetchedAt: number; pairs: DexPair[] } | null = null;
let marketRequest: Promise<DexPair[]> | null = null;

interface DexPair {
  chainId?: string;
  pairAddress?: string;
  baseToken?: { address?: string };
  quoteToken?: { address?: string };
  liquidity?: { usd?: number };
}

const assets: Record<TradeAssetId, { address: string; decimals: number; name: string }> = {
  wOCT: { address: WOCT, decimals: 6, name: "Wrapped OCT" },
  ETH: { address: NATIVE, decimals: 18, name: "Ethereum" },
  USDT: { address: USDT, decimals: 6, name: "Tether USD" }
};

const ERC20 = [
  "function allowance(address owner,address spender) view returns (uint256)",
  "function approve(address spender,uint256 amount) returns (bool)"
];
const PERMIT2_ABI = ["function approve(address token,address spender,uint160 amount,uint48 expiration)"];

export function isSupportedTradePair(from: TradeAssetId, to: TradeAssetId): boolean {
  return from !== to && (from === "wOCT" || to === "wOCT") && (from === "ETH" || from === "USDT" || to === "ETH" || to === "USDT");
}

export async function quoteTrade(input: {
  network: EvmNetwork;
  from: TradeAssetId;
  to: TradeAssetId;
  amount: string;
  slippageBps: number;
}): Promise<TradeQuote> {
  ensureMainnet(input.network);
  if (!isSupportedTradePair(input.from, input.to)) throw new Error("Trade supports wOCT pairs with ETH or USDT.");
  const amountInRaw = parseUnits(input.amount, assets[input.from].decimals);
  if (amountInRaw <= 0n) throw new Error("Enter a valid amount.");
  const pool = poolFor(input.from, input.to);
  const data = quoteCalldata(pool, amountInRaw);
  const raw = String(await evmProvider(input.network).call({ to: V4_QUOTER, data })).replace(/^0x/, "");
  if (raw.length < 128) throw new Error("The pool returned an empty quote.");
  const amountOutRaw = BigInt(`0x${raw.slice(0, 64)}`);
  const gasEstimate = BigInt(`0x${raw.slice(64, 128)}`);
  const feeData = await evmProvider(input.network).getFeeData();
  const gasPrice = feeData.gasPrice || feeData.maxFeePerGas || 1_000_000_000n;
  const minimumOutRaw = amountOutRaw * BigInt(10_000 - Math.max(1, Math.min(5_000, input.slippageBps))) / 10_000n;
  const liquidityUsd = await fetchPoolLiquidity(input.from, input.to).catch(() => null);
  return {
    amountInRaw,
    amountOutRaw,
    amountOut: trim(formatUnits(amountOutRaw, assets[input.to].decimals)),
    minimumOutRaw,
    minimumOut: trim(formatUnits(minimumOutRaw, assets[input.to].decimals)),
    gasEstimate,
    gasPrice,
    gasFeeEth: trim(formatEther(gasEstimate * gasPrice), 8),
    route: `${input.from}/${input.to} v4 0.30%`,
    liquidityUsd,
    priceImpactLabel: liquidityUsd == null ? "Unavailable" : estimateImpact(amountInRaw, input.from, liquidityUsd)
  };
}

export async function executeTrade(input: {
  network: EvmNetwork;
  wallet: WalletRecord;
  from: TradeAssetId;
  to: TradeAssetId;
  quote: TradeQuote;
  gasPrice?: bigint;
  onStage?: (label: string, hash?: string) => void;
}): Promise<TradeExecution> {
  ensureMainnet(input.network);
  const provider = evmProvider(input.network);
  const signer = new Wallet(evmPrivateKey(input.wallet), provider);
  const owner = await signer.getAddress();
  const stages: Array<{ label: string; hash: string }> = [];
  const overrides = input.gasPrice ? { gasPrice: input.gasPrice } : {};

  if (input.from !== "ETH") {
    const token = new Contract(assets[input.from].address, ERC20, signer);
    const allowance = BigInt(await token.allowance(owner, PERMIT2));
    if (allowance < input.quote.amountInRaw) {
      if (input.from === "USDT" && allowance > 0n) {
        input.onStage?.("Reset token approval");
        const reset = await token.approve(PERMIT2, 0n, overrides);
        stages.push({ label: "Reset approval", hash: reset.hash });
        if ((await reset.wait())?.status !== 1) throw new Error("USDT approval reset reverted.");
      }
      input.onStage?.("Approve token spending");
      const approval = await token.approve(PERMIT2, BigInt(`0x${MAX_UINT256}`), overrides);
      stages.push({ label: "Token approval", hash: approval.hash });
      if ((await approval.wait())?.status !== 1) throw new Error("Token approval reverted.");
    }
    input.onStage?.("Approve Uniswap router");
    const permit = new Contract(PERMIT2, PERMIT2_ABI, signer);
    const permitTx = await permit.approve(assets[input.from].address, UNIVERSAL_ROUTER, input.quote.amountInRaw, BigInt(Math.floor(Date.now() / 1000) + 2_592_000), overrides);
    stages.push({ label: "Permit2 approval", hash: permitTx.hash });
    if ((await permitTx.wait())?.status !== 1) throw new Error("Permit2 approval reverted.");
  }

  input.onStage?.("Swap tokens");
  const pool = poolFor(input.from, input.to);
  const data = routerCalldata(pool, input.quote.amountInRaw, input.quote.minimumOutRaw);
  const swap = await signer.sendTransaction({
    to: UNIVERSAL_ROUTER,
    data,
    value: input.from === "ETH" ? input.quote.amountInRaw : 0n,
    gasPrice: input.gasPrice,
    gasLimit: input.quote.gasEstimate * 130n / 100n
  });
  stages.push({ label: "Swap", hash: swap.hash });
  if ((await swap.wait())?.status !== 1) throw new Error("Swap transaction reverted.");
  return { stages, hash: swap.hash };
}

export async function fetchTradeCandles(frame: "1H" | "4H" | "1D" | "1W", force = false): Promise<TradeCandleResult> {
  const cached = readCandleCache(frame);
  const age = cached ? Date.now() - cached.fetchedAt : Number.POSITIVE_INFINITY;
  if (cached && (!force && age < 60_000 || force && age < 15_000)) return { candles: cached.candles, stale: false };
  const running = candleRequests.get(frame);
  if (running) return running;
  const request = loadTradeCandles(frame, cached).finally(() => candleRequests.delete(frame));
  candleRequests.set(frame, request);
  return request;
}

async function loadTradeCandles(frame: "1H" | "4H" | "1D" | "1W", cached: { fetchedAt: number; candles: TradeCandle[] } | null): Promise<TradeCandleResult> {
  try {
    const pairs = await fetchWoctPairs();
    const pool = pairs.sort((a, b) => Number(b.liquidity?.usd || 0) - Number(a.liquidity?.usd || 0))[0]?.pairAddress;
    if (!pool) throw new Error("wOCT chart pool is unavailable.");
    const config = frame === "1H" ? ["hour", 1, 32] : frame === "4H" ? ["hour", 4, 32] : frame === "1D" ? ["day", 1, 32] : ["day", 7, 24];
    const url = `https://api.geckoterminal.com/api/v2/networks/eth/pools/${pool}/ohlcv/${config[0]}?aggregate=${config[1]}&limit=${config[2]}&currency=usd`;
    const payload = await fetchJson<{ data?: { attributes?: { ohlcv_list?: number[][] } } }>(url, "Chart service");
    const candles = (payload.data?.attributes?.ohlcv_list || [])
      .map((row) => ({ timestamp: row[0], open: row[1], high: row[2], low: row[3], close: row[4] }))
      .filter((row) => row.close > 0 && row.high >= row.low)
      .sort((a, b) => a.timestamp - b.timestamp);
    if (!candles.length) throw new Error("Chart service returned no candles.");
    writeCandleCache(frame, candles);
    return { candles, stale: false };
  } catch (cause) {
    if (cached?.candles.length) return {
      candles: cached.candles,
      stale: true,
      warning: cause instanceof Error ? `${cause.message} Showing cached chart data.` : "Showing cached chart data."
    };
    throw cause;
  }
}

type Pool = { currency0: string; currency1: string; zeroForOne: boolean; hooks: string; input: string; output: string };
function poolFor(from: TradeAssetId, to: TradeAssetId): Pool {
  const fromAddress = assets[from].address;
  const toAddress = assets[to].address;
  const currency0 = BigInt(fromAddress) <= BigInt(toAddress) ? fromAddress : toAddress;
  const currency1 = currency0.toLowerCase() === fromAddress.toLowerCase() ? toAddress : fromAddress;
  return { currency0, currency1, zeroForOne: currency0.toLowerCase() === fromAddress.toLowerCase(), hooks: from === "ETH" || to === "ETH" ? WOCT_ETH_HOOK : ZERO, input: fromAddress, output: toAddress };
}

function quoteCalldata(pool: Pool, amount: bigint): string {
  const head = [addressWord(pool.currency0), addressWord(pool.currency1), word(BigInt(FEE)), signedWord(TICK_SPACING), addressWord(pool.hooks), word(pool.zeroForOne ? 1n : 0n), word(amount), word(256n)].join("");
  return `0x${selector("quoteExactInputSingle(((address,address,uint24,int24,address),bool,uint128,bytes))")}${word(32n)}${head}${word(0n)}`;
}

function routerCalldata(pool: Pool, amount: bigint, minimum: bigint): string {
  const swapHead = [addressWord(pool.currency0), addressWord(pool.currency1), word(BigInt(FEE)), signedWord(TICK_SPACING), addressWord(pool.hooks), word(pool.zeroForOne ? 1n : 0n), word(amount), word(minimum), word(0n), word(320n)].join("");
  const swapParams = `${word(32n)}${swapHead}${word(0n)}`;
  const settle = `${addressWord(pool.input)}${MAX_UINT256}`;
  const take = `${addressWord(pool.output)}${word(0n)}`;
  const routerInput = encodeBytesAndArray("060c0f", [swapParams, settle, take]);
  const commands = encodeBytes("10");
  const inputs = encodeBytesArray([routerInput]);
  const inputsOffset = word(BigInt(96 + commands.length / 2));
  return `0x${selector("execute(bytes,bytes[],uint256)")}${word(96n)}${inputsOffset}${word(BigInt(Math.floor(Date.now() / 1000) + 1200))}${commands}${inputs}`;
}

function encodeBytesAndArray(bytes: string, values: string[]): string {
  const first = encodeBytes(bytes);
  return `${word(64n)}${word(BigInt(64 + first.length / 2))}${first}${encodeBytesArray(values)}`;
}
function encodeBytesArray(values: string[]): string {
  const encoded = values.map(encodeBytes);
  let cursor = values.length * 32;
  const offsets = encoded.map((value) => { const offset = word(BigInt(cursor)); cursor += value.length / 2; return offset; });
  return `${word(BigInt(values.length))}${offsets.join("")}${encoded.join("")}`;
}
function encodeBytes(value: string): string { const clean = value.replace(/^0x/, ""); return `${word(BigInt(clean.length / 2))}${clean.padEnd(Math.ceil(clean.length / 64) * 64, "0")}`; }
function addressWord(value: string): string { return value.replace(/^0x/, "").toLowerCase().padStart(64, "0"); }
function word(value: bigint): string { return value.toString(16).padStart(64, "0"); }
function signedWord(value: number): string { return word(value >= 0 ? BigInt(value) : 2n ** 256n - BigInt(Math.abs(value))); }
function selector(signature: string): string { return id(signature).slice(2, 10); }
function ensureMainnet(network: EvmNetwork): void { if (network.chainId !== 1) throw new Error("Trade is available on Ethereum Mainnet."); }
function trim(value: string, digits = 6): string { const [whole, fraction = ""] = value.split("."); const clean = fraction.slice(0, digits).replace(/0+$/, ""); return clean ? `${whole}.${clean}` : whole; }
function estimateImpact(amount: bigint, from: TradeAssetId, liquidityUsd: number): string { const roughUsd = from === "USDT" ? Number(formatUnits(amount, 6)) : 0; if (!roughUsd || !liquidityUsd) return "Unavailable"; return `${Math.min(99, roughUsd / liquidityUsd * 100).toFixed(2)}%`; }
async function fetchPoolLiquidity(from: TradeAssetId, to: TradeAssetId): Promise<number | null> {
  const pairs = await fetchWoctPairs();
  const other = (from === "wOCT" ? assets[to] : assets[from]).address.toLowerCase();
  const values = pairs.filter((pair) => [pair.baseToken?.address?.toLowerCase(), pair.quoteToken?.address?.toLowerCase()].includes(other)).map((pair) => Number(pair.liquidity?.usd || 0));
  return values.length ? Math.max(...values) : null;
}

async function fetchWoctPairs(): Promise<DexPair[]> {
  if (marketCache && Date.now() - marketCache.fetchedAt < 60_000) return [...marketCache.pairs];
  if (marketRequest) return marketRequest;
  marketRequest = fetchJson<{ pairs?: DexPair[] }>(`https://api.dexscreener.com/latest/dex/tokens/${WOCT}`, "Market data")
    .then((payload) => {
      const pairs = (payload.pairs || []).filter((pair) => pair.chainId === "ethereum" && pair.pairAddress);
      if (!pairs.length) throw new Error("wOCT market data is unavailable.");
      marketCache = { fetchedAt: Date.now(), pairs };
      return [...pairs];
    })
    .finally(() => { marketRequest = null; });
  return marketRequest;
}

function readCandleCache(frame: string): { fetchedAt: number; candles: TradeCandle[] } | null {
  const memory = candleMemory.get(frame);
  if (memory) return memory;
  try {
    const value = localStorage.getItem(`${CANDLE_CACHE_PREFIX}${frame}`);
    if (!value) return null;
    const parsed = JSON.parse(value) as { fetchedAt?: number; candles?: TradeCandle[] };
    if (!parsed.fetchedAt || !Array.isArray(parsed.candles) || Date.now() - parsed.fetchedAt > 86_400_000) return null;
    const cached = { fetchedAt: parsed.fetchedAt, candles: parsed.candles };
    candleMemory.set(frame, cached);
    return cached;
  } catch { return null; }
}

function writeCandleCache(frame: string, candles: TradeCandle[]): void {
  const value = { fetchedAt: Date.now(), candles };
  candleMemory.set(frame, value);
  try { localStorage.setItem(`${CANDLE_CACHE_PREFIX}${frame}`, JSON.stringify(value)); } catch { /* memory cache remains available */ }
}

async function fetchJson<T>(url: string, label: string): Promise<T> {
  const controller = new AbortController();
  const timeout = globalThis.setTimeout(() => controller.abort(), 12_000);
  try {
    const response = await fetch(url, { cache: "no-store", signal: controller.signal });
    if (!response.ok) throw new Error(`${label} HTTP ${response.status}.`);
    return await response.json() as T;
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === "AbortError") throw new Error(`${label} timed out.`);
    throw cause;
  } finally { globalThis.clearTimeout(timeout); }
}
