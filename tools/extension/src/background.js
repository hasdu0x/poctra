import { loadSettings, resolveInputTarget } from "./shared.js";

chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: "open-octra",
    title: "Open with circle viewer",
    contexts: ["link", "selection"]
  });
});

chrome.contextMenus.onClicked.addListener(async (info) => {
  const input = info.linkUrl || info.selectionText || "";
  if (!input) return;
  await openOctraInput(input);
});

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message?.type !== "open-octra-input") return false;
  openOctraInput(message.input)
    .then(() => sendResponse({ ok: true }))
    .catch((error) => sendResponse({ ok: false, message: error.message || "Unable to open input." }));
  return true;
});

chrome.omnibox.onInputChanged.addListener((text, suggest) => {
  const clean = text.trim();
  suggest([
    {
      content: clean,
      description: `Open ${escapeXml(clean || "oct:// circle, foo.oct, or octx...")}`
    }
  ]);
});

chrome.omnibox.onInputEntered.addListener(async (text) => {
  await openOctraInput(text);
});

async function openOctraInput(input) {
  const settings = await loadSettings();
  const target = await resolveInputTarget(input, settings);
  await chrome.tabs.create({ url: target.url });
}

function escapeXml(value) {
  return String(value || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}
