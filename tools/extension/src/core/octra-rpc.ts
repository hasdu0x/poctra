import { bytesToBase64, formatUnits, parseUnits, utf8 } from "./encoding";
import type { ActivityItem, OctraBalance, OctraNetwork, TokenBalance, WalletRecord } from "./types";
import { isValidOctraAddress, signOctraBytes } from "./wallet";

interface RpcResponse<T> {
  result?: T;
  error?: { code?: number; message?: string; reason?: string };
}

const cache = new Map<string, { expiresAt: number; value: unknown }>();

export async function octraRpc<T>(network: OctraNetwork, method: string, params: unknown[] = [], timeoutMs = 12_000): Promise<T> {
  const controller = new AbortController();
  const timeout = globalThis.setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(rpcEndpoint(network.rpcUrl), {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ jsonrpc: "2.0", id: Date.now(), method, params }),
      signal: controller.signal,
      cache: "no-store"
    });
    if (!response.ok) throw new Error(`Octra RPC HTTP ${response.status}.`);
    const payload = await response.json() as RpcResponse<T>;
    if (payload.error) throw new Error(payload.error.message || payload.error.reason || "Octra RPC returned an error.");
    if (!("result" in payload)) throw new Error(`${method} returned no result.`);
    return payload.result as T;
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") throw new Error("Octra RPC timed out. Retry or change network.");
    throw error;
  } finally {
    globalThis.clearTimeout(timeout);
  }
}

export async function fetchOctraBalance(network: OctraNetwork, address: string): Promise<OctraBalance> {
  const result = await cached(`balance:${network.id}:${address}`, 4_000, () =>
    octraRpc<Record<string, unknown>>(network, "octra_balance", [address])
  );
  const rawValue = result.balance_raw ?? result.balance ?? 0;
  const raw = typeof rawValue === "number"
    ? BigInt(Math.round(rawValue > 1_000_000 ? rawValue : rawValue * 1_000_000))
    : String(rawValue).includes(".")
      ? parseUnits(String(rawValue), 6)
      : BigInt(String(rawValue || 0));
  return {
    raw,
    amount: Number(raw) / 1_000_000,
    nonce: Number(result.pending_nonce ?? result.nonce ?? 0)
  };
}

export async function fetchRecommendedFee(network: OctraNetwork, opType = "standard"): Promise<bigint> {
  try {
    const result = await cached(`fee:${network.id}:${opType}`, 15_000, () =>
      octraRpc<{ recommended?: string | number }>(network, "octra_recommendedFee", [opType])
    );
    const fee = BigInt(result.recommended || 0);
    return fee > 0n ? fee : fallbackFee(opType);
  } catch {
    return fallbackFee(opType);
  }
}

export async function fetchOctraActivity(network: OctraNetwork, address: string, limit = 7): Promise<ActivityItem[]> {
  type HistoryResponse = {
    transactions?: Array<Record<string, unknown>>;
    recent_txs?: Array<Record<string, unknown>>;
  };
  type StagingResponse = {
    transactions?: Array<Record<string, unknown>>;
    staged_transactions?: Array<Record<string, unknown>>;
  };
  const count = Math.min(limit, 50);
  const [history, staging] = await Promise.all([
    octraRpc<HistoryResponse>(network, "octra_transactionsByAddress", [address, count]).catch(() => null),
    octraRpc<StagingResponse>(network, "staging_view", [address]).catch((): StagingResponse => ({ transactions: [] }))
  ]);

  let confirmedRows = history?.transactions || history?.recent_txs || [];
  if (!history) {
    const account = await octraRpc<HistoryResponse>(network, "octra_account", [address, count]);
    confirmedRows = await hydrateActivityRows(network, account.recent_txs || [], count);
  }
  const confirmed = confirmedRows.map((item) => mapOctraActivity(item, address, "confirmed"));
  const pendingRows = staging.transactions || staging.staged_transactions || [];
  const pending = pendingRows.map((item) => mapOctraActivity(item, address, "pending"));
  return [...pending, ...confirmed]
    .filter((item) => item.hash)
    .filter((item, index, list) => list.findIndex((candidate) => candidate.hash === item.hash) === index)
    .sort((a, b) => b.timestamp - a.timestamp)
    .slice(0, limit);
}

async function hydrateActivityRows(
  network: OctraNetwork,
  rows: Array<Record<string, unknown>>,
  limit: number
): Promise<Array<Record<string, unknown>>> {
  const settled = await Promise.allSettled(rows.slice(0, limit).map(async (row) => {
    if (row.from || row.to || row.to_) return row;
    const hash = String(row.hash || row.tx_hash || "");
    if (!hash) return row;
    return await fetchOctraTransaction(network, hash) || row;
  }));
  return settled.map((result, index) => result.status === "fulfilled" ? result.value : rows[index]);
}

