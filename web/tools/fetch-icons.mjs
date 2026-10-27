// Build an inline SVG sprite from Lucide (ISC). Run from /web.
// Usage: node tools/fetch-icons.mjs
import { writeFile } from "node:fs/promises";

const ICONS = [
  "audio-lines",
  "arrow-down-to-line",
  "arrow-up-right",
  "arrow-right",
  "play",
  "chevron-right",
  "chevron-down",
  "check",
  "x",
  "copy",
  "shield-check",
  "mic",
  "power",
  "terminal",
  "smartphone",
  "lock",
  "sparkles",
  "book-open",
  "github",
  "external-link",
  "menu",
  "triangle-alert",
  "monitor-smartphone",
  "refresh-cw",
  "zap",
  "languages",
  "layers",
  "circle-check",
  "volume-off",
  "file-archive",
  "timer",
  "plug-zap",
];

const out = [];
for (const name of ICONS) {
  const svg = await fetch(`https://unpkg.com/lucide-static@0.468.0/icons/${name}.svg`).then((r) => {
    if (!r.ok) throw new Error(`${name}: ${r.status}`);
    return r.text();
  });
  const inner = svg
    .replace(/^[\s\S]*?<svg[^>]*>/, "")
    .replace(/<\/svg>[\s\S]*$/, "")
    .replace(/\s+/g, " ")
    .trim();
  out.push(`<symbol id="i-${name}" viewBox="0 0 24 24">${inner}</symbol>`);
}
await writeFile("assets/icons.svg.html", `<svg xmlns="http://www.w3.org/2000/svg" aria-hidden="true" style="display:none">\n${out.join("\n")}\n</svg>\n`);
console.log(`Wrote ${ICONS.length} icons.`);
