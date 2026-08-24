import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, isAbsolute, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const FRONTEND_DIR = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const DIST_DIR = resolve(FRONTEND_DIR, 'dist');
const ASSET_DIR = resolve(DIST_DIR, 'assets');
const INDEX_PATH = resolve(DIST_DIR, 'index.html');
const invariant = (condition, message) => { if (!condition) throw new Error(`[real-match-bundle] ${message}`); };

function moduleEntryPaths(html) {
  return [...html.matchAll(/<script\b[^>]*>/gi)]
    .map(([tag]) => ({
      isModule: /\btype=["']module["']/i.test(tag),
      source: tag.match(/\bsrc=["']([^"']+)["']/i)?.[1] ?? null,
    }))
    .filter((script) => script.isModule && script.source)
    .map((script) => resolve(DIST_DIR, script.source.replace(/^\//, '')));
}

function staticImports(source) {
  return [...source.matchAll(/\bimport\s*(?:[\w*${}\s,]+\s+from\s*)?["']([^"']+)["']/g)]
    .map((match) => match[1]);
}

function initialModuleGraph(entries) {
  const visited = new Set();
  const pending = [...entries];
  while (pending.length > 0) {
    const path = pending.pop();
    if (visited.has(path)) continue;
    const relativePath = relative(DIST_DIR, path);
    invariant(relativePath && !relativePath.startsWith('..') && !isAbsolute(relativePath), `dist 밖의 initial module을 참조합니다: ${path}`);
    invariant(statSync(path).isFile(), `initial module을 찾지 못했습니다: ${relative(DIST_DIR, path)}`);
    visited.add(path);
    const source = readFileSync(path, 'utf8');
    for (const specifier of staticImports(source)) {
      if (specifier.startsWith('.') || specifier.startsWith('/')) {
        pending.push(specifier.startsWith('/')
          ? resolve(DIST_DIR, specifier.replace(/^\//, ''))
          : resolve(dirname(path), specifier));
      }
    }
  }
  return visited;
}

const indexSource = readFileSync(INDEX_PATH, 'utf8');
const entries = moduleEntryPaths(indexSource);
invariant(entries.length > 0, 'dist/index.html에서 module entry를 찾지 못했습니다.');
const initialPaths = initialModuleGraph(entries);
const referenceNames = readdirSync(ASSET_DIR).filter((name) => /^matchSession\.adapter-[\w-]+\.js$/.test(name));
invariant(referenceNames.length === 1, `reference dynamic chunk가 정확히 하나여야 합니다. actual=${referenceNames.length}`);
const referencePath = resolve(ASSET_DIR, referenceNames[0]);
invariant(!initialPaths.has(referencePath), 'reference adapter가 initial static module graph에 포함됐습니다.');

const initialSource = [...initialPaths].map((path) => readFileSync(path, 'utf8')).join('\n');
const referenceSource = readFileSync(referencePath, 'utf8');
for (const marker of ['V8 Reference Fixture', 'sourceFullResponseBytes:33617922', 'sourceManifestRawSha256']) {
  invariant(!initialSource.includes(marker), `LIVE initial module graph에 reference marker가 포함됐습니다: ${marker}`);
  invariant(referenceSource.includes(marker), `reference chunk에서 marker를 찾지 못했습니다: ${marker}`);
}

const initialBytes = [...initialPaths].reduce((total, path) => total + statSync(path).size, 0);
const referenceBytes = statSync(referencePath).size;
const initialNames = [...initialPaths].map((path) => relative(DIST_DIR, path)).sort().join(',');
console.log(`[real-match-bundle] OK initialGraph=${initialNames} initialBytes=${initialBytes} reference=${referenceNames[0]}:${referenceBytes}B lazy=true`);