export async function fetchOctraTransaction(network: OctraNetwork, hash: string): Promise<Record<string, unknown> | null> {
  try {
    return await octraRpc<Record<string, unknown>>(network, "octra_transaction", [hash]);
  } catch {
    return null;
  }
}

export async function submitOctraTransfer(input: {
  network: OctraNetwork;
  wallet: WalletRecord;
  recipient: string;
  amount: string;
  feeOu: bigint;
  message?: string;
  opType?: string;
  encryptedData?: string;
}): Promise<{ hash: string; status: string }> {
  const recipient = input.recipient.trim();
  if (!isValidOctraAddress(recipient)) throw new Error("Enter a valid Octra recipient address.");
  if (recipient === input.wallet.address) throw new Error("Cannot send to the active wallet address.");
  const amountRaw = parseUnits(input.amount, 6);
  if (amountRaw <= 0n) throw new Error("Amount must be greater than zero.");
  const balance = await fetchOctraBalance(input.network, input.wallet.address);
  if (amountRaw + input.feeOu > balance.raw) throw new Error("Amount exceeds available balance after network fee.");
  return submitOctraRaw({
    ...input,
    amountRaw,
    nonce: balance.nonce + 1,
    opType: input.opType || "standard"
  });
}

export async function submitOctraContractCall(input: {
  network: OctraNetwork;
  wallet: WalletRecord;
  contractAddress: string;
  method: string;
  params: unknown[];
  feeOu: bigint;
  amountRaw?: bigint;
}): Promise<{ hash: string; status: string }> {
  if (!isValidOctraAddress(input.contractAddress)) throw new Error("Enter a valid Octra contract address.");
  const balance = await fetchOctraBalance(input.network, input.wallet.address);
  return submitOctraRaw({
    network: input.network,
    wallet: input.wallet,
    recipient: input.contractAddress,
    amountRaw: input.amountRaw || 0n,
    nonce: balance.nonce + 1,
    feeOu: input.feeOu,
    message: JSON.stringify(input.params),
    opType: "call",
    encryptedData: input.method
  });
}

export async function contractView(
  network: OctraNetwork,
  contractAddress: string,
  method: string,
  params: unknown[],
  caller: string
): Promise<string | null> {
  try {
    const result = await octraRpc<{ result?: unknown }>(network, "contract_call", [contractAddress, method, params, caller]);
    const value = result?.result;
    return value == null ? null : String(value);
  } catch {
    const result = await octraRpc<unknown>(network, "octra_callProgramView", [{ address: contractAddress, method, params }]);
    if (result && typeof result === "object" && "result" in (result as Record<string, unknown>)) {
      return String((result as Record<string, unknown>).result ?? "") || null;
    }
    return result == null ? null : String(result);
  }
}

export async function fetchOctraTokens(
  network: OctraNetwork,
  walletAddress: string,
  savedContracts: string[]
): Promise<TokenBalance[]> {
  const discovered = await octraRpc<{ tokens?: Array<Record<string, unknown>> }>(network, "octra_tokensByAddress", [walletAddress])
    .then((result) => result.tokens || [])
    .catch(() => []);
  const contracts = [...new Set([...discovered.map((item) => String(item.address || "")), ...savedContracts].filter(isValidOctraAddress))];
  const loaded = await Promise.all(contracts.map(async (contractAddress) => {
    const known = discovered.find((item) => item.address === contractAddress);
    const [name, symbol, decimals, balance] = await Promise.all([
      known?.name ? String(known.name) : contractView(network, contractAddress, "get_name", [], walletAddress),
      known?.symbol ? String(known.symbol) : contractView(network, contractAddress, "get_symbol", [], walletAddress),
      known?.decimals != null ? String(known.decimals) : contractView(network, contractAddress, "decimals", [], walletAddress),
      known?.balance != null ? String(known.balance) : contractView(network, contractAddress, "balance_of", [walletAddress], walletAddress)
    ]);
    const raw = BigInt(balance || 0);
    const decimalCount = Number(decimals || 0);
    return {
      contractAddress,
      name: name || symbol || "Octra token",
      symbol: symbol || "TOKEN",
      decimals: decimalCount,
      raw,
      formatted: formatUnits(raw, decimalCount),
      network: "octra" as const
    };
  }));
  return loaded;
}

function rpcEndpoint(url: string): string {
  const clean = url.trim().replace(/\/+$/, "");
  return clean.endsWith("/rpc") ? clean : `${clean}/rpc`;
}

async function submitOctraRaw(input: {
  network: OctraNetwork;
  wallet: WalletRecord;
  recipient: string;
  amountRaw: bigint;
  nonce: number;
  feeOu: bigint;
  message?: string;
  opType: string;
  encryptedData?: string;
}): Promise<{ hash: string; status: string }> {
  const tx = buildSignedOctraTransaction(input);
  return submitSignedOctraTransaction(input.network, tx);
}

