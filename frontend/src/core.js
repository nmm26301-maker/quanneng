export function formatTime(value, precise = false) {
  const n = Number.isFinite(value) ? Math.max(0, value) : 0;
  const hours = Math.floor(n / 3600), minutes = Math.floor(n / 60) % 60;
  const seconds = Math.floor(n) % 60;
  const base = [hours, minutes, seconds].map(x => String(x).padStart(2, '0')).join(':');
  return precise && n % 1 > .001 ? base + (n % 1).toFixed(2).slice(1) : base;
}
export function parseTime(text) {
  const input = String(text).trim();
  if (!/^\d+(?::\d{1,2}){0,2}(?:\.\d{1,3})?$/.test(input)) return NaN;
  const parts = input.split(':').map(Number);
  if (parts.slice(1).some(x => x >= 60)) return NaN;
  return parts.reduce((a, b) => a * 60 + b, 0);
}
export function clampSeek(time, duration) {
  return Math.max(0, Math.min(time, Number.isFinite(duration) ? duration : Infinity));
}
export function validAB(a, b, duration) {
  return Number.isFinite(a) && Number.isFinite(b) && a >= 0 && b > a && Number.isFinite(duration) && b <= duration;
}
export function normalizeUrl(text) {
  const url = new URL(text.trim());
  if (url.protocol !== 'https:') throw new Error('请使用 HTTPS 视频直链');
  if (url.username || url.password) throw new Error('不支持带用户名或密码的链接');
  return url.href;
}
export function srtToVtt(text) {
  const clean = text.replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n');
  return clean.startsWith('WEBVTT') ? clean : 'WEBVTT\n\n' + clean.replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, '$1.$2');
}
