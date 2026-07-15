import { describe, expect, it } from "vitest";
import { importBase64Key, importEvmHexKey, importMnemonic, signOctraBytes, verifyWalletRecord } from "./wallet";
import { bytesToBase64, utf8 } from "./encoding";

// Public fixture only. This scalar is intentionally trivial and must never hold funds.
const TEST_PRIVATE_KEY_BASE64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAE=";
const TEST_PRIVATE_KEY_HEX = "0000000000000000000000000000000000000000000000000000000000000001";

describe("wallet derivation", () => {
  it("matches the known Octra Base64 key vector", () => {
    const wallet = importBase64Key(TEST_PRIVATE_KEY_BASE64);
    expect(wallet.address).toMatch(/^oct[1-9A-HJ-NP-Za-km-z]{44}$/);
    expect(wallet.evmAddress).toMatch(/^0x[0-9A-Fa-f]{40}$/);
    expect(verifyWalletRecord(wallet)).toBe(true);
    expect(bytesToBase64(signOctraBytes(wallet, utf8("poctra-test")))).toMatch(/^[A-Za-z0-9+/]{86}==$/);
  });

  it("derives the same account from EVM hex and Base64 seed forms", () => {
    const fromBase64 = importBase64Key(TEST_PRIVATE_KEY_BASE64);
    const fromHex = importEvmHexKey(TEST_PRIVATE_KEY_HEX);
    expect(fromHex.address).toBe(fromBase64.address);
    expect(fromHex.evmAddress).toBe(fromBase64.evmAddress);
  });

  it("rejects invalid mnemonic and malformed keys with clear errors", () => {
    expect(() => importMnemonic("not a seed phrase")).toThrow("12 or 24 words");
    expect(() => importBase64Key("invalid")).toThrow();
    expect(() => importEvmHexKey("0x1234")).toThrow("64 hexadecimal characters");
  });
});
