import { AbstractProvider, Contract, FallbackProvider, JsonRpcProvider, MaxUint256, Wallet, formatEther, formatUnits as ethersFormatUnits, parseEther, parseUnits } from "ethers";
import type { ActivityItem, EvmAsset, EvmNetwork, EvmNft, TokenBalance, WalletRecord } from "./types";
import { evmPrivateKey } from "./wallet";

const ERC20_ABI = [
  "function name() view returns (string)",
  "function symbol() view returns (string)",
  "function decimals() view returns (uint8)",
  "function balanceOf(address) view returns (uint256)",
  "function transfer(address,uint256) returns (bool)",
  "function allowance(address,address) view returns (uint256)",
  "function approve(address,uint256) returns (bool)"
];
const ERC721_ABI = [
  "function name() view returns (string)",
  "function symbol() view returns (string)",
  "function ownerOf(uint256 tokenId) view returns (address)",
  "function safeTransferFrom(address from,address to,uint256 tokenId)"
];

const providers = new Map<string, AbstractProvider>();
const KNOWN_SPENDERS = [
  { name: "Uniswap Permit2", address: "0x000000000022D473030F116dDEE9F6B43aC78BA3" },
  { name: "Uniswap Router", address: "0x66a9893cC07D91D95644AEDD05D03f95e1dBA8Af" },
  { name: "Octra wOCT Bridge", address: "0xE7eD69b852fd2a1406080B26A37e8E04e7dA4caE" }
] as const;

export interface EvmApproval {
  token: EvmAsset;
  spenderName: string;
  spenderAddress: string;
  allowanceRaw: bigint;
  allowance: string;
}

export function evmProvider(network: EvmNetwork): AbstractProvider {
  const key = `${network.chainId}:${network.rpcUrl}`;
  const existing = providers.get(key);
  if (existing) return existing;
  const rpcUrls = evmRpcCandidates(network);
  const staticNetwork = { chainId: network.chainId, name: network.name };
  const rpcProviders = rpcUrls.map((rpcUrl) => new JsonRpcProvider(rpcUrl, staticNetwork, { staticNetwork: true }));
  const provider = rpcProviders.length === 1
    ? rpcProviders[0]
    : new FallbackProvider(
      rpcProviders.map((candidate, index) => ({ provider: candidate, priority: index + 1, stallTimeout: 900, weight: 1 })),
      staticNetwork,
      { quorum: 1 }
    );
  providers.set(key, provider);
  return provider;
}

export async function fetchEvmBalance(network: EvmNetwork, address: string): Promise<{ wei: bigint; formatted: string }> {
  const wei = await evmProvider(network).getBalance(address);
  return { wei, formatted: trimAmount(formatEther(wei)) };
}

export async function fetchEvmFeeOptions(network: EvmNetwork, gasLimit = 21_000n): Promise<{
  gasLimit: bigint; low: bigint; market: bigint; fast: bigint;
}> {
  const fee = await evmProvider(network).getFeeData();
  const marketPrice = fee.gasPrice || fee.maxFeePerGas || 1_000_000_000n;
  return {
    gasLimit,
    low: marketPrice * 85n / 100n,
    market: marketPrice,
    fast: marketPrice * 125n / 100n
  };
}

export async function sendEvmNative(input: {
  network: EvmNetwork;
  wallet: WalletRecord;
  recipient: string;
  amount: string;
  gasPrice?: bigint;
}): Promise<{ hash: string; wait: () => Promise<boolean> }> {
  const provider = evmProvider(input.network);
  const signer = new Wallet(evmPrivateKey(input.wallet), provider);
  const recipient = await resolveEvmRecipient(provider, input.recipient);
  const transaction = await signer.sendTransaction({
    to: recipient,
    value: parseEther(input.amount),
    gasPrice: input.gasPrice
  });
  return {
    hash: transaction.hash,
    wait: async () => (await transaction.wait())?.status === 1
  };
}

export async function sendEvmProviderTransaction(input: {
  network: EvmNetwork;
  wallet: WalletRecord;
  to: string;
  value?: string;
  data?: string;
  gasLimit?: string;
}): Promise<string> {
  const signer = new Wallet(evmPrivateKey(input.wallet), evmProvider(input.network));
  const to = await resolveEvmRecipient(evmProvider(input.network), input.to);
  const transaction = await signer.sendTransaction({
    to,
    value: parseEther(input.value || "0"),
    data: input.data || "0x",
    gasLimit: input.gasLimit ? BigInt(input.gasLimit) : undefined
  });
  return transaction.hash;
}

export async function signEvmMessage(wallet: WalletRecord, message: string): Promise<string> {
  return new Wallet(evmPrivateKey(wallet)).signMessage(message);
}

