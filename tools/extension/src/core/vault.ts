import type { VaultData, VaultEnvelope, WalletRecord } from "./types";
import { base64ToBytes, bytesToBase64, decodeUtf8, utf8 } from "./encoding";
import { defaultSettings } from "./defaults";
import { verifyWalletRecord } from "./wallet";

const KDF_ITERATIONS = 600_000;
const AAD = utf8("poctra-extension:v1");

export function createVault(wallet: WalletRecord): VaultData {
  return {
    version: 1,
    wallets: [{ ...wallet, name: wallet.name || "Wallet 1" }],
    activeAddress: wallet.address,
    settings: defaultSettings()
  };
}

export function hydrateVault(vault: VaultData): VaultData {
  const defaults = defaultSettings();
  const settings = vault.settings || defaults;
  return {
    ...vault,
    wallets: Array.isArray(vault.wallets) ? vault.wallets : [],
    settings: {
      ...defaults,
      ...settings,
      activeIdentityProviderByNetwork: {
        ...defaults.activeIdentityProviderByNetwork,
        ...(settings.activeIdentityProviderByNetwork || {})
      },
      octraNetworks: Array.isArray(settings.octraNetworks) ? settings.octraNetworks : defaults.octraNetworks,
      evmNetworks: Array.isArray(settings.evmNetworks) ? settings.evmNetworks : defaults.evmNetworks,
      identityProviders: Array.isArray(settings.identityProviders) ? settings.identityProviders : defaults.identityProviders,
      addressBook: Array.isArray(settings.addressBook) ? settings.addressBook : [],
      octraAssets: Array.isArray(settings.octraAssets) ? settings.octraAssets : [],
      evmAssets: Array.isArray(settings.evmAssets) ? settings.evmAssets : defaults.evmAssets,
      evmNfts: Array.isArray(settings.evmNfts) ? settings.evmNfts : [],
      connectedSites: Array.isArray(settings.connectedSites) ? settings.connectedSites.map((site) => ({
        ...site,
        permissions: site.permissions?.some((permission) => permission !== "accounts")
          ? site.permissions.filter((permission) => permission !== "accounts")
          : ["read_address", "read_balance", "send_transactions"]
      })) : []
    }
  };
}

export async function encryptVault(vault: VaultData, password: string, previous?: VaultEnvelope): Promise<VaultEnvelope> {
  validatePassword(password);
  validateVault(vault);
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const keyBytes = await deriveVaultKeyBytes(password, salt, KDF_ITERATIONS);
  const key = await importVaultKey(keyBytes);
  const plain = utf8(JSON.stringify(vault));
  try {
    const ciphertext = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv, additionalData: AAD, tagLength: 128 }, key, plain));
    return {
      version: 1,
      kdf: "PBKDF2-SHA256",
      iterations: KDF_ITERATIONS,
      salt: bytesToBase64(salt),
      iv: bytesToBase64(iv),
      ciphertext: bytesToBase64(ciphertext),
      createdAt: previous?.createdAt || Date.now(),
      updatedAt: Date.now()
    };
  } finally {
    plain.fill(0);
    keyBytes.fill(0);
  }
}

export async function decryptVault(envelope: VaultEnvelope, password: string): Promise<VaultData> {
  return (await unlockVault(envelope, password)).vault;
}

export async function unlockVault(envelope: VaultEnvelope, password: string): Promise<{ vault: VaultData; sessionKey: string }> {
  if (envelope.version !== 1 || envelope.kdf !== "PBKDF2-SHA256") throw new Error("Unsupported wallet vault format.");
  const salt = base64ToBytes(envelope.salt);
  const iv = base64ToBytes(envelope.iv);
  const cipher = base64ToBytes(envelope.ciphertext);
  const keyBytes = await deriveVaultKeyBytes(password, salt, envelope.iterations);
  const key = await importVaultKey(keyBytes);
  try {
    const plain = new Uint8Array(await crypto.subtle.decrypt({ name: "AES-GCM", iv, additionalData: AAD, tagLength: 128 }, key, cipher));
    try {
      const vault = hydrateVault(JSON.parse(decodeUtf8(plain)) as VaultData);
      validateVault(vault);
      return { vault, sessionKey: bytesToBase64(keyBytes) };
    } finally {
      plain.fill(0);
    }
  } catch (error) {
    if (error instanceof DOMException) throw new Error("Incorrect password or damaged wallet vault.");
    throw error;
  } finally {
    keyBytes.fill(0);
  }
}

export async function encryptVaultWithSessionKey(
  vault: VaultData,
  envelope: VaultEnvelope,
  sessionKey: string
): Promise<VaultEnvelope> {
  validateVault(vault);
  const keyBytes = base64ToBytes(sessionKey);
  if (keyBytes.length !== 32) throw new Error("Unlocked wallet session is invalid.");
  const key = await importVaultKey(keyBytes);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const plain = utf8(JSON.stringify(vault));
  try {
    const ciphertext = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv, additionalData: AAD, tagLength: 128 }, key, plain));
    return {
      ...envelope,
      iv: bytesToBase64(iv),
      ciphertext: bytesToBase64(ciphertext),
      updatedAt: Date.now()
    };
  } finally {
    plain.fill(0);
    keyBytes.fill(0);
  }
}

export function validatePassword(password: string): void {
  if (password.length < 10) throw new Error("Password must contain at least 10 characters.");
  if (password.length > 128) throw new Error("Password is too long.");
}

export function validateVault(vault: VaultData): void {
  if (vault.version !== 1 || !Array.isArray(vault.wallets) || !vault.wallets.length) throw new Error("Wallet vault is invalid.");
  if (!vault.wallets.every(verifyWalletRecord)) throw new Error("A wallet key does not match its stored address.");
  if (!vault.wallets.some((wallet) => wallet.address === vault.activeAddress)) throw new Error("Active wallet is missing from the vault.");
}

async function deriveVaultKeyBytes(password: string, salt: Uint8Array, iterations: number): Promise<Uint8Array> {
  const material = await crypto.subtle.importKey("raw", utf8(password.normalize("NFKC")), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", hash: "SHA-256", salt, iterations },
    material,
    256
  );
  return new Uint8Array(bits);
}

async function importVaultKey(value: Uint8Array): Promise<CryptoKey> {
  return crypto.subtle.importKey("raw", value, { name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]);
}
