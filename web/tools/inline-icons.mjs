// Inline the Lucide sprite into index.html at the <!--ICONS--> marker.
import { readFile, writeFile } from "node:fs/promises";
const html = await readFile("index.html", "utf8");
const sprite = (await readFile("assets/icons.svg.html", "utf8")).trim();
const out = html.replace("<!--ICONS-->", sprite);
await writeFile("index.html", out);
console.log("Inlined", sprite.length, "bytes of icons.");
