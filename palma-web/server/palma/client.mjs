import {requestHeaders} from './iron.mjs';

// Public APK certificate identity; no private APK signing key is involved.
export const APP_PROFILE = Object.freeze({
  package_name: 'com.drama.mp4',
  certificate_sha256: '6e2fcda8631eb49ebcba4ca8ef4c597abe84654c7d3e8096db32bdd21ecf763f',
  version_code: '17',
});
export const DEFAULT_BASE = 'https://api.ossccaarr111.online/';
export const DEFAULT_POSTER_BASE = 'https://poster.ostv-11-33.xyz/';

export class PalmaAPIError extends Error {
  constructor(message, status, code) {
    super(message);
    this.name = 'PalmaAPIError';
    this.status = status;
    this.code = code;
  }
}

export class PalmaClient {
  constructor({base = DEFAULT_BASE, profile = APP_PROFILE, fetchImpl = globalThis.fetch, timeout = 25000} = {}) {
    this.base = new URL(base);
    if (this.base.protocol !== 'https:' || this.base.username || this.base.password) throw new TypeError('PALMA base must be an HTTPS origin');
    this.base.pathname = '/'; this.base.search = ''; this.base.hash = '';
    this.profile = profile;
    this.fetch = fetchImpl;
    this.timeout = timeout;
    this.clockOffset = 0;
    this.clockUpdated = 0;
    this.clockPromise = null;
  }

  updateClock(response) {
    const date = Date.parse(response.headers.get('date'));
    if (Number.isFinite(date)) {
      this.clockOffset = Math.floor(date / 1000) - Math.floor(Date.now() / 1000);
      this.clockUpdated = Date.now();
    }
  }

  async syncClock() {
    if (this.clockUpdated > Date.now() - 300000) return;
    if (!this.clockPromise) this.clockPromise = (async () => {
      const response = await this.fetch(new URL('api/ping.php', this.base), {
        headers: {'User-Agent': 'okhttp/4.12.0'},
        redirect: 'error', signal: AbortSignal.timeout(Math.min(this.timeout, 8000)),
      });
      this.updateClock(response);
      await response.body?.cancel();
    })().finally(() => {this.clockPromise = null;});
    // The Android client can also continue with the system clock if ping fails.
    await this.clockPromise.catch(() => {});
  }

  async get(route, query = {}) {
    if (!/^api\/[A-Za-z0-9_./-]+$/.test(route) || route.split('/').includes('..')) throw new TypeError('Invalid PALMA API route');
    const url = new URL(route, this.base);
    for (const [key, value] of Object.entries(query)) if (value != null) url.searchParams.set(key, String(value));
    if (['api/v2/home.php', 'api/series/', 'api/movies/', 'api/content-categories.php'].includes(route)) {
      url.searchParams.set('app_version', this.profile.version_code || '17');
    }
    await this.syncClock();
    const timestamp = Math.floor(Date.now() / 1000) + this.clockOffset;
    const started = Date.now();
    const response = await this.fetch(url, {
      headers: {...requestHeaders(this.profile, url.pathname, timestamp), 'User-Agent': 'okhttp/4.12.0', Accept: 'application/json'},
      redirect: 'error', signal: AbortSignal.timeout(this.timeout),
    });
    this.updateClock(response);
    const raw = await response.text();
    let body;
    try {body = JSON.parse(raw);} catch {throw new PalmaAPIError('PALMA returned a non-JSON response', response.status, 'INVALID_RESPONSE');}
    if (!response.ok || body.status === 'error') {
      throw new PalmaAPIError(`PALMA request failed (${response.status})`, response.status, 'UPSTREAM_REJECTED');
    }
    return {body, status: response.status, elapsed_ms: Date.now() - started, bytes: Buffer.byteLength(raw)};
  }
}
