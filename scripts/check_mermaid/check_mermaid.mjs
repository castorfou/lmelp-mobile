#!/usr/bin/env node
// Vérifie la syntaxe des blocs ```mermaid des fichiers Markdown passés en argument
// (par défaut : tous les .md de docs/), avec l'analyseur de mermaid lui-même.
// mkdocs build ne le fait pas : les schémas sont dessinés dans le navigateur.
// Usage : node scripts/check_mermaid/check_mermaid.mjs [fichier.md ...]
// Dépendances installées par le devcontainer (npm ci dans ce dossier).
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { JSDOM } from "jsdom";

// mermaid (DOMPurify) a besoin d'un DOM, même pour analyser sans dessiner
const dom = new JSDOM("<!doctype html><body></body>", { pretendToBeVisual: true });
for (const k of ["window", "document", "Element", "HTMLElement", "Node", "DocumentFragment", "SVGElement"]) {
  globalThis[k] ??= k === "window" ? dom.window : dom.window[k];
}
const { default: mermaid } = await import("mermaid");

function markdownFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return markdownFiles(path);
    return path.endsWith(".md") ? [path] : [];
  });
}

// Renvoie [{ line, code }] pour chaque bloc ```mermaid (ligne de la fence ouvrante)
function mermaidBlocks(text) {
  const blocks = [];
  let current = null;
  text.split("\n").forEach((raw, i) => {
    const line = raw.trim();
    if (current === null && line === "```mermaid") current = { line: i + 1, lines: [] };
    else if (current !== null && line.startsWith("```")) {
      blocks.push({ line: current.line, code: current.lines.join("\n") });
      current = null;
    } else if (current !== null) current.lines.push(raw);
  });
  return blocks;
}

const files = process.argv.length > 2 ? process.argv.slice(2) : markdownFiles("docs");
let errors = 0;
let count = 0;
for (const file of files) {
  for (const { line, code } of mermaidBlocks(readFileSync(file, "utf8"))) {
    count++;
    try {
      await mermaid.parse(code);
    } catch (e) {
      errors++;
      console.error(`❌ ${file}:${line} : ${String(e.message ?? e).split("\n").join("\n   ")}`);
    }
  }
}
console.log(`${count} schéma(s) mermaid analysé(s), ${errors} en erreur`);
process.exit(errors ? 1 : 0);
