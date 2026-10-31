/* Observe the video the user starts. Never enumerate preloads or performance resources. */
(() => {
  if (window.__quannengMediaInstalled) return;
  window.__quannengMediaInstalled = true;
  const mediaPath = /\.(?:m3u8|mpd|mp4|webm|m4v|mov|mkv)(?:[?#]|$)|\/videoplayback(?:[/?]|$)|[?&]mime=video/i;
  const fragment = /\.(?:ts|m4s|cmfv|cmfa)(?:[?#]|$)|(?:^|[\/_ .-])(?:init|dashinit|seg(?:ment)?|chunk|frag(?:ment)?)[_.\d-]/i;
  const blobs = new WeakMap(), objects = new Map(), recent = [];
  let gesture = 0, active = null, serial = 0, seen = new Set();
  const report = data => { try { window.QuannengMedia?.postMessage(JSON.stringify({...data, frame: location.href, session: String(serial)})); } catch {} };
  function title(video) {
    const episode = document.querySelector('[aria-current="true"], [aria-current="page"], .episode.active, .playlist .active');
    const name = video?.getAttribute?.('title') || document.querySelector('meta[property="og:title"]')?.content || document.title || location.hostname;
    const part = episode?.textContent?.trim().replace(/\s+/g, ' ').slice(0, 60);
    return (name + (part && !name.includes(part) ? ' · ' + part : '')).slice(0, 180);
  }
  function sniffText(value) {
    const text = String(value || '').replace(/^\ufeff/, '').trim();
    if (/^#EXTM3U/i.test(text)) return 'application/vnd.apple.mpegurl';
    if (/^(?:<\?xml[^>]*>\s*)?(?:<!--[\s\S]*?-->\s*)*<(?:[\w.-]+:)?MPD(?:\s|>)/i.test(text)) return 'application/dash+xml';
    return '';
  }
  function sniffBytes(value) {
    if (!value) return '';
    const bytes = value instanceof Uint8Array ? value : new Uint8Array(value);
    const length = Math.min(bytes.length, 2048);
    if (length >= 8 && bytes[4] === 0x66 && bytes[5] === 0x74 && bytes[6] === 0x79 && bytes[7] === 0x70) return 'video/mp4';
    if (length >= 4 && bytes[0] === 0x1a && bytes[1] === 0x45 && bytes[2] === 0xdf && bytes[3] === 0xa3) return 'video/webm';
    try {
      const text = typeof TextDecoder === 'function' ? new TextDecoder().decode(bytes.subarray(0, length)) : String.fromCharCode(...bytes.subarray(0, length));
      return sniffText(text);
    } catch { return ''; }
  }
  function needsSniff(url, type) {
    const normalized = String(type || '').toLowerCase().split(';')[0].trim();
    return !mediaPath.test(url) || !normalized || normalized === 'application/octet-stream' || normalized === 'binary/octet-stream' || normalized === 'text/plain';
  }
  function inspectResponse(result, info) {
    const fallback = () => response(info.url, info.type);
    if (!needsSniff(info.url, info.type) || !result?.clone) { fallback(); return; }
    try {
      const clone = result.clone();
      const reader = clone.body?.getReader?.();
      if (reader) {
        reader.read().then(part => {
          const detected = part?.value ? sniffBytes(part.value) : '';
          try { reader.cancel(); } catch {}
          response(info.url, detected || info.type);
        }, fallback);
      } else {
        clone.arrayBuffer().then(bytes => response(info.url, sniffBytes(bytes) || info.type), fallback);
      }
    } catch { fallback(); }
  }
  function candidate(value, type = '', direct = false) {
    if (!active || seen.size >= 12 || !value) return;
    let url; try { url = new URL(value, location.href); } catch { return; }
    if (url.protocol !== 'https:' || url.username || url.password || url.href.length > 8192 || fragment.test(url.pathname)) return;
    const mime = type.toLowerCase().split(';')[0].trim();
    if (!direct && !mediaPath.test(url.href) && !/^(video\/|application\/(vnd\.apple\.mpegurl|x-mpegurl|dash\+xml))/.test(mime)) return;
    if (seen.has(url.href)) return;
    seen.add(url.href);report({kind: 'source', url: url.href, mime, title: title(active), direct});
  }
  function scan() {
    if (!active && Date.now() - gesture <= 30000) {
      const playing = [...document.querySelectorAll('video')].find(video => !video.paused && (video.currentSrc || video.src));
      if (playing) { active = playing; serial++; seen = new Set(); report({kind: 'activate', title: title(active)}); }
    }
    if (!active) return;
    const src = active.currentSrc || active.src || '';
    const mapped = objects.get(src);
    if (mapped) candidate(mapped.url, mapped.type, true);
    else if (!src.startsWith('blob:')) candidate(src, active.querySelector?.('source')?.type || '', true);
    report({kind: 'status', playing: !active.paused, blob: src.startsWith('blob:'), encrypted: !!active.mediaKeys});
  }
  function response(url, type) {
    if (Date.now() - gesture > 30000) return;
    if (recent.length >= 12) recent.shift();recent.push({url, type});
    if (active && (active.currentSrc || active.src || '').startsWith('blob:') && !objects.has(active.currentSrc || active.src)) candidate(url, type);
  }
  const intent = event => { if (event.isTrusted) { gesture = Date.now();recent.length = 0; } };
  ['pointerdown', 'touchstart', 'keydown'].forEach(event => document.addEventListener(event, intent, true));
  document.addEventListener('play', event => {
    if (event.target?.tagName !== 'VIDEO' || Date.now() - gesture > 30000) return;
    active = event.target;serial++;seen = new Set();report({kind: 'activate', title: title(active)});
    scan();
    const src = active.currentSrc || active.src || '';
    if (src.startsWith('blob:') && !objects.has(src)) recent.forEach(value => candidate(value.url, value.type));
  }, true);
  ['loadedmetadata', 'playing', 'encrypted'].forEach(event => document.addEventListener(event, e => { if (e.target === active) {if (event === 'encrypted') report({kind: 'status', encrypted: true});scan();} }, true));
  if (window.fetch) {
    const original = window.fetch;
    window.fetch = function(...args) {
      const pending = original.apply(this, args);
      pending.then(result => {
        if (!result.ok) return;
        const info = {url: result.url, type: result.headers.get('Content-Type') || ''};inspectResponse(result, info);
        const blob = result.blob;
        result.blob = function(...values) {return blob.apply(this, values).then(value => {blobs.set(value, info);return value;});};
      }, () => {}).catch(() => {});
      return pending;
    };
  }
  const send = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function(...args) {
    this.addEventListener('load', () => {
      try {
        if (this.status < 200 || this.status >= 300) return;
        const info = {url: this.responseURL, type: this.getResponseHeader('Content-Type') || ''};
        const finish = detected => {info.type = detected || info.type;if (this.response && typeof this.response === 'object') blobs.set(this.response, info);response(info.url, info.type);};
        if (this.responseType === 'arraybuffer' && this.response) finish(sniffBytes(this.response));
        else if ((this.responseType === 'text' || this.responseType === '') && typeof this.responseText === 'string') finish(sniffText(this.responseText));
        else if (this.responseType === 'blob' && this.response?.slice) this.response.slice(0, 2048).arrayBuffer().then(bytes => finish(sniffBytes(bytes)), () => finish(''));
        else finish('');
      } catch {}
    }, {once: true});return send.apply(this, args);
  };
  const create = URL.createObjectURL;
  if (create) URL.createObjectURL = function(value) {const url = create.call(this, value), info = blobs.get(value);if (info) {if (objects.size >= 100) objects.delete(objects.keys().next().value);objects.set(url, info);}return url;};
  const revoke = URL.revokeObjectURL;
  if (revoke) URL.revokeObjectURL = function(value) {objects.delete(value);return revoke.call(this, value);};
  try {
    const observer = new MutationObserver(() => { if (active && Date.now() - gesture <= 30000) scan(); });
    const observeRoot = () => {
      const root = document.documentElement;
      if (root) observer.observe(root, {subtree: true, attributes: true, attributeFilter: ['src', 'type']});
      else setTimeout(observeRoot, 0);
    };
    observeRoot();
  } catch {}
  report({kind: 'ready'});
  try {window.QuannengMedia.onmessage = event => {if (event.data === 'gesture') {gesture = Date.now();recent.length = 0;}else if (event.data === 'scan') scan();else if (event.data === 'pause') document.querySelectorAll('video').forEach(video => video.pause());};} catch {}
})();
