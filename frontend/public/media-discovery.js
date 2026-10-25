/* Original, bounded media observation. Messages are untrusted candidates, never commands. */
(() => {
  if (window.__quannengMediaInstalled) return;
  window.__quannengMediaInstalled = true;
  const mediaPath = /\.(?:m3u8|mpd|mp4|webm|m4v|mov|mkv)(?:[?#]|$)|\/videoplayback(?:[/?]|$)|[?&]mime=video/i;
  const segment = /\.(?:ts|m4s|cmfv|cmfa)(?:[?#]|$)/i;
  const seen = new Set();
  const report = data => {
    try { window.QuannengMedia?.postMessage(JSON.stringify({...data, frame: location.href})); } catch {}
  };
  function candidate(value, type = '', direct = false) {
    if (!value || seen.size >= 100) return;
    let url; try { url = new URL(value, location.href); } catch { return; }
    if (url.protocol !== 'https:' || url.username || url.password || url.href.length > 8192 || segment.test(url.href)) return;
    const mime = type.toLowerCase().split(';')[0].trim();
    const playable = /^(?:video\/(?:mp4|webm|quicktime|x-m4v|x-matroska)|application\/(?:vnd\.apple\.mpegurl|x-mpegurl|dash\+xml))$/.test(mime);
    const name = url.pathname.split('/').pop().replace(/\.(mp4|m4v|webm|mkv|mov)$/i, '');
    if (!direct && (/^\d+$/.test(name) || /(?:^|[-_.])(?:init|dashinit|seg(?:ment)?|chunk|frag(?:ment)?)(?:[-_.]|[0-9]|$)/i.test(name))) return;
    if (!direct && !mediaPath.test(url.href) && !playable) return;
    if (seen.has(url.href)) return;
    seen.add(url.href); report({kind: 'source', url: url.href, mime});
  }
  function scan() {
    document.querySelectorAll('video').forEach(video => {
      candidate(video.currentSrc || video.src, '', true);
      video.querySelectorAll('source').forEach(source => candidate(source.src, source.type, true));
      if (!video.paused || video.readyState >= 2) report({kind: 'status', playing: !video.paused, blob: (video.currentSrc || video.src || '').startsWith('blob:'), encrypted: !!video.mediaKeys});
    });
    try { performance.getEntriesByType('resource').forEach(entry => candidate(entry.name)); } catch {}
  }
  if (window.fetch) {
    const original = window.fetch;
    window.fetch = function(...args) {
      const pending = original.apply(this, args);
      pending.then(response => {
        if (response.ok) candidate(response.url, response.headers.get('Content-Type') || '');
      }, () => {}).catch(() => {});
      return pending;
    };
  }
  const send = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function(...args) {
    this.addEventListener('load', () => {
      try { if (this.status >= 200 && this.status < 300) candidate(this.responseURL, this.getResponseHeader('Content-Type') || ''); } catch {}
    }, {once: true});
    return send.apply(this, args);
  };
  ['play', 'loadedmetadata', 'loadeddata', 'loadstart', 'encrypted'].forEach(event => document.addEventListener(event, e => {
    if (e.target?.tagName === 'VIDEO') {
      if (event === 'encrypted') report({kind: 'status', encrypted: true});
      scan();
    }
  }, true));
  try {
    const observer = new PerformanceObserver(list => list.getEntries().forEach(entry => candidate(entry.name)));
    observer.observe({type: 'resource', buffered: true});
  } catch {}
  try { window.QuannengMedia.onmessage = event => { if (event.data === 'scan') scan(); }; } catch {}
  document.addEventListener('DOMContentLoaded', scan, {once: true});
  scan();
})();