export function buildSignedOctraTransaction(input: {
  wallet: WalletRecord;
  recipient: string;
  amountRaw: bigint;
  nonce: number;
  feeOu: bigint;
  message?: string;
  opType: string;
  encryptedData?: string;
}): Record<string, unknown> {
  const timestampMs = Date.now();
  const timestampText = canonicalTimestamp(timestampMs);
  const timestamp = Number(timestampText);
  const canonical = canonicalTransaction({
    from: input.wallet.address,
    to: input.recipient,
    amountRaw: input.amountRaw.toString(),
    nonce: input.nonce,
    ou: input.feeOu > 0n ? input.feeOu.toString() : "1",
    timestamp: timestampText,
    opType: input.opType,
    message: input.message,
    encryptedData: input.encryptedData
  });
  const tx: Record<string, unknown> = {
    from: input.wallet.address,
    to_: input.recipient,
    amount: input.amountRaw.toString(),
    nonce: input.nonce,
    ou: input.feeOu > 0n ? input.feeOu.toString() : "1",
    timestamp,
    op_type: input.opType,
    signature: bytesToBase64(signOctraBytes(input.wallet, utf8(canonical))),
    public_key: bytesToBase64(Uint8Array.from(input.wallet.publicKeyHex.match(/.{2}/g) || [], (pair) => Number.parseInt(pair, 16)))
  };
  if (input.message) tx.message = input.message;
  if (input.encryptedData) tx.encrypted_data = input.encryptedData;
  return tx;
}

export async function submitSignedOctraTransaction(network: OctraNetwork, tx: Record<string, unknown>): Promise<{ hash: string; status: string }> {
  const result = await octraRpc<Record<string, unknown>>(network, "octra_submit", [tx], 20_000);
  const status = String(result.status || "accepted").toLowerCase();
  if (status === "rejected") throw new Error(String(result.reason || "Transaction rejected."));
  const hash = String(result.tx_hash || result.hash || "");
  if (!hash) throw new Error("Octra node accepted the transaction without returning a hash.");
  cache.delete(`balance:${network.id}:${String(tx.from || "")}`);
  return { hash, status };
}

function canonicalTransaction(input: {
  from: string; to: string; amountRaw: string; nonce: number; ou: string; timestamp: string;
  opType: string; encryptedData?: string; message?: string;
}): string {
  let output = `{"from":${JSON.stringify(input.from)},"to_":${JSON.stringify(input.to)},"amount":${JSON.stringify(input.amountRaw)},"nonce":${input.nonce},"ou":${JSON.stringify(input.ou)},"timestamp":${input.timestamp},"op_type":${JSON.stringify(input.opType || "standard")}`;
  if (input.encryptedData) output += `,"encrypted_data":${JSON.stringify(input.encryptedData)}`;
  if (input.message) output += `,"message":${JSON.stringify(input.message)}`;
  return `${output}}`;
}

function canonicalTimestamp(epochMillis: number): string {
  const seconds = Math.floor(epochMillis / 1000);
  const millis = String(epochMillis % 1000).padStart(3, "0").replace(/0+$/, "");
  return `${seconds}.${millis || "0"}`;
}

function mapOctraActivity(item: Record<string, unknown>, address: string, fallbackStatus: "pending" | "confirmed"): ActivityItem {
  const from = String(item.from || "");
  const to = String(item.to || item.to_ || "");
  const amountValue = item.amount_raw ?? item.amount ?? 0;
  const amount = String(amountValue).includes(".") ? Number(amountValue) : Number(amountValue) / 1_000_000;
  return {
    hash: String(item.hash || item.tx_hash || `pending-${item.nonce || Date.now()}`),
    from,
    to,
    amount,
    timestamp: normalizeTimestamp(item.timestamp ?? item.block_timestamp ?? item.time ?? item.created_at),
    status: String(item.status || fallbackStatus).toLowerCase() === "rejected" ? "rejected" : fallbackStatus,
    opType: String(item.op_type || "standard").toLowerCase(),
    network: "octra",
    symbol: "OCT",
    message: item.message ? String(item.message) : undefined
  };
}

function normalizeTimestamp(value: unknown): number {
  const timestamp = Number(value || 0);
  if (!Number.isFinite(timestamp) || timestamp <= 0) return 0;
  return timestamp < 1_000_000_000_000 ? Math.round(timestamp * 1000) : Math.round(timestamp);
}

function fallbackFee(opType: string): bigint {
  return opType === "call" ? 1_100_000n : 850_000n;
}

async function cached<T>(key: string, ttlMs: number, loader: () => Promise<T>): Promise<T> {
  const hit = cache.get(key);
  if (hit && hit.expiresAt > Date.now()) return hit.value as T;
  const value = await loader();
  cache.set(key, { expiresAt: Date.now() + ttlMs, value });
  return value;
}