export async function signEvmTypedData(wallet: WalletRecord, input: {
  domain: Record<string, unknown>;
  types: Record<string, Array<{ name: string; type: string }>>;
  value: Record<string, unknown>;
}): Promise<string> {
  const types = { ...input.types };
  delete types.EIP712Domain;
  return new Wallet(evmPrivateKey(wallet)).signTypedData(input.domain, types, input.value);
}

export async function sendErc20(input: {
  network: EvmNetwork;
  wallet: WalletRecord;
  asset: EvmAsset;
  recipient: string;
  amount: string;
  gasPrice?: bigint;
}): Promise<{ hash: string; wait: () => Promise<boolean> }> {
  const provider = evmProvider(input.network);
  const signer = new Wallet(evmPrivateKey(input.wallet), provider);
  const recipient = await resolveEvmRecipient(provider, input.recipient);
  const contract = new Contract(input.asset.contractAddress, ERC20_ABI, signer);
  const transaction = await contract.transfer(recipient, parseUnits(input.amount, input.asset.decimals), { gasPrice: input.gasPrice });
  return {
    hash: transaction.hash as string,
    wait: async () => (await transaction.wait())?.status === 1
  };
}

export async function fetchEvmTokens(network: EvmNetwork, address: string, assets: EvmAsset[]): Promise<TokenBalance[]> {
  const provider = evmProvider(network);
  const selected = assets.filter((asset) => asset.chainId === network.chainId);
  return Promise.all(selected.map(async (asset) => {
    const contract = new Contract(asset.contractAddress, ERC20_ABI, provider);
    const raw = BigInt(await contract.balanceOf(address));
    return {
      contractAddress: asset.contractAddress,
      name: asset.name,
      symbol: asset.symbol,
      decimals: asset.decimals,
      raw,
      formatted: trimAmount(ethersFormatUnits(raw, asset.decimals)),
      network: "evm" as const
    };
  }));
}

export async function inspectErc20(network: EvmNetwork, contractAddress: string, owner: string): Promise<EvmAsset & { balance: string }> {
  const contract = new Contract(contractAddress, ERC20_ABI, evmProvider(network));
  const [name, symbol, decimals, balance] = await Promise.all([
    contract.name(), contract.symbol(), contract.decimals(), contract.balanceOf(owner)
  ]);
  return {
    contractAddress,
    name: String(name),
    symbol: String(symbol),
    decimals: Number(decimals),
    chainId: network.chainId,
    balance: trimAmount(ethersFormatUnits(balance, Number(decimals)))
  };
}

export async function getEvmTokenInfo(network: EvmNetwork, token: string): Promise<EvmAsset> {
  const contract = new Contract(token, ERC20_ABI, evmProvider(network));
  const [name, symbol, decimals] = await Promise.all([contract.name(), contract.symbol(), contract.decimals()]);
  return { contractAddress: token, name: String(name), symbol: String(symbol), decimals: Number(decimals), chainId: network.chainId };
}

export async function getEvmTokenBalance(network: EvmNetwork, token: string, owner: string): Promise<bigint> {
  return BigInt(await new Contract(token, ERC20_ABI, evmProvider(network)).balanceOf(owner));
}

export async function getEvmTokenAllowance(network: EvmNetwork, token: string, owner: string, spender: string): Promise<bigint> {
  return BigInt(await new Contract(token, ERC20_ABI, evmProvider(network)).allowance(owner, spender));
}

export async function transferEvmTokenRaw(input: { network: EvmNetwork; wallet: WalletRecord; token: string; to: string; amountRaw: bigint }): Promise<string> {
  const signer = new Wallet(evmPrivateKey(input.wallet), evmProvider(input.network));
  const recipient = await resolveEvmRecipient(evmProvider(input.network), input.to);
  const transaction = await new Contract(input.token, ERC20_ABI, signer).transfer(recipient, input.amountRaw);
  return transaction.hash as string;
}

export async function approveEvmTokenRaw(input: { network: EvmNetwork; wallet: WalletRecord; token: string; spender: string; amountRaw?: bigint }): Promise<string> {
  const signer = new Wallet(evmPrivateKey(input.wallet), evmProvider(input.network));
  const transaction = await new Contract(input.token, ERC20_ABI, signer).approve(input.spender, input.amountRaw ?? MaxUint256);
  return transaction.hash as string;
}

