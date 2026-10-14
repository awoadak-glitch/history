import {DEFAULT_POSTER_BASE} from './client.mjs';

const imageKeys = /^(?:poster|banner|thumbnail|logo|image|bg_image|logo_image|promo_bg_image|promo_char_image|series_poster|series_banner|anime_poster|anime_banner|federation_logo|league_logo|image_url)$/;

export function normalizeImages(value, base = DEFAULT_POSTER_BASE) {
  if (Array.isArray(value)) return value.map(item => normalizeImages(item, base));
  if (!value || typeof value !== 'object') return value;
  return Object.fromEntries(Object.entries(value).map(([key, item]) => {
    if (imageKeys.test(key) && typeof item === 'string' && item && !/^[a-z][a-z\d+.-]*:/i.test(item)) {
      try {return [key, new URL(item, base).href];} catch {return [key, ''];}
    }
    return [key, normalizeImages(item, base)];
  }));
}
