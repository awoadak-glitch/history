// Inspect bytes, not the source's filename: live PALMA streams may end in .css.
export function detectMedia(bytes, contentType = '', url = '') {
  const text = new TextDecoder().decode(bytes.subarray(0, 1024)).trimStart();
  if (text.startsWith('#EXTM3U')) return 'hls';
  if (/<MPD(?:\s|>)/i.test(text)) return 'dash';
  if (/^(?:<!doctype\s+html|<html|<head|<body|\{\s*")/i.test(text)) return 'page';
  if (bytes[0] === 0x46 && bytes[1] === 0x4c && bytes[2] === 0x56) return 'flv';
  for (let offset = 0; offset < Math.min(188, bytes.length - 376); offset++) {
    if (bytes[offset] === 0x47 && bytes[offset + 188] === 0x47 && bytes[offset + 376] === 0x47) return 'mpegts';
  }
  if (/ftyp|moov|moof|styp/.test(new TextDecoder().decode(bytes.subarray(4, 8)))) return 'video';
  if (bytes[0] === 0x1a && bytes[1] === 0x45 && bytes[2] === 0xdf && bytes[3] === 0xa3) return 'video';
  if (/mpegurl/i.test(contentType)) return 'hls';
  if (/mp2t/i.test(contentType)) return 'mpegts';
  if (/x-flv/i.test(contentType)) return 'flv';
  if (/dash\+xml/i.test(contentType)) return 'dash';
  if (/^video\//i.test(contentType)) return 'video';
  if (!/text|json|xml/i.test(contentType)) {
    if (/\.m3u8(?:\?|$)/i.test(url)) return 'hls';
    if (/\.ts(?:\?|$)/i.test(url)) return 'mpegts';
    if (/\.(mp4|webm|mkv|m4v)(?:\?|$)/i.test(url)) return 'video';
  }
  return 'page';
}

export async function inspectMedia(response, url) {
  const reader = response.body?.getReader();
  if (!reader) return {type:'page', response};
  const chunks = []; let size = 0, ended = false;
  while (size < 1024) {
    const next = await reader.read();
    if (next.done) { ended = true; break; }
    chunks.push(next.value); size += next.value.length;
  }
  const prefix = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { prefix.set(chunk, offset); offset += chunk.length; }
  const type = detectMedia(prefix, response.headers.get('content-type') || '', url);
  const stream = new ReadableStream({
    start(controller) { for (const chunk of chunks) controller.enqueue(chunk); if (ended) controller.close(); },
    async pull(controller) { if (ended) return; const next = await reader.read(); if (next.done) { ended = true; controller.close(); } else controller.enqueue(next.value); },
    cancel(reason) { return reader.cancel(reason); },
  });
  return {type, response:new Response(stream, {status:response.status, headers:response.headers})};
}
