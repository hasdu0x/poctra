import type { IdentityProvider, OctraNetwork } from "./types";
import { contractView } from "./octra-rpc";
import { isValidOctraAddress } from "./wallet";

export function isIdentityName(value: string): boolean {
  const clean = value.trim().toLowerCase().replace(/\.oct$/, "");
  if (!clean || clean.length > 189) return false;
  return clean.split(".").every((label) => /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/.test(label));
}

export async function resolveOctraIdentity(
  network: OctraNetwork,
  provider: IdentityProvider,
  name: string,
  caller: string
): Promise<string | null> {
  const clean = name.trim().toLowerCase().replace(/\.oct$/, "");
  if (!isIdentityName(`${clean}.oct`)) return null;
  const method = clean.includes(".") ? "resolve_name" : "resolve";
  const value = await contractView(network, provider.contractAddress, method, [clean], caller);
  return value && isValidOctraAddress(value) ? value : null;
}

export async function reverseOctraIdentity(
  network: OctraNetwork,
  provider: IdentityProvider,
  address: string
): Promise<string | null> {
  if (!isValidOctraAddress(address)) return null;
  const primary = await contractView(network, provider.contractAddress, "primary_of", [address], address).catch(() => null);
  if (primary) return normalizeDomain(primary);
  const total = Number(await contractView(network, provider.contractAddress, "owner_total", [address], address).catch(() => "0"));
  if (total <= 0) return null;
  const first = await contractView(network, provider.contractAddress, "owner_key_at", [address, 0], address).catch(() => null);
  return first ? normalizeDomain(first) : null;
}

function normalizeDomain(value: string): string | null {
  const clean = value.trim().toLowerCase().replace(/\.octra$/, "").replace(/\.id$/, "").replace(/\.oct$/, "");
  return isIdentityName(`${clean}.oct`) ? `${clean}.oct` : null;
}
