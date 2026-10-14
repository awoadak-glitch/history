import {createHash, createHmac, randomUUID} from 'node:crypto';

const digest = data => createHash('sha256').update(data).digest();
const xor = (data, mask) => Buffer.from(data.map((value, index) => value ^ mask[index % mask.length]));

// Port of the supplied libiron_fingerprint.so's nativeSign calculation.
// The certificate hash is public APK identity, not a private signing key.
export function deriveRequestKey(certificateSha256, packageName) {
  if (!/^[a-f0-9]{64}$/.test(certificateSha256)) throw new TypeError('Expected lowercase SHA-256 certificate fingerprint');
  if (!/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/.test(packageName)) throw new TypeError('Invalid Android package name');
  const joined = Buffer.from(`${certificateSha256}|${packageName}`, 'utf8');
  const first = xor(joined, Buffer.from('OscarTV'));
  const second = xor(Buffer.from(first).reverse(), Buffer.from('IronGuard'));
  return digest(digest(digest(second)));
}

export function signPath(key, path, timestamp, nonce) {
  if (!path.startsWith('/api/') || /[?#\r\n]/.test(path)) throw new TypeError('Sign only the encoded API path, without query or fragment');
  if (!/^\d+$/.test(String(timestamp)) || !/^[a-f0-9]{8}$/.test(nonce)) throw new TypeError('Invalid timestamp or nonce');
  return createHmac('sha256', key).update(`${path}|${timestamp}|${nonce}`).digest('hex');
}

export function requestHeaders(profile, path, timestamp = Math.floor(Date.now() / 1000), nonce = randomUUID().replaceAll('-', '').slice(0, 8)) {
  const key = deriveRequestKey(profile.certificate_sha256, profile.package_name);
  return {
    'X-Iron-Sig': signPath(key, path, timestamp, nonce),
    'X-Iron-Ts': String(timestamp),
    'X-Iron-Nonce': nonce,
  };
}
