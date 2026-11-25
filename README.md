# ARGUS FORGE — Android

Native Android port of the **Argus Forge** red-team workbench (the Windows
Tkinter tool), **Bruce-only**. Same dark red-team
skin and the same Repository system prompt. Built as a lean hybrid: a Java
shell hosts the UI in a full-screen `WebView`, and a JS bridge exposes native
networking / storage / clipboard. **Zero third-party dependencies** — pure
Android SDK.

## Features

| Area | Details |
|------|---------|
| Tabs | EVIL PORTAL · BAD USB · LIBRARY · STORE |
| LLM backend | any OpenAI-compatible `/v1/chat/completions` (native HTTP) |
| Repository system prompt | `assets/systemprompt.txt` (byte-identical to desktop) |
| Generate / Clear / Copy / Download | identical to the desktop tool |
| **My Vault** | save generated output per tab, persisted locally, view / copy / delete |
| Bruce deploy | ping → multipart `/api/upload` to `/evilportal/` / `/badusb/` |
| GitHub library | live seed repos + search + smart filters + multi-select download/deploy |
| **Store** | live app/theme list from `bruce.computer/appstore`, one-tap install to Bruce (`/BruceJS/<Category>/File.js`, `/Themes/<Name>/`) |
| Config | `SharedPreferences` (AI provider + GitHub + Bruce) |
| Splash | HTML5 canvas particle field (purple) |
| Theme | near-black `#0a0b0e`, crimson `#ff3344`, mono type |

## Architecture

```
app/src/main/
├── assets/
│   ├── systemprompt.txt        # Repository prompt (byte-identical to desktop)
│   └── www/
│       ├── index.html          # app shell + splash + 4 tabs + vault + store + settings
│       ├── app.css             # dark skin + responsive layout
│       ├── app.js              # UI controller + JS bridge client (+ DEMO mode)
│       └── logo.png            # ARGUS FORGE logo
├── java/com/argusforge/app/
│   ├── MainActivity.java       # WebView host, edge-to-edge dark chrome
│   ├── Bridge.java             # @JavascriptInterface → native ops (LLM/Bruce/vault/store/library)
│   ├── ApiClient.java          # HttpURLConnection: LLM / Bruce / GitHub / App Store
│   ├── ConfigStore.java        # SharedPreferences config (Bruce-only)
│   ├── VaultStore.java         # persisted "My Vault" entries
│   └── FileStore.java          # save artifacts to Downloads (MediaStore)
└── res/                        # dark theme, adaptive icon, network config
```

The UI talks to native code through `window.Argus` (async, callback-based via
`window.ArgusBridge.resolve`). When the bridge is absent (e.g. the bundled
`index.html` opened in a desktop browser) the UI auto-switches to an in-browser
**DEMO mode** — which is also how the preview screenshots were produced.

## Library filtering

- **Evil Portal** — only `.html`/`.htm` that are not README/license/docs.
- **Bad USB** — only actual DuckyScript payloads (`.duck`/`.ds`/`.badusb`/
  `.payload`, plus `.txt` files that are clearly payloads); READMEs, licenses,
  docs and config files are excluded.

## Store

The Store tab fetches the live app store data endpoint behind
`bruce.computer/appstore` (`App-Store-Data/releases/category-all.json`) and
separates apps (category ≠ `Themes`) from themes (category = `Themes`).
Install downloads the pinned files from the source repo and uploads them to the
connected Bruce device:

- Apps → `/BruceJS/<Category>/File.js`
- Themes → `/Themes/<ThemeName>/`

Network failures show a retry control.

## Build

```
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Requirements: JDK 17, Android SDK (compileSdk 35), Gradle 8.8 (wrapper included).

## Notes

- `minSdk 24`, `targetSdk 35`, `compileSdk 35`.
- Cleartext HTTP is permitted (`network_security_config.xml`) because Bruce
  devices and local LLM servers speak plain HTTP.
- Deploy calls to the Bruce device use a trust-all TLS context (it often ships
  a self-signed cert); LLM / GitHub / App Store calls use normal system TLS.
- Generated artifacts, downloaded payloads and store installs go through the
  Bruce web upload API; artifacts also land in the public **Downloads** folder
  (MediaStore on API 29+).

## Screenshots

`screenshots/` — phone (390×844 @2x) and tablet (820×1180 @2x) captures.
