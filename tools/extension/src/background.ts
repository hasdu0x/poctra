import { approvalKey, type ApprovalRequest } from "./core/approval";
import type { VaultData } from "./core/types";
import { octraRpc } from "./core/octra-rpc";
import { loadVaultEnvelope, saveUnlockedSession, saveVaultEnvelope } from "./core/storage";
import { encryptVaultWithSessionKey } from "./core/vault";
import { isValidOctraAddress } from "./core/wallet";

const SESSION_KEY = "poctraUnlockedSession";
const LOCK_ALARM = "poctra-auto-lock";
const pending = new Map<string, { respond: (response: ProviderResponse) => void; windowId?: number; method: string; origin: string; transportId?: string }>();
type ProviderResponse = { result?: unknown; error?: { code: number; message: string; data?: Record<string, unknown> } };
const OCTRA_READ_METHODS = new Set([
  "node_version", "node_status", "node_stats", "octra_balance", "octra_account", "octra_nonce",
  "octra_publicKey", "octra_validateAddress", "octra_supply", "octra_transaction", "octra_recentTransactions",
  "octra_transactions", "octra_transactionsByAddress", "octra_transactionsByEpoch", "octra_totalTransactions",
  "octra_search", "epoch_current", "epoch_get", "epoch_list", "epoch_summaries", "octra_recommendedFee",
  "staging_view", "staging_stats", "staging_estimateOu", "contract_call", "octra_callProgramView", "contract_receipt"
]);

chrome.runtime.onInstalled.addListener(async (details) => {
  await chrome.storage.session.setAccessLevel({ accessLevel: "TRUSTED_CONTEXTS" });
  await chrome.alarms.create(LOCK_ALARM, { periodInMinutes: 1 });

  if (details.reason === "install" && !(await loadVaultEnvelope())) {
    await chrome.tabs.create({
      url: chrome.runtime.getURL("app.html"),
      active: true
    });
  }
});

chrome.runtime.onStartup.addListener(async () => {
  await chrome.storage.session.setAccessLevel({ accessLevel: "TRUSTED_CONTEXTS" });
  await chrome.alarms.create(LOCK_ALARM, { periodInMinutes: 1 });
});

chrome.alarms.onAlarm.addListener(async (alarm) => {
  if (alarm.name !== LOCK_ALARM) return;
  const session = await unlockedSession();
  if (session?.expiresAt && session.expiresAt <= Date.now()) await chrome.storage.session.remove(SESSION_KEY);
});

chrome.windows.onRemoved.addListener((windowId) => {
  for (const [id, request] of pending) {
    if (request.windowId !== windowId) continue;
    request.respond(providerFailure(4001, "User rejected the request.", "user_rejected"));
    pending.delete(id);
    void chrome.storage.session.remove(approvalKey(id));
  }
});

chrome.runtime.onConnect.addListener((port) => {
  if (port.name !== "poctra-provider") return;
  const transportId = crypto.randomUUID();
  port.onMessage.addListener((message) => {
    if (message?.type !== "poctra-provider-request" || !message.id) return;
    const origin = senderOrigin(port.sender, message.origin);
    const respond = (response: ProviderResponse) => {
      try { port.postMessage({ type: "poctra-provider-response", id: message.id, ...response }); } catch { /* The requesting page closed. */ }
    };
    void handleProviderRequest({ ...message, origin }, respond, transportId).catch((error) => {
      respond(providerFailure(-32603, error instanceof Error ? error.message : "Wallet request failed.", "internal_error"));
    });
  });
  port.onDisconnect.addListener(() => {
    for (const [id, request] of pending) {
      if (request.transportId !== transportId) continue;
      pending.delete(id);
      void chrome.storage.session.remove(approvalKey(id));
      if (request.windowId != null) void chrome.windows.remove(request.windowId).catch(() => undefined);
    }
  });
});

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message?.type === "poctra-open-expanded") {
    void chrome.tabs.create({ url: chrome.runtime.getURL("app.html") });
    sendResponse({ ok: true });
    return false;
  }
  if (message?.type === "poctra-lock") {
    void chrome.storage.session.remove(SESSION_KEY).then(() => sendResponse({ ok: true }));
    return true;
  }
  if (message?.type === "poctra-wallet-state-changed") {
    void broadcastWalletState().then(() => sendResponse({ ok: true }));
    return true;
  }
  if (message?.type === "poctra-provider-request") {
    void handleProviderRequest({ ...message, origin: senderOrigin(sender, message.origin) }, sendResponse).catch((error) => sendResponse(providerFailure(-32603, error instanceof Error ? error.message : "Wallet request failed.", "internal_error")));
    return true;
  }
  if (message?.type === "poctra-approval-result") {
    finishApproval(message.id, message.approved ? { result: message.result } : providerFailure(4001, message.error || "User rejected the request.", "user_rejected"));
    sendResponse({ ok: true });
    return false;
  }
  return false;
});

