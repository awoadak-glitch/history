import {DEFAULT_BASE} from './client.mjs';

const imageKeys = /^(?:poster|banner|thumbnail|logo|image|bg_image|logo_image|promo_bg_image|promo_char_image|series_poster|series_banner|anime_poster|anime_banner|federation_logo|league_logo|image_url|photo|file_path|profile_path|channel_logo|home_team_logo|away_team_logo)$/;

export function normalizeImages(value, base = DEFAULT_BASE) {
  if (Array.isArray(value)) return value.map(item => normalizeImages(item, base));
  if (!value || typeof value !== 'object') return value;
  return Object.fromEntries(Object.entries(value).map(([key, item]) => {
    if (imageKeys.test(key) && typeof item === 'string' && item) {
      try {
        if (/^https?:/i.test(item)) {
          const absolute=new URL(item);
          return [key,absolute.hostname==='admin.dramaramadan.net'?new URL(absolute.pathname+absolute.search,base).href:item];
        }
        if (/^[a-z][a-z\d+.-]*:/i.test(item)) return [key,''];
        const path='/'+item.replace(/^\/+/, '');
        return [key,/^\/(uploads|storage|api|static|img)\//.test(path)?new URL(path,base).href:'https://image.tmdb.org/t/p/w780'+path];
      } catch {return [key, ''];}
    }
    return [key, normalizeImages(item, base)];
  }));
}
