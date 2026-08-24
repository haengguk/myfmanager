import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const FRONTEND_DIR = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const ASSET_DIR = resolve(FRONTEND_DIR, 'dist/assets');
const invariant = (condition, message) => { if (!condition) throw new Error(`[real-match-bundle] ${message}`); };
const assets = readdirSync(ASSET_DIR);
const mainName = assets.find((name) => /^index-[\w-]+\.js$/.test(name));
const referenceName = assets.find((name) => /^matchSession\.adapter-[\w-]+\.js$/.test(name));
invariant(mainName, 'LIVE initial JS chunk를 찾지 못했습니다.');
invariant(referenceName, 'reference dynamic chunk를 찾지 못했습니다.');
const mainSource = readFileSync(resolve(ASSET_DIR, mainName), 'utf8');
const referenceSource = readFileSync(resolve(ASSET_DIR, referenceName), 'utf8');
for (const marker of ['V8 Reference Fixture', 'sourceFullResponseBytes:33617922', 'sourceManifestRawSha256']) {
  invariant(!mainSource.includes(marker), `LIVE initial chunk에 reference marker가 포함됐습니다: ${marker}`);
  invariant(referenceSource.includes(marker), `reference chunk에서 marker를 찾지 못했습니다: ${marker}`);
}
const mainBytes = statSync(resolve(ASSET_DIR, mainName)).size;
const referenceBytes = statSync(resolve(ASSET_DIR, referenceName)).size;
console.log(`[real-match-bundle] OK initial=${mainName}:${mainBytes}B reference=${referenceName}:${referenceBytes}B lazy=true`);
