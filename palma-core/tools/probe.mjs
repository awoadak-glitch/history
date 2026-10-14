#!/usr/bin/env node
import {mkdir, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {PalmaClient} from '../src/client.mjs';

const [route = 'api/v2/home.php', ...pairs] = process.argv.slice(2);
const query = Object.fromEntries(pairs.map(pair => {const index = pair.indexOf('='); if(index < 1) throw new Error('Use key=value query arguments'); return [pair.slice(0,index), pair.slice(index+1)];}));
const client = new PalmaClient({base: process.env.PALMA_API_BASE});
const root = resolve(import.meta.dirname, '..');
try {
  const result = await client.get(route, query);
  await mkdir(resolve(root, 'captures'), {recursive: true});
  const capture = route.replaceAll('/', '_').replaceAll('.', '_') + '.json';
  await writeFile(resolve(root, 'captures', capture), JSON.stringify(result.body, null, 2) + '\n');
  const data = result.body.data;
  console.log(JSON.stringify({route, query, status: result.status, bytes: result.bytes, elapsed_ms: result.elapsed_ms,
    response_status: result.body.status, data_keys: data && !Array.isArray(data) ? Object.keys(data) : undefined,
    items: Array.isArray(data) ? data.length : undefined, sections: data?.sections?.length, banners: data?.banners?.length,
  }, null, 2));
} catch (error) {
  console.error(JSON.stringify({route, error: error.message, status: error.status, code: error.code}));
  process.exitCode = 1;
}
