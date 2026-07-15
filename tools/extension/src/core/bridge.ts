import { Contract, Wallet, concat, hexlify, toBeHex, zeroPadValue } from "ethers";
import { evmProvider } from "./evm-rpc";
import { fetchOctraBalance, octraRpc } from "./octra-rpc";
import { evmPrivateKey } from "./wallet";
import type { EvmNetwork, OctraNetwork, WalletRecord } from "./types";

export const OCTRA_BRIDGE = "oct5MrNfjiXFNRDLwsodn8Zm9hDKNGAYt3eQDCQ52bSpCHq";
export const WOCT_TOKEN = "0x4647e1fE715c9e23959022C2416C71867F5a6E80";
export const EVM_BRIDGE = "0xE7eD69b852fd2a1406080B26A37e8E04e7dA4caE";
export const BRIDGE_RELAYER = "https://relayer-002838819188.octra.network";

const ERC20 = ["function allowance(address owner,address spender) view returns (uint256)", "function approve(address spender,uint256 amount) returns (bool)"];
const BURN_SELECTOR = "0xe3e3aed0";

export interface LockedEvent {
  epoch: number;
  amountRaw: bigint;
  evmRecipient: string;
  sourceNonce: number;
}

export async function readLockedEvent(network: OctraNetwork, txHash: string, fallbackEpoch = 0): Promise<LockedEvent> {
  const receipt = await octraRpc<Record<string, unknown>>(network, "contract_receipt", [txHash]);
  if (receipt.success === false) throw new Error(String(receipt.error || "lock_to_eth failed on-chain."));
  const events = Array.isArray(receipt.events) ? receipt.events as Array<Record<string, unknown>> : [];
  const locked = events.find((event) => event.event === "Locked");
  const values = Array.isArray(locked?.values) ? locked?.values : [];
  if (values.length < 4) throw new Error("Locked event was not found in the contract receipt.");
  return {
    epoch: Number(receipt.epoch ?? fallbackEpoch),
    amountRaw: BigInt(String(values[1] || 0)),
    evmRecipient: String(values[2] || ""),
    sourceNonce: Number(values[3] || 0)
  };
}

export async function waitForBridgeClaim(input: {
  locked: LockedEvent;
  timeoutMs?: number;
  onWaiting?: () => void;
}): Promise<string> {
  const started = Date.now();
  const timeout = input.timeoutMs || 360_000;
  while (Date.now() - started < timeout) {
    input.onWaiting?.();
    const header = await relayer<Record<string, unknown>>("bridgeHeader", [input.locked.epoch]).catch(() => null);
    if (Number(header?.message_count || 0) > 0) {
      const payload = await relayer<unknown>("bridgeMessagesByEpoch", [input.locked.epoch]);
      const messages = extractMessages(payload);
      const candidates = messages.map((message) => {
        const recipient = firstString(message, "recipient", "eth_address", "ethAddress", "to");
        const amount = firstString(message, "amount_raw", "amountRaw", "amount");
        const nonce = firstString(message, "src_nonce", "srcNonce", "source_nonce", "nonce");
        const leaf = Number(firstString(message, "leaf_index", "leafIndex", "index") || -1);
        const matches = recipient.toLowerCase() === input.locked.evmRecipient.toLowerCase() && (!amount || BigInt(amount) === input.locked.amountRaw);
        return { leaf, score: !matches ? 0 : Number(nonce) === input.locked.sourceNonce ? 3 : !nonce ? 2 : 0 };
      }).filter((entry) => entry.leaf >= 0 && entry.score > 0).sort((a, b) => b.score - a.score || a.leaf - b.leaf);
      if (candidates.length) {
        const claim = await relayer<Record<string, unknown>>("bridgeClaimCalldata", [input.locked.epoch, candidates[0].leaf]);
        const calldata = String(claim.calldata || "");
        if (/^0x[0-9a-f]+$/i.test(calldata) && calldata.length > 10) return calldata;
      }
    }
    await delay(5_000);
  }
  throw new Error("Bridge claim is not ready. The OCT lock is safe and can be recovered later.");
}

export async function mintWoct(input: { network: EvmNetwork; wallet: WalletRecord; calldata: string; gasPrice?: bigint }): Promise<string> {
  ensureEthereumMainnet(input.network);
  const signer = new Wallet(evmPrivateKey(input.wallet), evmProvider(input.network));
  await evmProvider(input.network).call({ from: input.wallet.evmAddress, to: EVM_BRIDGE, data: input.calldata });
  const tx = await signer.sendTransaction({ to: EVM_BRIDGE, data: input.calldata, gasPrice: input.gasPrice });
  if ((await tx.wait())?.status !== 1) throw new Error("Mint wOCT transaction reverted.");
  return tx.hash;
}

