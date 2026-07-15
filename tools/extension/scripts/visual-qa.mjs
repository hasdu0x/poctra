import { spawn } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { randomUUID } from "node:crypto";
import WebSocket from "ws";

const chromePath = process.env.CHROME_PATH || "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const port = 9337;
const profile = join(tmpdir(), `poctra-extension-qa-${randomUUID()}`);
const outputDir = new URL("../qa/", import.meta.url);
mkdirSync(outputDir, { recursive: true });

const chrome = spawn(chromePath, [
  "--headless=new",
  "--disable-gpu",
  "--no-first-run",
  "--remote-allow-origins=*",
  `--remote-debugging-port=${port}`,
  `--user-data-dir=${profile}`,
  "--window-size=400,600",
  "http://127.0.0.1:4180/popup.html"
], { stdio: "ignore" });

try {
  await waitForChrome();
  const targets = await fetch(`http://127.0.0.1:${port}/json/list`).then((response) => response.json());
  const target = targets.find((item) => item.type === "page" && item.url.includes("popup.html"));
  if (!target) throw new Error("Popup target was not created.");
  const cdp = connect(target.webSocketDebuggerUrl);
  await cdp.ready;
  await cdp.send("Page.enable");
  await cdp.send("Runtime.enable");
  await cdp.send("Emulation.setDeviceMetricsOverride", { width: 400, height: 600, deviceScaleFactor: 1, mobile: false });
  await waitFor(cdp, `[...document.querySelectorAll('button')].some((item) => item.textContent.includes('Import Wallet'))`, 5_000);

  await click(cdp, "Import Wallet");
  await delay(120);
  await captureAndAssert(cdp, "popup-import-methods");
  await delay(100);
  await click(cdp, "Private Key (Base64)");
  await delay(120);
  await captureAndAssert(cdp, "popup-import-key");
  // Public fixture only. The corresponding scalar is 1 and must never hold funds.
  await setInput(cdp, "input", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAE=");
  await click(cdp, "Import Wallet");
  await delay(150);
  await captureAndAssert(cdp, "popup-create-password");
  await setInput(cdp, "input:nth-of-type(1)", "poctra-visual-qa");
  await cdp.send("Runtime.evaluate", { expression: `(() => { const inputs = document.querySelectorAll('input[type="password"]'); const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set; setter.call(inputs[1], 'poctra-visual-qa'); inputs[1].dispatchEvent(new Event('input', { bubbles: true })); })()` });
  await click(cdp, "Secure Wallet");
  await delay(1200);

  await captureAndAssert(cdp, "popup-dashboard");
  await cdp.send("Runtime.evaluate", { expression: `document.querySelector('.wallet-select')?.click()` });
  await waitFor(cdp, `document.querySelector('.drawer-layer')?.classList.contains('open')`, 3_000);
  await captureAndAssert(cdp, "popup-wallet-drawer");
  await cdp.send("Runtime.evaluate", { expression: `document.querySelector('.wallet-drawer button[aria-label="Close"]')?.click()` });
  await waitFor(cdp, `!document.querySelector('.drawer-layer')?.classList.contains('open')`, 3_000);
  await click(cdp, "Receive");
  await waitFor(cdp, `document.querySelector('.screen-title h1')?.textContent === 'Receive OCT'`, 3_000);
  await captureAndAssert(cdp, "popup-receive-oct");
  await assertNoVerticalScroll(cdp, ".screen-content", "Receive OCT");
  await cdp.send("Runtime.evaluate", { expression: `document.querySelector('button[aria-label="Back"]')?.click()` });
  await waitFor(cdp, `Boolean(document.querySelector('.overview-header'))`, 3_000);
  await click(cdp, "Bridge");
  await waitFor(cdp, `document.querySelector('.screen-title h1')?.textContent === 'Bridge'`, 3_000);
  await delay(250);
  await captureAndAssert(cdp, "popup-bridge");
  await assertNoVerticalScroll(cdp, ".screen-content", "Bridge");
  await cdp.send("Runtime.evaluate", { expression: `document.querySelector('button[aria-label="Back"]')?.click()` });
  await waitFor(cdp, `Boolean(document.querySelector('.overview-header'))`, 3_000);
  await click(cdp, "Assets");
  await delay(150);
  await captureAndAssert(cdp, "popup-assets");
  await click(cdp, "EVM Account");
  await waitFor(cdp, `Boolean(document.querySelector('.evm-account-screen'))`, 3_000);
  await captureAndAssert(cdp, "popup-evm-account");
  await cdp.send("Runtime.evaluate", { expression: `document.querySelector('button[aria-label="EVM activity"]')?.click()` });
  await waitFor(cdp, `document.querySelector('.screen-title h1')?.textContent === 'EVM Activity'`, 3_000);
  await captureAndAssert(cdp, "popup-evm-activity");
  await cdp.send("Runtime.evaluate", { expression: `document.querySelector('button[aria-label="Back"]')?.click()` });
  await waitFor(cdp, `Boolean(document.querySelector('.evm-account-screen'))`, 3_000);
  await cdp.send("Runtime.evaluate", { expression: `document.querySelector('button[aria-label="Back"]')?.click()` });
  await waitFor(cdp, `Boolean(document.querySelector('.assets-content'))`, 3_000);
  await click(cdp, "Trade");
  await waitFor(cdp, `Boolean(document.querySelector('.candle-chart svg, .chart-placeholder'))`, 5_000);
  await delay(900);
  await captureAndAssert(cdp, "popup-trade");
  await assertNoVerticalScroll(cdp, ".dashboard-scroll", "Trade");
  await click(cdp, "Assets");
  await delay(100);
  await cdp.send("Runtime.evaluate", { expression: `document.querySelector('button[aria-label="Settings"]')?.click()` });
  await waitFor(cdp, `Boolean(document.querySelector('.settings-list'))`, 3_000);
  await captureAndAssert(cdp, "popup-settings");
  await click(cdp, "Portfolio");
  await waitFor(cdp, `Boolean(document.querySelector('.portfolio-screen'))`, 3_000);
  await captureAndAssert(cdp, "popup-portfolio");
  await cdp.send("Runtime.evaluate", { expression: `document.querySelector('button[aria-label="Back"]')?.click()` });
  await waitFor(cdp, `Boolean(document.querySelector('.settings-list'))`, 3_000);
  await click(cdp, "Activity Center");
  await waitFor(cdp, `Boolean(document.querySelector('.activity-center'))`, 3_000);
  await captureAndAssert(cdp, "popup-activity-center");
  await cdp.send("Emulation.setDeviceMetricsOverride", { width: 1280, height: 720, deviceScaleFactor: 1, mobile: false });
  await cdp.send("Runtime.evaluate", { expression: `location.href = 'http://127.0.0.1:4180/app.html'` });
  await waitFor(cdp, `Boolean(document.querySelector('.overview-header'))`, 5_000);
  await captureExpandedDashboard(cdp);
  await cdp.send("Runtime.evaluate", { expression: `localStorage.clear(); location.href = 'http://127.0.0.1:4180/app.html'` });
  await waitFor(cdp, `[...document.querySelectorAll('button')].some((item) => item.textContent.includes('Import Wallet'))`, 5_000);
  await captureExpandedOnboarding(cdp);
  process.stdout.write("Visual QA passed: popup flows at 400x600 and expanded onboarding at 1280x720\n");
} finally {
  chrome.kill();
}

async function captureExpandedDashboard(cdp) {
  await delay(260);
  const layout = await evaluate(cdp, `(() => { const app = document.querySelector('.app-root')?.getBoundingClientRect(); const address = document.querySelector('.wallet-select small')?.textContent?.trim() || ''; return app ? { left: app.left, width: app.width, address, viewport: innerWidth } : null; })()`);
  const shot = await cdp.send("Page.captureScreenshot", { format: "png", captureBeyondViewport: false, clip: { x: 0, y: 0, width: 1280, height: 720, scale: 1 } });
  writeFileSync(new URL("expanded-dashboard.png", outputDir), Buffer.from(shot.data, "base64"));
  writeFileSync(new URL("expanded-dashboard.json", outputDir), JSON.stringify(layout, null, 2));
  if (!layout || layout.width > 621 || !/^oct[A-Za-z0-9]{40,}$/.test(layout.address)) throw new Error(`Expanded dashboard validation failed: ${JSON.stringify(layout)}`);
}

async function assertNoVerticalScroll(cdp, selector, name) {
  const layout = await evaluate(cdp, `(() => { const element = document.querySelector(${JSON.stringify(selector)}); return element ? { clientHeight: element.clientHeight, scrollHeight: element.scrollHeight } : null; })()`);
  if (!layout || layout.scrollHeight > layout.clientHeight + 1) throw new Error(`${name} requires vertical scrolling: ${JSON.stringify(layout)}`);
}

async function captureExpandedOnboarding(cdp) {
  await delay(260);
  const layout = await evaluate(cdp, `(() => { const rect = document.querySelector('.onboarding')?.getBoundingClientRect(); return rect ? { left: rect.left, right: rect.right, width: rect.width, viewport: innerWidth } : null; })()`);
  const shot = await cdp.send("Page.captureScreenshot", { format: "png", captureBeyondViewport: false, clip: { x: 0, y: 0, width: 1280, height: 720, scale: 1 } });
  writeFileSync(new URL("expanded-onboarding.png", outputDir), Buffer.from(shot.data, "base64"));
  writeFileSync(new URL("expanded-onboarding.json", outputDir), JSON.stringify(layout, null, 2));
  if (!layout || layout.width > 621 || Math.abs(layout.left - (layout.viewport - layout.width) / 2) > 1) throw new Error(`Expanded onboarding is not centered: ${JSON.stringify(layout)}`);
}

async function captureAndAssert(cdp, name) {
  await delay(260);
  const layout = await evaluate(cdp, `(() => {
    const viewport = { width: innerWidth, height: innerHeight, scrollWidth: document.documentElement.scrollWidth };
    const overflowing = [...document.querySelectorAll('button, header, main, section, .card')].filter((element) => !element.closest('.drawer-layer:not(.open)')).map((element) => {
      const rect = element.getBoundingClientRect();
      return { tag: element.tagName, className: element.className, text: element.textContent?.trim().slice(0, 50), left: rect.left, right: rect.right, width: rect.width };
    }).filter((item) => item.left < -0.5 || item.right > innerWidth + 0.5);
    const undersized = [...document.querySelectorAll('button')].filter((element) => !element.disabled && !element.closest('.drawer-layer:not(.open)')).map((element) => {
      const rect = element.getBoundingClientRect();
      return { text: element.getAttribute('aria-label') || element.textContent?.trim().slice(0, 40), width: rect.width, height: rect.height };
    }).filter((item) => item.width > 0 && item.height > 0 && (item.width < 28 || item.height < 28));
    return { viewport, overflowing, undersized };
  })()`);
  const shot = await cdp.send("Page.captureScreenshot", { format: "png", captureBeyondViewport: false, clip: { x: 0, y: 0, width: 400, height: 600, scale: 1 } });
  writeFileSync(new URL(`${name}.png`, outputDir), Buffer.from(shot.data, "base64"));
  writeFileSync(new URL(`${name}.json`, outputDir), JSON.stringify(layout, null, 2));
  if (layout.overflowing.length || layout.undersized.length || layout.viewport.scrollWidth > layout.viewport.width) {
    throw new Error(`${name} overflow detected: ${JSON.stringify(layout)}`);
  }
}

async function click(cdp, text) {
  const result = await evaluate(cdp, `(() => { const expected = ${JSON.stringify(text)}; const button = [...document.querySelectorAll('button')].find((item) => item.textContent.trim() === expected) || [...document.querySelectorAll('button')].find((item) => item.textContent.trim().startsWith(expected)); if (!button) return false; button.click(); return true; })()`);
  if (!result) throw new Error(`Button not found: ${text}`);
}

async function setInput(cdp, selector, value) {
  const result = await evaluate(cdp, `(() => { const input = document.querySelector(${JSON.stringify(selector)}); if (!input) return false; const setter = Object.getOwnPropertyDescriptor(input instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype, 'value').set; setter.call(input, ${JSON.stringify(value)}); input.dispatchEvent(new Event('input', { bubbles: true })); return true; })()`);
  if (!result) throw new Error(`Input not found: ${selector}`);
}

async function evaluate(cdp, expression) {
  const result = await cdp.send("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text || "Browser evaluation failed.");
  return result.result.value;
}

async function waitFor(cdp, expression, timeoutMs) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    if (await evaluate(cdp, expression)) return;
    await delay(50);
  }
  throw new Error(`Timed out waiting for: ${expression}`);
}

function connect(url) {
  const socket = new WebSocket(url);
  const pending = new Map();
  let sequence = 0;
  const ready = new Promise((resolve, reject) => {
    socket.once("open", resolve);
    socket.once("error", reject);
    socket.once("close", (code, reason) => reject(new Error(`Chrome DevTools socket closed (${code}): ${String(reason)}`)));
  });
  socket.on("message", (message) => {
    const data = JSON.parse(String(message));
    if (!data.id || !pending.has(data.id)) return;
    const { resolve, reject } = pending.get(data.id);
    pending.delete(data.id);
    data.error ? reject(new Error(data.error.message)) : resolve(data.result);
  });
  return {
    ready,
    send(method, params = {}) {
      return new Promise((resolve, reject) => {
        const id = ++sequence;
        pending.set(id, { resolve, reject });
        socket.send(JSON.stringify({ id, method, params }));
      });
    }
  };
}

async function waitForChrome() {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    try { const response = await fetch(`http://127.0.0.1:${port}/json/version`); if (response.ok) return; } catch { /* Chrome is still starting. */ }
    await delay(100);
  }
  throw new Error("Chrome DevTools did not start.");
}

function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
