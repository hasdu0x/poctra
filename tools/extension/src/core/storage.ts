import type { VaultData, VaultEnvelope } from "./types";

const VAULT_KEY = "poctraVault";
const SESSION_KEY = "poctraUnlockedSession";

export interface SessionRecord {
  vault: VaultData;
  vaultKey: string;
  expiresAt: number;
  lastActivityAt: number;
}

function hasChromeStorage(): boolean {
  return typeof chrome !== "undefined" && Boolean(chrome.storage?.local);
}

export async function loadVaultEnvelope(): Promise<VaultEnvelope | null> {
  if (hasChromeStorage()) {
    const result = await chrome.storage.local.get(VAULT_KEY);
    return (result[VAULT_KEY] as VaultEnvelope | undefined) || null;
  }
  const raw = localStorage.getItem(VAULT_KEY);
  return raw ? JSON.parse(raw) as VaultEnvelope : null;
}

export async function saveVaultEnvelope(envelope: VaultEnvelope): Promise<void> {
  if (hasChromeStorage()) {
    await chrome.storage.local.set({ [VAULT_KEY]: envelope });
    return;
  }
  localStorage.setItem(VAULT_KEY, JSON.stringify(envelope));
}

export async function saveUnlockedSession(vault: VaultData, vaultKey: string): Promise<void> {
  const now = Date.now();
  const record: SessionRecord = {
    vault,
    vaultKey,
    lastActivityAt: now,
    expiresAt: now + vault.settings.autoLockMinutes * 60_000
  };
  if (hasChromeStorage() && chrome.storage.session) {
    await chrome.storage.session.setAccessLevel({ accessLevel: "TRUSTED_CONTEXTS" });
    await chrome.storage.session.set({ [SESSION_KEY]: record });
    return;
  }
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(record));
}

export async function loadUnlockedSession(): Promise<VaultData | null> {
  return (await loadUnlockedState())?.vault || null;
}

export async function loadUnlockedState(): Promise<SessionRecord | null> {
  let record: SessionRecord | null = null;
  if (hasChromeStorage() && chrome.storage.session) {
    const result = await chrome.storage.session.get(SESSION_KEY);
    record = (result[SESSION_KEY] as SessionRecord | undefined) || null;
  } else {
    const raw = sessionStorage.getItem(SESSION_KEY);
    record = raw ? JSON.parse(raw) as SessionRecord : null;
  }
  if (!record) return null;
  if (record.expiresAt <= Date.now()) {
    await clearUnlockedSession();
    return null;
  }
  return record;
}

export async function touchUnlockedSession(vault: VaultData, vaultKey: string): Promise<void> {
  await saveUnlockedSession(vault, vaultKey);
}

export async function clearUnlockedSession(): Promise<void> {
  if (hasChromeStorage() && chrome.storage.session) {
    await chrome.storage.session.remove(SESSION_KEY);
    return;
  }
  sessionStorage.removeItem(SESSION_KEY);
}

export async function hasStoredVault(): Promise<boolean> {
  return Boolean(await loadVaultEnvelope());
}

export async function clearAllWalletData(): Promise<void> {
  await clearUnlockedSession();
  if (hasChromeStorage()) await chrome.storage.local.remove(VAULT_KEY);
  else localStorage.removeItem(VAULT_KEY);
}