async function handleProviderRequest(message: { method?: string; params?: unknown[]; origin?: string }, sendResponse: (response: ProviderResponse) => void, transportId?: string) {
  const session = await unlockedSession();
  if (!session?.vault || !session.expiresAt || session.expiresAt <= Date.now()) {
    sendResponse(providerFailure(4100, "Unlock poctra before using this site.", "wallet_locked"));
    return;
  }
  const method = String(message.method || "");
  const origin = String(message.origin || "unknown");
  const connectedSite = session.vault.settings?.connectedSites?.find((site) => site.origin === origin);
  const connected = Boolean(connectedSite);
  const octraNetwork = session.vault.settings.octraNetworks.find((network) => network.id === session.vault!.settings.activeOctraNetworkId);
  const evmNetwork = session.vault.settings.evmNetworks.find((network) => network.id === session.vault!.settings.activeEvmNetworkId);
  const activeWallet = session.vault.wallets.find((wallet) => wallet.address === session.vault!.activeAddress);
  if (!octraNetwork || !evmNetwork || !activeWallet) {
    sendResponse(providerFailure(4900, "The active wallet or network is unavailable.", "wallet_disconnected"));
    return;
  }
  if (method === "octra_accounts") {
    sendResponse({ result: connected && session.vault.activeAddress ? [session.vault.activeAddress] : [] });
    return;
  }
  if (method === "octra_requestAccounts" && connectedSite) {
    const requested = requestedProviderPermissions(message.params);
    if (requested.every((permission) => connectedSite.permissions.includes(permission))) {
      await updateSessionVault(session, (vault) => ({ ...vault, settings: { ...vault.settings, connectedSites: vault.settings.connectedSites.map((site) => site.origin === origin ? { ...site, lastUsedAt: Date.now() } : site) } }));
      sendResponse({ result: [activeWallet.address] });
      return;
    }
  }
  if (method === "octra_disconnect") {
    await updateSessionVault(session, (vault) => ({ ...vault, settings: { ...vault.settings, connectedSites: vault.settings.connectedSites.filter((site) => site.origin !== origin) } }));
    await broadcastProviderEvent(origin, "accountsChanged", []);
    await broadcastProviderEvent(origin, "permissionsChanged", []);
    await broadcastProviderEvent(origin, "disconnect", { code: 4900, message: "Disconnected" });
    sendResponse({ result: { disconnected: true } });
    return;
  }
  if (method === "octra_networkId") {
    sendResponse({ result: `octra:${octraNetwork.networkType}` });
    return;
  }
  if (method === "octra_networkInfo") {
    sendResponse({ result: { id: `octra:${octraNetwork.networkType}`, name: octraNetwork.name, rpcUrl: octraNetwork.rpcUrl, explorerUrl: octraNetwork.explorerUrl, supportsPrivacy: false, isTestnet: octraNetwork.networkType !== "mainnet" } });
    return;
  }
  if (method === "octra_permissions") {
    const site = session.vault.settings.connectedSites.find((item) => item.origin === origin);
    sendResponse({ result: site?.permissions || [] });
    return;
  }
  if (method === "evm_getDerivedAddress") {
    sendResponse(connected ? { result: activeWallet.evmAddress } : providerFailure(4100, "Connect this site before reading the EVM account.", "unauthorized"));
    return;
  }
  if (method === "evm_getChainId") { sendResponse({ result: evmNetwork.chainId }); return; }
  if (method === "evm_getNetworkInfo") { sendResponse({ result: evmNetwork }); return; }
  if (method === "evm_getBalance") {
    const input = firstRecord(message.params);
    const address = String(input.address || (connected ? activeWallet.evmAddress : ""));
    if (!connected && !input.address) { sendResponse(providerFailure(4100, "Connect this site before reading the EVM account.", "unauthorized")); return; }
    if (!/^0x[0-9a-f]{40}$/i.test(address)) { sendResponse(providerFailure(-32602, "A valid EVM address is required.", "invalid_address")); return; }
    const balanceHex = String(await evmRpc(evmNetwork.rpcUrl, "eth_getBalance", [address, "latest"]));
    const balanceWei = BigInt(balanceHex).toString();
    sendResponse({ result: { address, balance: formatDecimalUnits(BigInt(balanceWei), 18), balanceWei, chainId: evmNetwork.chainId } });
    return;
  }
  if (["evm_call", "evm_estimateGas", "evm_getGasPrice"].includes(method)) {
    const mapped = method === "evm_call" ? "eth_call" : method === "evm_estimateGas" ? "eth_estimateGas" : "eth_gasPrice";
    const input = firstRecord(message.params);
    const rpcInput = normalizeEvmCall(input, activeWallet.evmAddress);
    const raw = String(await evmRpc(evmNetwork.rpcUrl, mapped, method === "evm_getGasPrice" ? [] : method === "evm_call" ? [rpcInput, "latest"] : [rpcInput]));
    if (method === "evm_estimateGas") sendResponse({ result: { gas: BigInt(raw).toString(), gasHex: raw } });
    else if (method === "evm_getGasPrice") sendResponse({ result: { gasPriceWei: BigInt(raw).toString(), gasPriceGwei: formatDecimalUnits(BigInt(raw), 9) } });
    else sendResponse({ result: raw });
    return;
  }
  if (["evm_getTokenInfo", "evm_getTokenBalance", "evm_getAllowance"].includes(method)) {
    const input = firstRecord(message.params);
    const token = String(input.token || "");
    if (!/^0x[0-9a-f]{40}$/i.test(token)) { sendResponse(providerFailure(-32602, "A valid token address is required.", "invalid_address")); return; }
    const evm = await import("./core/evm-rpc");
    if (method === "evm_getTokenInfo") {
      const info = await evm.getEvmTokenInfo(evmNetwork, token);
      sendResponse({ result: { token, name: info.name, symbol: info.symbol, decimals: info.decimals } });
    } else if (method === "evm_getTokenBalance") {
      const owner = String(input.owner || (connected ? activeWallet.evmAddress : ""));
      if (!isEvmAddress(owner)) { sendResponse(providerFailure(-32602, "A valid token owner address is required.", "invalid_address")); return; }
      const balance = await evm.getEvmTokenBalance(evmNetwork, token, owner);
      sendResponse({ result: { token, owner, balance: balance.toString() } });
    } else {
      const owner = String(input.owner || "");
      const spender = String(input.spender || "");
      if (!isEvmAddress(owner) || !isEvmAddress(spender)) { sendResponse(providerFailure(-32602, "Valid owner and spender addresses are required.", "invalid_address")); return; }
      const allowance = await evm.getEvmTokenAllowance(evmNetwork, token, owner, spender);
      sendResponse({ result: { allowance: allowance.toString() } });
    }
    return;
  }
  if (method === "octra_callContract") {
    const input = firstRecord(message.params);
    const result = await octraRpc(octraNetwork, "contract_call", [String(input.address || ""), String(input.method || ""), Array.isArray(input.params) ? input.params : [], String(input.caller || activeWallet.address)]);
    sendResponse({ result }); return;
  }
  if (method === "octra_getContractReceipt") {
    const input = firstRecord(message.params);
    sendResponse({ result: await octraRpc(octraNetwork, "contract_receipt", [String(input.hash || "")]) }); return;
  }
  if (OCTRA_READ_METHODS.has(method)) {
    sendResponse({ result: await octraRpc(octraNetwork, method, message.params || []) });
    return;
  }
  const privacyMethods = ["octra_getEncryptedBalance", "octra_encryptBalance", "octra_decryptBalance", "octra_sendPrivateTransfer", "octra_scanStealth", "octra_claimStealth"];
  if (privacyMethods.includes(method)) {
    sendResponse(providerFailure(4200, `${method} is not available in this Poctra extension build.`, "privacy_not_supported"));
    return;
  }
  const supported = [
    "octra_requestAccounts", "octra_switchNetwork", "octra_signMessage", "octra_sendTransaction", "octra_signTransaction",
    "octra_submitTransaction", "octra_sendContractTransaction", "evm_switchChain", "evm_sendTransaction", "evm_signMessage",
    "evm_signTypedData", "evm_transferToken", "evm_approveToken"
  ].includes(method);
  if (!supported) {
    sendResponse(providerFailure(4200, `Unsupported provider method: ${method}`, "unsupported_method"));
    return;
  }
  if (method !== "octra_requestAccounts" && !connected) {
    sendResponse(providerFailure(4100, "Connect this site before requesting a signature or transaction.", "unauthorized"));
    return;
  }
  if (method !== "octra_requestAccounts") {
    const site = session.vault.settings.connectedSites.find((item) => item.origin === origin);
    const required = requiredPermission(method);
    if (required && !site?.permissions.includes(required)) {
      sendResponse(providerFailure(4100, `The ${required} permission is required.`, "unauthorized", { permission: required }));
      return;
    }
  }
  const invalid = validateApprovalRequest(method, message.params, activeWallet.address, activeWallet.evmAddress);
  if (invalid) {
    sendResponse(providerFailure(invalid.code, invalid.message, invalid.reason));
    return;
  }
  const id = crypto.randomUUID();
  const request: ApprovalRequest = { id, origin, method, params: Array.isArray(message.params) ? message.params : [], createdAt: Date.now() };
  await chrome.storage.session.set({ [approvalKey(id)]: request });
  const window = await chrome.windows.create({ url: chrome.runtime.getURL(`approval.html?request=${encodeURIComponent(id)}`), type: "popup", width: 400, height: 650, focused: true });
  pending.set(id, { respond: sendResponse, windowId: window.id, method, origin, transportId });
}