export async function inspectNft(network: EvmNetwork, contractAddress: string, tokenId: string, owner: string): Promise<EvmNft> {
  const contract = new Contract(contractAddress, ERC721_ABI, evmProvider(network));
  const [name, symbol, tokenOwner] = await Promise.all([contract.name(), contract.symbol(), contract.ownerOf(BigInt(tokenId))]);
  if (String(tokenOwner).toLowerCase() !== owner.toLowerCase()) throw new Error("The active wallet does not own this NFT.");
  return { contractAddress, tokenId: BigInt(tokenId).toString(), name: String(name), symbol: String(symbol), chainId: network.chainId };
}

export async function fetchKnownApprovals(network: EvmNetwork, owner: string, assets: EvmAsset[]): Promise<EvmApproval[]> {
  const provider = evmProvider(network);
  const requests = assets.filter((asset) => asset.chainId === network.chainId).flatMap((token) =>
    KNOWN_SPENDERS.map(async (spender): Promise<EvmApproval | null> => {
      const contract = new Contract(token.contractAddress, ERC20_ABI, provider);
      const allowanceRaw = BigInt(await contract.allowance(owner, spender.address));
      return allowanceRaw > 0n ? {
        token,
        spenderName: spender.name,
        spenderAddress: spender.address,
        allowanceRaw,
        allowance: trimAmount(ethersFormatUnits(allowanceRaw, token.decimals))
      } : null;
    })
  );
  const settled = await Promise.allSettled(requests);
  return settled.flatMap((result) => result.status === "fulfilled" && result.value ? [result.value] : []);
}

export async function revokeErc20Approval(input: {
  network: EvmNetwork;
  wallet: WalletRecord;
  token: EvmAsset;
  spender: string;
  gasPrice?: bigint;
}): Promise<{ hash: string; wait: () => Promise<boolean> }> {
  const signer = new Wallet(evmPrivateKey(input.wallet), evmProvider(input.network));
  const contract = new Contract(input.token.contractAddress, ERC20_ABI, signer);
  const transaction = await contract.approve(input.spender, 0n, { gasPrice: input.gasPrice });
  return { hash: transaction.hash as string, wait: async () => (await transaction.wait())?.status === 1 };
}

export async function sendNft(input: { network: EvmNetwork; wallet: WalletRecord; nft: EvmNft; recipient: string; gasPrice?: bigint }): Promise<{ hash: string; wait: () => Promise<boolean> }> {
  const provider = evmProvider(input.network);
  const signer = new Wallet(evmPrivateKey(input.wallet), provider);
  const recipient = await resolveEvmRecipient(provider, input.recipient);
  const contract = new Contract(input.nft.contractAddress, ERC721_ABI, signer);
  const transaction = await contract.safeTransferFrom(input.wallet.evmAddress, recipient, BigInt(input.nft.tokenId), { gasPrice: input.gasPrice });
  return { hash: transaction.hash as string, wait: async () => (await transaction.wait())?.status === 1 };
}

export async function fetchEvmActivity(network: EvmNetwork, address: string, limit = 7): Promise<ActivityItem[]> {
  if (![1, 11155111].includes(network.chainId)) return [];
  const actions = ["txlist", "txlistinternal", "tokentx"] as const;
  const settled = await Promise.allSettled(actions.map((action) => fetchEtherscanRows(network, address, action, limit)));
  const activity = settled.flatMap((result, index) => result.status === "fulfilled"
    ? mapEtherscanActivity(result.value, actions[index], network)
    : []);

  if (settled[0].status === "rejected") {
    activity.push(...await fetchBlockscoutActivity(network, address, limit).catch(() => []));
  }
  if (!activity.length && settled.every((result) => result.status === "rejected")) {
    const reason = settled[0].status === "rejected" ? settled[0].reason : null;
    throw reason instanceof Error ? reason : new Error("EVM history is temporarily unavailable.");
  }
  return activity
    .filter((item, index, rows) => rows.findIndex((candidate) => activityKey(candidate) === activityKey(item)) === index)
    .sort((a, b) => b.timestamp - a.timestamp)
    .slice(0, limit);
}

async function fetchEtherscanRows(
  network: EvmNetwork,
  address: string,
  action: "txlist" | "txlistinternal" | "tokentx",
  limit: number
): Promise<Array<Record<string, string>>> {
  const endpoint = new URL("https://rpc.xme.my.id/api/etherscan/account");
  endpoint.searchParams.set("chainId", String(network.chainId));
  endpoint.searchParams.set("action", action);
  endpoint.searchParams.set("address", address);
  endpoint.searchParams.set("startblock", "0");
  endpoint.searchParams.set("endblock", "99999999");
  endpoint.searchParams.set("page", "1");
  endpoint.searchParams.set("offset", String(Math.min(limit, 50)));
  endpoint.searchParams.set("sort", "desc");
  const response = await fetch(endpoint, { cache: "no-store" });
  if (!response.ok) throw new Error(`EVM history HTTP ${response.status}.`);
  const payload = await response.json() as { status?: string; result?: Array<Record<string, string>> | string; message?: string };
  if (Array.isArray(payload.result)) return payload.result.slice(0, limit);
  const detail = `${payload.message || ""} ${String(payload.result || "")}`.trim();
  if (/no transactions found/i.test(detail)) return [];
  throw new Error(detail || "EVM history returned an invalid response.");
}

