import { ed25519 } from "@noble/curves/ed25519";
import { secp256k1 } from "@noble/curves/secp256k1";
import { hmac } from "@noble/hashes/hmac";
import { sha256 } from "@noble/hashes/sha256";
import { sha512 } from "@noble/hashes/sha512";
import { generateMnemonic, mnemonicToSeedSync, validateMnemonic } from "@scure/bip39";
import { wordlist } from "@scure/bip39/wordlists/english";
import { computeAddress } from "ethers";
import type { WalletRecord, WalletType } from "./types";
import { base58Encode, base64ToBytes, bytesToBase64, bytesToHex, hexToBytes, utf8 } from "./encoding";

const OCTRA_ADDRESS_LENGTH = 47;
const SECP256K1_ORDER = BigInt("0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141");

export async function generateWalletRecord(index = 0): Promise<WalletRecord> {
  for (let attempt = 0; attempt < 100; attempt += 1) {
    const mnemonic = generateMnemonic(wordlist, 128);
    const wallet = walletFromMnemonic(mnemonic, "generated", `Wallet ${index + 1}`);
    if (isValidOctraAddress(wallet.address)) return wallet;
  }
  throw new Error("Unable to generate a valid Octra address after 100 attempts.");
}

export function importMnemonic(value: string, name = "Imported wallet"): WalletRecord {
  const mnemonic = value.trim().toLowerCase().replace(/\s+/g, " ");
  const count = mnemonic.split(" ").length;
  if (count !== 12 && count !== 24) throw new Error("Seed phrase must contain 12 or 24 words.");
  if (!validateMnemonic(mnemonic, wordlist)) throw new Error("Seed phrase checksum is invalid.");
  return requireValid(walletFromMnemonic(mnemonic, "imported-mnemonic", name), "seed phrase");
}

export function importBase64Key(value: string, name = "Imported wallet"): WalletRecord {
  const seed = base64ToBytes(value);
  if (seed.length !== 32) throw new Error("Base64 private key must decode to exactly 32 bytes.");
  return requireValid(walletFromSeed(seed, "imported-private-key", name), "Base64 private key");
}

export function importEvmHexKey(value: string, name = "Imported wallet"): WalletRecord {
  const clean = value.trim().replace(/^0x/i, "");
  if (!/^[0-9a-f]{64}$/i.test(clean)) throw new Error("Ethereum private key must be 64 hexadecimal characters.");
  const scalar = BigInt(`0x${clean}`);
  if (scalar <= 0n || scalar >= SECP256K1_ORDER) throw new Error("Ethereum private key is outside the secp256k1 range.");
  return requireValid(walletFromSeed(hexToBytes(clean), "imported-evm-private-key", name), "Ethereum private key");
}

export function walletFromMnemonic(mnemonic: string, type: WalletType, name: string): WalletRecord {
  const bip39Seed = mnemonicToSeedSync(mnemonic);
  const seed = hmac(sha512, utf8("Octra seed"), bip39Seed).slice(0, 32);
  bip39Seed.fill(0);
  return walletFromSeed(seed, type, name, mnemonic);
}

export function walletFromSeed(seed: Uint8Array, type: WalletType, name: string, mnemonic?: string): WalletRecord {
  if (seed.length !== 32) throw new Error("Wallet seed must be exactly 32 bytes.");
  const publicKey = ed25519.getPublicKey(seed);
  const address = `oct${base58Encode(sha256(publicKey))}`;
  const privateKeyHex = `0x${bytesToHex(seed)}`;
  const evmPoint = secp256k1.getPublicKey(seed, false);
  if (!evmPoint.length) throw new Error("Unable to derive EVM public key.");
  return {
    address,
    privateKeyBase64: bytesToBase64(seed),
    publicKeyHex: bytesToHex(publicKey),
    mnemonic,
    evmAddress: computeAddress(privateKeyHex),
    name: name.trim().slice(0, 32) || "Wallet",
    type,
    createdAt: Date.now()
  };
}

export function signOctraBytes(wallet: WalletRecord, message: Uint8Array): Uint8Array {
  const seed = base64ToBytes(wallet.privateKeyBase64);
  verifyWalletKey(wallet, seed);
  const signature = ed25519.sign(message, seed);
  seed.fill(0);
  return signature;
}

export function evmPrivateKey(wallet: WalletRecord): string {
  const seed = base64ToBytes(wallet.privateKeyBase64);
  verifyWalletKey(wallet, seed);
  const key = `0x${bytesToHex(seed)}`;
  seed.fill(0);
  return key;
}

export function verifyWalletRecord(wallet: WalletRecord): boolean {
  try {
    verifyWalletKey(wallet, base64ToBytes(wallet.privateKeyBase64));
    return isValidOctraAddress(wallet.address);
  } catch {
    return false;
  }
}

export function isValidOctraAddress(value: string): boolean {
  return value.startsWith("oct") && value.length === OCTRA_ADDRESS_LENGTH;
}

function verifyWalletKey(wallet: WalletRecord, seed: Uint8Array): void {
  const publicKey = ed25519.getPublicKey(seed);
  const address = `oct${base58Encode(sha256(publicKey))}`;
  if (address !== wallet.address) throw new Error("Wallet signing key does not match its Octra address.");
}

function requireValid(wallet: WalletRecord, source: string): WalletRecord {
  if (!isValidOctraAddress(wallet.address)) {
    throw new Error(`This ${source} is valid, but it derives ${wallet.address}, which is not a valid Octra address for poctra.`);
  }
  return wallet;
}
