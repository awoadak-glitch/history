import { createHash, randomBytes } from 'node:crypto';
import { cp, rm, mkdir, readFile, writeFile } from 'node:fs/promises';
import {prepareVendors} from './vendor.mjs';
await prepareVendors();
await rm('dist', { recursive: true, force: true });
await mkdir('dist', { recursive: true });
await cp('public', 'dist', { recursive: true });

// One private key per deployment, shared by the resolve and media Functions.
// It is generated after checkout and never copied to public/ or dist/.
await mkdir('server/generated', {recursive:true});
await writeFile('server/generated/media-key.json',JSON.stringify({key:randomBytes(32).toString('hex')}),{mode:0o600});

// Serve the verified Windows release as a static download, never through a Function.
const desktop = JSON.parse(await readFile(new URL('../desktop-release.json', import.meta.url), 'utf8'));
const response = await fetch(desktop.url, { signal: AbortSignal.timeout(60_000) });
if (!response.ok) throw new Error(`Desktop release download failed: HTTP ${response.status}`);
const archive = Buffer.from(await response.arrayBuffer());
const checksum = createHash('sha256').update(archive).digest('hex');
if (archive.length !== desktop.size || checksum !== desktop.sha256) {
  throw new Error('Desktop release does not match the verified Windows build.');
}
await mkdir('dist/downloads', { recursive: true });
await writeFile(`dist/downloads/${desktop.filename}`, archive);
await writeFile(`dist/downloads/${desktop.filename}.sha256`, `${checksum}  ${desktop.filename}\n`);
console.log(`Verified desktop package: ${desktop.filename} (${archive.length} bytes).`);
console.log('PALMA VISION static assets built. Vercel serves api/*.js separately.');
