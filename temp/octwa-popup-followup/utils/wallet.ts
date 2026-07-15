import { Wallet } from '../types/wallet';
import {
  generateMnemonic,
  validateMnemonic,
  generateWalletFromMnemonic,
  bufferToBase64,
  bufferToHex,
  createOctraAddress,
} from './crypto';
import * as nacl from 'tweetnacl';

const EXPECTED_ADDRESS_LENGTH = 47;
const MAX_GENERATION_ATTEMPTS = 100;
const SECP256K1_ORDER = BigInt('0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141');

function requireValidOctraWallet(wallet: Wallet, source: string): Wallet {
  if (!wallet.address.startsWith('oct') || wallet.address.length !== EXPECTED_ADDRESS_LENGTH) {
    throw new Error(
      `This ${source} is valid, but it derives ${wallet.address}, which is not a valid Octra address. Use another key or create a new wallet.`,
    );
  }
  return wallet;
}

async function walletFromSeed(seed: Buffer, type: Wallet['type']): Promise<Wallet> {
  const keyPair = nacl.sign.keyPair.fromSeed(seed);
  const publicKey = Buffer.from(keyPair.publicKey);
  return {
    address: await createOctraAddress(publicKey),
    privateKey: bufferToBase64(seed),
    publicKey: bufferToHex(publicKey),
    type,
  };
}

export async function generateWallet(): Promise<Wallet> {
  let walletData;
  let attempts = 0;

  do {
    if (attempts >= MAX_GENERATION_ATTEMPTS) {
      throw new Error(
        `Failed to generate wallet with ${EXPECTED_ADDRESS_LENGTH}-character address after ${MAX_GENERATION_ATTEMPTS} attempts`,
      );
    }
    walletData = await generateWalletFromMnemonic(generateMnemonic());
    attempts++;
  } while (walletData.address.length !== EXPECTED_ADDRESS_LENGTH);

  return {
    address: walletData.address,
    privateKey: walletData.privateKey,
    mnemonic: walletData.mnemonic,
    publicKey: walletData.publicKey,
    type: 'generated',
  };
}

export async function importWalletFromPrivateKey(privateKey: string): Promise<Wallet> {
  const cleanKey = privateKey.trim();
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(cleanKey)) {
    throw new Error('Private key must be valid Base64.');
  }

  const seed = Buffer.from(cleanKey, 'base64');
  if (seed.length !== 32) {
    throw new Error('Private key must decode to 32 bytes.');
  }

  return requireValidOctraWallet(
    await walletFromSeed(seed, 'imported-private-key'),
    'Base64 private key',
  );
}

export async function importWalletFromEvmPrivateKey(privateKey: string): Promise<Wallet> {
  const cleanKey = privateKey.trim().replace(/^0x/i, '');
  if (!/^[0-9a-fA-F]{64}$/.test(cleanKey)) {
    throw new Error('EVM private key must contain exactly 64 hexadecimal characters, with an optional 0x prefix.');
  }

  const scalar = BigInt(`0x${cleanKey}`);
  if (scalar <= 0n || scalar >= SECP256K1_ORDER) {
    throw new Error('EVM private key is outside the valid secp256k1 range.');
  }

  return requireValidOctraWallet(
    await walletFromSeed(Buffer.from(cleanKey, 'hex'), 'imported-evm-private-key'),
    'EVM private key',
  );
}

export async function importWalletFromMnemonic(mnemonic: string): Promise<Wallet> {
  const words = mnemonic.trim().split(/\s+/);
  if (words.length !== 12 && words.length !== 24) {
    throw new Error('Invalid mnemonic length. Must be 12 or 24 words.');
  }
  if (!validateMnemonic(mnemonic)) {
    throw new Error('Invalid mnemonic phrase');
  }

  const walletData = await generateWalletFromMnemonic(mnemonic);
  return requireValidOctraWallet({
    address: walletData.address,
    privateKey: walletData.privateKey,
    mnemonic: walletData.mnemonic,
    publicKey: walletData.publicKey,
    type: 'imported-mnemonic',
  }, 'mnemonic phrase');
}