function finishApproval(id: string, response: ProviderResponse): void {
  const request = pending.get(id);
  if (!request) return;
  pending.delete(id);
  request.respond(response);
  if (response && typeof response === "object" && "result" in response) void broadcastApprovalEvent(request.method, request.origin, (response as { result?: unknown }).result);
  void chrome.storage.session.remove(approvalKey(id));
  if (request.windowId != null) void chrome.windows.remove(request.windowId).catch(() => undefined);
}

async function broadcastApprovalEvent(method: string, origin: string, result: unknown): Promise<void> {
  if (method === "octra_requestAccounts") {
    await broadcastProviderEvent(origin, "accountsChanged", result);
    const session = await unlockedSession();
    const permissions = session?.vault?.settings.connectedSites.find((site) => site.origin === origin)?.permissions || [];
    await broadcastProviderEvent(origin, "permissionsChanged", permissions);
    const network = session?.vault?.settings.octraNetworks.find((item) => item.id === session.vault?.settings.activeOctraNetworkId);
    const networkInfo = network ? octraNetworkPayload(network) : undefined;
    if (networkInfo) await broadcastProviderEvent(origin, "connect", { networkId: networkInfo.id, networkInfo });
    return;
  }
  if (method === "octra_switchNetwork") await broadcastProviderEvent(origin, "networkChanged", result);
  if (method === "evm_switchChain") await broadcastProviderEvent(origin, "networkChanged", result);
}

