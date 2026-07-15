import { readFileSync } from "node:fs";
import vm from "node:vm";
import { describe, expect, it } from "vitest";

const providerSource = readFileSync(new URL("../../public/inpage.js", import.meta.url), "utf8");

describe("RFC-O-1 in-page provider", () => {
  it("announces Poctra without replacing another compliant wallet", () => {
    const otherProvider = { isOctra: true, providerId: "other" };
    const harness = createHarness(otherProvider);
    vm.runInNewContext(providerSource, harness.context);

    expect(harness.window.octra).toBe(otherProvider);
    expect(harness.window.octraProviders).toHaveLength(1);
    expect(harness.window.octraProviders[0].providerId).toBe("poctra");
    expect(harness.announcements).toHaveLength(1);
    expect(harness.announcements[0].info.rdns).toBe("app.poctra.wallet");
  });

  it("normalizes object params and resolves provider responses", async () => {
    const harness = createHarness();
    vm.runInNewContext(providerSource, harness.context);
    const request = harness.window.octra.request({ method: "octra_requestAccounts", params: { permissions: ["read_address"] } });
    const outgoing = harness.messages.at(-1);

    expect(outgoing.params).toEqual([{ permissions: ["read_address"] }]);
    harness.emitMessage({ source: "poctra-content", id: outgoing.id, result: ["oct-test"] });

    await expect(request).resolves.toEqual(["oct-test"]);
    expect(harness.window.octra.selectedAddress).toBe("oct-test");
  });

  it("rejects malformed requests with RFC error 4200", async () => {
    const harness = createHarness();
    vm.runInNewContext(providerSource, harness.context);
    await expect(harness.window.octra.request({})).rejects.toMatchObject({ code: 4200 });
  });
});

function createHarness(existingProvider?: Record<string, unknown>) {
  const listeners = new Map<string, Set<(event: any) => void>>();
  const messages: any[] = [];
  const announcements: any[] = [];
  const windowObject: any = {
    location: { origin: "https://dapp.example" },
    octra: existingProvider,
    addEventListener(type: string, handler: (event: any) => void) {
      const values = listeners.get(type) || new Set();
      values.add(handler);
      listeners.set(type, values);
    },
    dispatchEvent(event: any) {
      if (event.type === "octra:announceProvider") announcements.push(event.detail);
      listeners.get(event.type)?.forEach((handler) => handler(event));
      return true;
    },
    postMessage(message: any) { messages.push(message); },
    setTimeout,
    clearTimeout
  };
  const context = {
    window: windowObject,
    crypto: globalThis.crypto,
    Error,
    Map,
    Set,
    Object,
    Array,
    Date,
    CustomEvent: class { type: string; detail: any; constructor(type: string, init?: { detail?: any }) { this.type = type; this.detail = init?.detail; } },
    Event: class { type: string; constructor(type: string) { this.type = type; } }
  };
  return {
    context,
    window: windowObject,
    messages,
    announcements,
    emitMessage(data: any) {
      listeners.get("message")?.forEach((handler) => handler({ source: windowObject, origin: windowObject.location.origin, data }));
    }
  };
}
