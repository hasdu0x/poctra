const BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

export function bytesToHex(bytes: Uint8Array): string {
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

export function hexToBytes(value: string): Uint8Array {
  const clean = value.replace(/^0x/i, "");
  if (!/^[0-9a-f]*$/i.test(clean) || clean.length % 2) throw new Error("Invalid hexadecimal value.");
  return Uint8Array.from(clean.match(/.{2}/g) || [], (pair) => Number.parseInt(pair, 16));
}

export function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  for (let index = 0; index < bytes.length; index += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(index, index + 0x8000));
  }
  return btoa(binary);
}

export function base64ToBytes(value: string): Uint8Array {
  try {
    const binary = atob(value.trim());
    return Uint8Array.from(binary, (character) => character.charCodeAt(0));
  } catch {
    throw new Error("Private key must be valid Base64.");
  }
}

export function base58Encode(input: Uint8Array): string {
  if (!input.length) return "";
  let number = BigInt(`0x${bytesToHex(input) || "0"}`);
  let output = "";
  while (number > 0n) {
    const remainder = Number(number % 58n);
    output = BASE58_ALPHABET[remainder] + output;
    number /= 58n;
  }
  for (const byte of input) {
    if (byte !== 0) break;
    output = `1${output}`;
  }
  return output;
}

export function utf8(value: string): Uint8Array {
  return new TextEncoder().encode(value);
}

export function decodeUtf8(value: Uint8Array): string {
  return new TextDecoder().decode(value);
}

export function shortAddress(value: string, size = 8): string {
  if (value.length <= size * 2 + 3) return value;
  return `${value.slice(0, size)}...${value.slice(-size)}`;
}

export function formatUnits(raw: bigint, decimals: number, precision = 6): string {
  if (decimals <= 0) return raw.toString();
  const unit = 10n ** BigInt(decimals);
  const whole = raw / unit;
  const fraction = (raw % unit).toString().padStart(decimals, "0").slice(0, precision).replace(/0+$/, "");
  return fraction ? `${whole}.${fraction}` : whole.toString();
}

export function parseUnits(value: string, decimals: number): bigint {
  const clean = value.trim().replace(",", ".");
  if (!/^\d+(?:\.\d+)?$/.test(clean)) throw new Error("Enter a valid amount.");
  const [whole, fraction = ""] = clean.split(".");
  if (fraction.length > decimals) throw new Error(`Amount supports up to ${decimals} decimals.`);
  return BigInt(whole) * 10n ** BigInt(decimals) + BigInt(fraction.padEnd(decimals, "0") || "0");
}
