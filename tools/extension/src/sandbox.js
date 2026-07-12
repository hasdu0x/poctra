window.addEventListener("message", (event) => {
  const message = event.data;
  if (message?.source !== "poctra-viewer-render") return;
  document.open();
  document.write(message.html || "<!doctype html><title>circle viewer</title>");
  document.close();
});