export async function burnWoct(input: {
  network: EvmNetwork;
  wallet: WalletRecord;
  recipient: string;
  amountRaw: bigint;
  gasPrice?: bigint;
  onStage?: (stage: "approve" | "burn", hash?: string) => void;
}): Promise<{ approvalHash?: string; burnHash: string }> {
  ensureEthereumMainnet(input.network);
  if (!input.recipient.startsWith("oct") || input.recipient.length !== 47) throw new Error("Enter a valid Octra recipient address.");
  const signer = new Wallet(evmPrivateKey(input.wallet), evmProvider(input.network));
  const token = new Contract(WOCT_TOKEN, ERC20, signer);
  const owner = input.wallet.evmAddress;
  const allowance = BigInt(await token.allowance(owner, EVM_BRIDGE));
  let approvalHash: string | undefined;
  if (allowance < input.amountRaw) {
    input.onStage?.("approve");
    const approval = await token.approve(EVM_BRIDGE, input.amountRaw, input.gasPrice ? { gasPrice: input.gasPrice } : {});
    approvalHash = approval.hash;
    input.onStage?.("approve", approval.hash);
    if ((await approval.wait())?.status !== 1) throw new Error("wOCT approval reverted.");
  }
  input.onStage?.("burn");
  const data = encodeBurn(input.recipient, input.amountRaw);
  await evmProvider(input.network).call({ from: owner, to: EVM_BRIDGE, data });
  const burn = await signer.sendTransaction({ to: EVM_BRIDGE, data, gasPrice: input.gasPrice });
  input.onStage?.("burn", burn.hash);
  if ((await burn.wait())?.status !== 1) throw new Error("wOCT burn reverted.");
  return { approvalHash, burnHash: burn.hash };
}

export async function waitForIncomingOct(input: { network: OctraNetwork; address: string; balanceBefore: bigint; timeoutMs?: number }): Promise<void> {
  const started = Date.now();
  while (Date.now() - started < (input.timeoutMs || 360_000)) {
    const balance = await fetchOctraBalance(input.network, input.address).catch(() => null);
    if (balance && balance.raw > input.balanceBefore) return;
    await delay(5_000);
  }
  throw new Error("The burn is confirmed, but incoming OCT is still pending. It can be resumed from bridge history.");
}

function encodeBurn(recipient: string, amount: bigint): string {
  const bytes = new TextEncoder().encode(recipient);
  const padded = new Uint8Array(Math.ceil(bytes.length / 32) * 32);
  padded.set(bytes);
  return hexlify(concat([
    BURN_SELECTOR,
    zeroPadValue(toBeHex(64), 32),
    zeroPadValue(toBeHex(amount), 32),
    zeroPadValue(toBeHex(bytes.length), 32),
    padded
  ]));
}

async function relayer<T>(method: string, params: unknown[]): Promise<T> {
  const controller = new AbortController();
  const timeout = globalThis.setTimeout(() => controller.abort(), 12_000);
  try {
    const response = await fetch(BRIDGE_RELAYER, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ jsonrpc: "2.0", id: Date.now(), method, params }), signal: controller.signal, cache: "no-store" });
    if (!response.ok) throw new Error(`Bridge relayer HTTP ${response.status}.`);
    const payload = await response.json() as { result?: T; error?: { message?: string } };
    if (payload.error) throw new Error(payload.error.message || "Bridge relayer error.");
    return payload.result as T;
  } finally { globalThis.clearTimeout(timeout); }
}

function extractMessages(payload: unknown): Array<Record<string, unknown>> {
  if (Array.isArray(payload)) return payload as Array<Record<string, unknown>>;
  if (!payload || typeof payload !== "object") return [];
  const record = payload as Record<string, unknown>;
  if (Array.isArray(record.messages)) return record.messages as Array<Record<string, unknown>>;
  if (record.result && typeof record.result === "object" && Array.isArray((record.result as Record<string, unknown>).messages)) return (record.result as Record<string, unknown>).messages as Array<Record<string, unknown>>;
  return Array.isArray(record.result) ? record.result as Array<Record<string, unknown>> : [];
}
function firstString(value: Record<string, unknown>, ...keys: string[]): string { for (const key of keys) if (value[key] != null) return String(value[key]); return ""; }
function ensureEthereumMainnet(network: EvmNetwork): void { if (network.chainId !== 1) throw new Error("Bridge requires Ethereum Mainnet."); }
function delay(ms: number): Promise<void> { return new Promise((resolve) => globalThis.setTimeout(resolve, ms)); }