function mapEtherscanActivity(
  rows: Array<Record<string, string>>,
  action: "txlist" | "txlistinternal" | "tokentx",
  network: EvmNetwork
): ActivityItem[] {
  return rows.flatMap((item) => {
    const raw = BigInt(item.value || 0);
    if (action === "txlistinternal" && raw === 0n) return [];
    const decimals = action === "tokentx" ? Math.max(0, Math.min(36, Number(item.tokenDecimal || 18))) : 18;
    return [{
      hash: item.hash || "",
      from: item.from || "",
      to: item.to || "",
      amount: Number(ethersFormatUnits(raw, decimals)),
      timestamp: normalizeTimestamp(item.timeStamp),
      status: item.isError === "1" || item.txreceipt_status === "0" ? "rejected" : "confirmed",
      opType: action === "tokentx" ? "token transfer" : action === "txlistinternal" ? "internal" : item.input && item.input !== "0x" ? "call" : "standard",
      network: "evm" as const,
      symbol: action === "tokentx" ? item.tokenSymbol || "TOKEN" : network.symbol
    }];
  });
}

async function fetchBlockscoutActivity(network: EvmNetwork, address: string, limit: number): Promise<ActivityItem[]> {
  const host = network.chainId === 1 ? "eth.blockscout.com" : "eth-sepolia.blockscout.com";
  const response = await fetch(`https://${host}/api/v2/addresses/${address}/transactions`, { cache: "no-store" });
  if (!response.ok) throw new Error(`EVM history fallback HTTP ${response.status}.`);
  const payload = await response.json() as { items?: Array<Record<string, unknown>> };
  return (payload.items || []).slice(0, limit).map((item) => ({
    hash: String(item.hash || ""),
    from: String((item.from as Record<string, unknown> | null)?.hash || item.from || ""),
    to: String((item.to as Record<string, unknown> | null)?.hash || item.to || ""),
    amount: Number(ethersFormatUnits(BigInt(String(item.value || 0)), 18)),
    timestamp: Date.parse(String(item.timestamp || "")) || 0,
    status: String(item.status || "").toLowerCase() === "error" ? "rejected" : "confirmed",
    opType: String(item.method || "").trim() ? "call" : "standard",
    network: "evm",
    symbol: network.symbol
  }));
}

function activityKey(item: ActivityItem): string {
  return `${item.hash}:${item.symbol}:${item.from}:${item.to}:${item.amount}`.toLowerCase();
}

function evmRpcCandidates(network: EvmNetwork): string[] {
  const primary = network.rpcUrl.trim();
  if (!/^https:\/\/rpc\.xme\.my\.id\/eth-(mainnet|sepolia)\/?$/i.test(primary)) return [primary];
  const fallback = network.chainId === 1
    ? "https://ethereum-rpc.publicnode.com"
    : network.chainId === 11155111
      ? "https://ethereum-sepolia-rpc.publicnode.com"
      : "";
  return fallback ? [primary, fallback] : [primary];
}

export async function resolveEvmRecipient(provider: AbstractProvider, input: string): Promise<string> {
  const clean = input.trim();
  if (/^0x[0-9a-f]{40}$/i.test(clean)) return clean;
  if (clean.toLowerCase().endsWith(".eth")) {
    const resolved = await provider.resolveName(clean);
    if (resolved) return resolved;
  }
  throw new Error("Enter a valid 0x address or ENS name.");
}

export async function waitForEvmTransaction(network: EvmNetwork, hash: string): Promise<boolean> {
  const receipt = await evmProvider(network).waitForTransaction(hash, 1, 300_000);
  return receipt?.status === 1;
}

function trimAmount(value: string, maxFraction = 6): string {
  const [whole, fraction = ""] = value.split(".");
  const clean = fraction.slice(0, maxFraction).replace(/0+$/, "");
  return clean ? `${whole}.${clean}` : whole;
}

function normalizeTimestamp(value: unknown): number {
  const timestamp = Number(value || 0);
  if (!Number.isFinite(timestamp) || timestamp <= 0) return 0;
  return timestamp < 1_000_000_000_000 ? Math.round(timestamp * 1000) : Math.round(timestamp);
}
