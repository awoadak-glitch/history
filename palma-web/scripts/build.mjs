import { cp, rm, mkdir } from 'node:fs/promises';
await rm('dist', { recursive: true, force: true });
await mkdir('dist', { recursive: true });
await cp('public', 'dist', { recursive: true });
console.log('PALMA VISION static assets built. Vercel serves api/*.js separately.');