async function broadcastWalletState(): Promise<void> {
  const session = await unlockedSession();
  if (!session?.vault) return;
  const wallet = session.vault.wallets.find((item) => item.address === session.vault!.activeAddress);
  const network = session.vault.settings.octraNetworks.find((item) => item.id === session.vault!.settings.activeOctraNetworkId);
  const origins = session.vault.settings.connectedSites.map((site) => site.origin);
  await Promise.all(origins.flatMap((origin) => [
    broadcastProviderEvent(origin, "accountsChanged", wallet ? [wallet.address] : []),
    network ? broadcastProviderEvent(origin, "networkChanged", { id: `octra:${network.networkType}`, name: network.name, rpcUrl: network.rpcUrl, explorerUrl: network.explorerUrl, supportsPrivacy: false, isTestnet: network.networkType !== "mainnet" }) : Promise.resolve()
  ]));
}

async function broadcastProviderEvent(origin: string, event: string, payload: unknown): Promise<void> {
  const tabs = await chrome.tabs.query({});
  await Promise.all(tabs.flatMap((tab) => tab.id == null ? [] : [chrome.tabs.sendMessage(tab.id, { type: "poctra-provider-event", origin, event, payload }).catch(() => undefined)]));
}

async function unlockedSession(): Promise<{ vault?: VaultData; vaultKey?: string; expiresAt?: number } | undefined> {
  const result = await chrome.storage.session.get(SESSION_KEY);
  return result[SESSION_KEY];
}

async function updateSessionVault(
  session: { vault?: VaultData; vaultKey?: string },
  mutator: (vault: VaultData) => VaultData
): Promise<void> {
  if (!session.vault || !session.vaultKey) throw new Error("The unlocked wallet session is invalid.");
  const envelope = await loadVaultEnvelope();
  if (!envelope) throw new Error("Wallet vault was not found.");
  const next = mutator(structuredClone(session.vault));
  const encrypted = await encryptVaultWithSessionKey(next, envelope, session.vaultKey);
  await Promise.all([saveVaultEnvelope(encrypted), saveUnlockedSession(next, session.vaultKey)]);
}

