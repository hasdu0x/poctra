import { describe, expect, it } from "vitest";
import { createVault, decryptVault, encryptVault, encryptVaultWithSessionKey, hydrateVault, unlockVault } from "./vault";
import { importBase64Key } from "./wallet";

describe("password vault", () => {
  // Public fixture only. The corresponding scalar is 1 and must never hold funds.
  const wallet = importBase64Key("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAE=");

  it("round-trips encrypted wallet data", async () => {
    const vault = createVault(wallet);
    const envelope = await encryptVault(vault, "strong-test-password");
    expect(envelope.ciphertext).not.toContain(wallet.privateKeyBase64);
    const unlocked = await unlockVault(envelope, "strong-test-password");
    expect(unlocked.vault.activeAddress).toBe(wallet.address);
    expect(unlocked.sessionKey).toBeTruthy();
  });

  it("rejects an incorrect password", async () => {
    const envelope = await encryptVault(createVault(wallet), "strong-test-password");
    await expect(decryptVault(envelope, "wrong-password-value")).rejects.toThrow("Incorrect password");
  });

  it("can persist changes using only the transient unlocked key", async () => {
    const original = createVault(wallet);
    const envelope = await encryptVault(original, "strong-test-password");
    const unlocked = await unlockVault(envelope, "strong-test-password");
    const changed = { ...unlocked.vault, wallets: [{ ...wallet, name: "Primary" }] };
    const updatedEnvelope = await encryptVaultWithSessionKey(changed, envelope, unlocked.sessionKey);
    const reopened = await decryptVault(updatedEnvelope, "strong-test-password");
    expect(reopened.wallets[0].name).toBe("Primary");
    expect(updatedEnvelope.iv).not.toBe(envelope.iv);
  });

  it("hydrates fields added after a vault was created", () => {
    const legacy = createVault(wallet);
    delete (legacy.settings as Partial<typeof legacy.settings>).evmNfts;
    const hydrated = hydrateVault(legacy);
    expect(hydrated.settings.evmNfts).toEqual([]);
    expect(hydrated.settings.octraNetworks.length).toBeGreaterThan(0);
  });
});
