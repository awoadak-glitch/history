import {createHash} from 'node:crypto';
import {SourceError} from './http.js';

// Exact normalized fingerprint of the rejection playlist verified in Windows
// and again in a fresh V3 session. Duration alone is not proof of rejection:
// trailers, match highlights, and other legitimate clips can be short.
const noticePlaylists=new Set([
  'ac540b598ddfee6bdf1a73d7f13c471a17de3abf95b3075db0c2726532f7ab07'
]);

export function assertPlaybackMedia(url,playlist) {
  const noticeHost=new URL(url).hostname==='notice.shahidtv.net';
  const knownPlaylist=typeof playlist==='string'&&noticePlaylists.has(
    createHash('sha256').update(playlist.replaceAll('\r\n','\n').trim()).digest('hex')
  );
  if(noticeHost||knownPlaylist)throw new SourceError(
    'المصدر أعاد مقطع رفض بدل الفيديو المطلوب. تعذر تشغيل المحتوى المطلوب.',
    403,'SOURCE_SESSION_REQUIRED'
  );
}
