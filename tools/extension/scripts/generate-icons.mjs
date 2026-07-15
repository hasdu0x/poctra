import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { Resvg } from "@resvg/resvg-js";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const source = await readFile(resolve(root, "public/assets/poctra.svg"), "utf8");
const output = resolve(root, "public/assets");
await mkdir(output, { recursive: true });

for (const size of [16, 32, 48, 128]) {
  const rendered = new Resvg(source, {
    fitTo: { mode: "width", value: size },
    background: "rgba(0,0,0,0)"
  }).render();
  await writeFile(resolve(output, `icon-${size}.png`), rendered.asPng());
}