async function evmRpc(rpcUrl: string, method: string, params: unknown[]): Promise<unknown> {
  const controller = new AbortController();
  const timeout = globalThis.setTimeout(() => controller.abort(), 15_000);
  try {
    const response = await fetch(rpcUrl, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ jsonrpc: "2.0", id: Date.now(), method, params }), signal: controller.signal, cache: "no-store" });
    if (!response.ok) throw new Error(`EVM RPC HTTP ${response.status}.`);
    const payload = await response.json() as { result?: unknown; error?: { message?: string } };
    if (payload.error) throw new Error(payload.error.message || "EVM RPC returned an error.");
    return payload.result;
  } finally { globalThis.clearTimeout(timeout); }
}

function firstRecord(params: unknown[] | undefined): Record<string, unknown> {
  const value = params?.[0];
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function requiredPermission(method: string): string | null {
  if (method === "octra_signMessage" || method === "evm_signMessage" || method === "evm_signTypedData") return "sign_messages";
  if (method === "octra_sendContractTransaction") return "contract_calls";
  if (method.includes("Transaction") || method === "evm_transferToken" || method === "evm_approveToken") return "send_transactions";
  return null;
}

function normalizeEvmCall(input: Record<string, unknown>, fallbackFrom: string): Record<string, string> {
  const output: Record<string, string> = { to: String(input.to || ""), data: String(input.data || "0x") };
  if (input.from || fallbackFrom) output.from = String(input.from || fallbackFrom);
  if (input.value != null && String(input.value) !== "") output.value = decimalEthToHex(String(input.value));
  return output;
}

function decimalEthToHex(value: string): string {
  if (/^0x[0-9a-f]+$/i.test(value)) return value;
  const [whole = "0", fraction = ""] = value.split(".");
  const wei = BigInt(whole || 0) * 10n ** 18n + BigInt((fraction + "0".repeat(18)).slice(0, 18));
  return `0x${wei.toString(16)}`;
}

function formatDecimalUnits(value: bigint, decimals: number): string {
  const divisor = 10n ** BigInt(decimals);
  const whole = value / divisor;
  const fraction = (value % divisor).toString().padStart(decimals, "0").replace(/0+$/, "");
  return fraction ? `${whole}.${fraction}` : whole.toString();
}

function providerFailure(code: number, message: string, reason: string, extra: Record<string, unknown> = {}): ProviderResponse {
  return { error: { code, message, data: { reason, ...extra } } };
}

function senderOrigin(sender: chrome.runtime.MessageSender | undefined, claimedOrigin: unknown): string {
  const source = sender?.url || sender?.tab?.url || "";
  try {
    const origin = new URL(source).origin;
    if (origin && origin !== "null") return origin;
  } catch { /* Fall back to the content script's claimed origin. */ }
  return String(claimedOrigin || "unknown");
}

function octraNetworkPayload(network: { name: string; networkType: string; rpcUrl: string; explorerUrl: string }) {
  return { id: `octra:${network.networkType}`, name: network.name, rpcUrl: network.rpcUrl, explorerUrl: network.explorerUrl, supportsPrivacy: false, isTestnet: network.networkType !== "mainnet" };
}

function validateApprovalRequest(method: string, params: unknown[] | undefined, octraAddress: string, evmAddress: string): { code: number; message: string; reason: string } | null {
  const input = firstRecord(params);
  const fail = (message: string, reason: string, code = -32602) => ({ code, message, reason });
  if (method === "octra_requestAccounts") {
    const supported = new Set(["read_address", "read_balance", "read_public_key", "sign_messages", "send_transactions", "contract_calls"]);
    const requested = Array.isArray(input.permissions) ? input.permissions.map(String) : [];
    const unsupported = requested.find((permission) => !supported.has(permission));
    return unsupported ? fail(`Permission ${unsupported} is not available in this Poctra extension build.`, unsupported.includes("private") || unsupported.includes("encrypted") || unsupported.includes("stealth") ? "privacy_not_supported" : "unsupported_permission", 4200) : null;
  }
  if (method === "octra_switchNetwork") return String(input.networkId || "").trim() ? null : fail("networkId is required.", "network_unavailable");
  if (method === "evm_switchChain") return Number.isSafeInteger(Number(input.chainId)) && Number(input.chainId) > 0 ? null : fail("A valid chainId is required.", "network_unavailable");
  if (method === "octra_signMessage") {
    if (typeof input.message !== "string" || !input.message.length) return fail("A non-empty message is required.", "invalid_message");
    if (input.address && String(input.address) !== octraAddress) return fail("The requested Octra account is not active.", "unauthorized", 4100);
    return null;
  }
  if (method === "evm_signMessage") return typeof input.message === "string" && input.message.length ? null : fail("A non-empty message is required.", "invalid_message");
  if (method === "evm_signTypedData") return input.domain && input.types && input.value ? null : fail("domain, types, and value are required for EIP-712.", "invalid_typed_data");
  if (method === "octra_submitTransaction") {
    const tx = input.tx && typeof input.tx === "object" ? input.tx as Record<string, unknown> : null;
    if (!tx) return fail("A signed transaction is required.", "malformed_transaction");
    if (String(tx.from || "") !== octraAddress) return fail("The signed transaction does not belong to the active account.", "unauthorized", 4100);
    if (!isValidOctraAddress(String(tx.to_ || ""))) return fail("The signed transaction recipient is invalid.", "invalid_address");
    if (!isUnsignedInteger(tx.amount) || !isUnsignedInteger(tx.ou) || !Number.isSafeInteger(Number(tx.nonce))) return fail("The signed transaction contains invalid amount, fee, or nonce values.", "malformed_transaction");
    if (!isBase64Bytes(tx.signature, 64) || !isBase64Bytes(tx.public_key, 32)) return fail("The signed transaction signature or public key is malformed.", "invalid_signature");
    return null;
  }
  if (["octra_sendTransaction", "octra_signTransaction", "octra_sendContractTransaction"].includes(method)) {
    const recipient = String(input.to || input.address || "");
    if (!isValidOctraAddress(recipient)) return fail("A valid Octra recipient or contract address is required.", "invalid_address");
    if (method !== "octra_sendContractTransaction" && !isPositiveDecimal(input.amount)) return fail("Amount must be greater than zero.", "invalid_amount");
    if (method === "octra_signTransaction" && !isPositiveDecimal(input.fee)) return fail("A positive fee is required when signing a transaction.", "fee_too_low");
    if (method === "octra_sendContractTransaction" && !String(input.method || "").trim()) return fail("A contract method is required.", "invalid_method");
    return null;
  }
  if (method === "evm_sendTransaction") {
    if (!isEvmAddress(input.to)) return fail("A valid EVM recipient is required.", "invalid_address");
    if (input.from && String(input.from).toLowerCase() !== evmAddress.toLowerCase()) return fail("The requested EVM account is not active.", "unauthorized", 4100);
    return null;
  }
  if (method === "evm_transferToken") {
    if (!isEvmAddress(input.token) || !isEvmAddress(input.to)) return fail("A valid token and recipient address are required.", "invalid_address");
    return isUnsignedInteger(input.amount) ? null : fail("Token amount must use a non-negative smallest-unit integer.", "invalid_amount");
  }
  if (method === "evm_approveToken") {
    if (!isEvmAddress(input.token) || !isEvmAddress(input.spender)) return fail("A valid token and spender address are required.", "invalid_address");
    return input.amount == null || isUnsignedInteger(input.amount) ? null : fail("Approval amount must use a non-negative smallest-unit integer.", "invalid_amount");
  }
  return null;
}

function isEvmAddress(value: unknown): boolean { return /^0x[0-9a-f]{40}$/i.test(String(value || "")); }
function isUnsignedInteger(value: unknown): boolean { return /^(0|[1-9]\d*)$/.test(String(value ?? "")); }
function isPositiveDecimal(value: unknown): boolean {
  const text = String(value ?? "");
  if (!/^(?:0|[1-9]\d*)(?:\.\d+)?$/.test(text)) return false;
  try { return Number(text) > 0; } catch { return false; }
}
function isBase64Bytes(value: unknown, byteLength: number): boolean {
  const text = String(value || "");
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(text)) return false;
  try {
    const decoded = atob(text);
    return decoded.length === byteLength;
  } catch { return false; }
}

function requestedProviderPermissions(params: unknown[] | undefined): string[] {
  const input = firstRecord(params);
  return Array.isArray(input.permissions) ? input.permissions.map(String) : ["read_address", "read_balance", "send_transactions"];
}
