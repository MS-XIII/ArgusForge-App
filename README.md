<div align="center">

# ARGUS FORGE

**Mobile red-team workbench for Android. Generate captive portals and BadUSB payloads from natural language, archive them in your Vault, and deploy payloads, apps, and themes to any Bruce-compatible device in one tap.**

[![Downloads](https://img.shields.io/github/downloads/MS-XIII/Argus-Forge/latest/total?label=downloads)](#)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84)](#)
[![Target](https://img.shields.io/badge/target-Bruce%20Firmware-ff3344)](#supported-deploy-targets)
[![License](https://img.shields.io/badge/license-AGPLv3-ff3344)](#license)

</div>

---

## Overview

**ARGUS FORGE** is a self-contained Android red-team toolkit that turns plain-text prompts into ready-to-deploy offensive artifacts. Built for field operators running **Bruce firmware** hardware (M5Stack, LilyGo, and ESP32-family boards), it combines AI payload generation, local artifact management, and a built-in device store in a single mobile interface.

Both generation surfaces—**Evil Portal** for captive-portal infrastructure and **Bad USB** for keystroke-injection payloads—sit on top of any OpenAI-compatible chat-completion endpoint (OpenAI, OpenRouter, Groq, Together, vLLM, llama.cpp, Ollama, or a self-hosted box). Every generation is routed through a fixed **Repository** system prompt with tab-scoped context injection for consistent, code-first output.

Generated scripts and portals can be saved directly to the on-device **Vault** for offline reuse, or pushed straight to your Bruce device over its HTTP interface with one tap. The integrated **Store** also lets you browse and install apps and themes directly onto your Bruce hardware from your phone.

---

## Features

- **Dual-surface AI generator** — Switch between `Evil Portal` and `Bad USB` inside one mobile workspace, sharing a single config and AI backend.

- **Pluggable LLM backend** — Connect to any endpoint speaking the OpenAI `/v1/chat/completions` spec. Set your `Base URL`, `API Key`, and `Model` in Settings—works with hosted providers or a self-hosted local server.

- **Repository system prompt** — Every request prepends a fixed operator doctrine for code-first, consistent output, paired with a tab-scoped context line so the model knows whether it is building portal HTML or DuckyScript.

- **Local Vault** — Save, organize, inspect, and redeploy your generated BadUSB scripts and Evil Portals directly on your Android device. Build your arsenal once and deploy it anytime—even without an active LLM connection.

- **Bruce Store** — Browse and install community apps and custom themes directly onto your connected Bruce firmware device with one tap.

- **One-tap Bruce deploy** — Authenticates against the Bruce WebUI and pushes artifacts to the correct on-device folder (`/evilportal/` for HTML portals, `/badusb/` for scripts) with timestamped filenames.

- **Dark red-team UI** — Near-black base (`#0a0b0e`), crimson accent (`#ff3344`), and mono typography designed for clean readability on mobile screens.

- **On-device config persistence** — API key, base URL, model, and Bruce device credentials are stored locally in app settings.

---

## Workspace Reference

Each section of the app is scoped to a specific part of the operator workflow.

| # | Section | Function | Output / Target | Bruce Path |
|---|---------|----------|-----------------|------------|
| 1 | **Evil Portal** | Generates captive-portal HTML/CSS/JS, login clones, and capture backends | `.html` artifact | `/evilportal/portal_<ts>.html` |
| 2 | **Bad USB** | Generates DuckyScript, Flipper `.badusb`, and companion PowerShell/bash stagers | `.txt` script | `/badusb/badusb_<ts>.txt` |
| 3 | **Vault** | Stores, manages, and redeploys saved Evil Portals and BadUSB scripts locally | Local archive | `/evilportal/`, `/badusb/` |
| 4 | **Store** | Browses and installs apps and themes directly to Bruce hardware | Apps & Themes | Bruce device storage |

---

## Supported Deploy Targets

ARGUS FORGE communicates directly with **Bruce firmware** devices over their built-in Wi-Fi web interface:

| Target | Auth | Transport | Paths |
|--------|------|-----------|-------|
| Bruce firmware devices — M5Stack Cardputer / StickC, LilyGo T-Embed / T-Deck, ESP32-S3 boards | Device WebUI auth | HTTP upload to the Bruce web server | `/evilportal/`, `/badusb/`, apps & themes |

Connect your Android device to the Bruce AP (or the same local network), and ARGUS FORGE handles authentication and file transfers automatically.

---

## Status Indicators

| Signal | Status | Description |
|--------|--------|-------------|
| ✅ | Active | Shipped, tested, and part of the current Android build. |
| 🧪 | Experimental | Works, but behaviour may change between builds. |
| 🚧 | Planned | Scoped, not yet implemented. See [Roadmap](#roadmap). |

**Feature status:**

| Feature | Status |
|---------|--------|
| Evil Portal & Bad USB AI generation | ✅ Active |
| OpenAI-compatible backend | ✅ Active |
| Repository system prompt + tab context | ✅ Active |
| Local Vault (scripts & portals) | ✅ Active |
| Bruce Store (apps & themes) | ✅ Active |
| One-tap Bruce device deploy | ✅ Active |
| Dark red-team mobile UI | ✅ Active |
| Multi-device deploy queue | 🚧 Planned |
| Additional surfaces (WiFi / BLE payloads) | 🚧 Planned |

---

## Quick Start

1. Download the latest `.apk` from [Releases](https://github.com/MS-XIII/Argus-Forge/releases) and install it on your Android device (enable *Install from unknown sources* if prompted).
2. Open **ARGUS FORGE** and go to **Settings**.
3. Configure your LLM backend:
   - **API Key** — your provider key (OpenAI, OpenRouter, Groq, etc.).
   - **Base URL** — e.g. `https://api.openai.com/v1`, `https://openrouter.ai/api/v1`, or your local server URL.
   - **Model** — e.g. `gpt-4o-mini`, `claude-sonnet-4`, `qwen2.5-coder-32b-instruct`.
4. Configure your Bruce device:
   - **Bruce Host** — default `http://bruce.local` or your device's IP.
   - **Bruce User / Pass** — your Bruce WebUI credentials (default `admin` / `bruce`).
5. Save settings, type a prompt in **Evil Portal** or **Bad USB**, hit **Generate**, save it to your **Vault**, or tap **Send to Bruce**.
6. Open the **Store** tab anytime to install new apps and themes onto your connected Bruce device.

---

## Roadmap

- [x] Android native/mobile port
- [x] Dual-surface AI generator (Evil Portal / Bad USB)
- [x] OpenAI-compatible backend
- [x] Repository system prompt + per-tab context injection
- [x] Local Vault for saving scripts and portals
- [x] Bruce Store for installing apps and themes
- [x] One-tap Bruce device deploy
- [x] Dark red-team UI
- [ ] Additional generation surfaces (BLE payloads, shell stagers)
- [ ] Streaming token output during generation
- [ ] Vault import/export & backup
- [ ] Multi-device deploy queue

---

## License

Copyright (c) 2026 MS-XIII

This project is licensed under the GNU Affero General Public License v3.0 (AGPLv3). See the [LICENSE](LICENSE) file for details.

---

<div align="center">

*Built for the operator who'd rather type a prompt than hand-write a DuckyScript.*

</div>
