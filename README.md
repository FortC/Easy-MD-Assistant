# EasyMDAssistant

**[简体中文](README.zh-CN.md)** | English

An AI-powered Markdown assistant for IntelliJ IDEA: scan and classify every doc in your project, chat with any document as context, and generate project AI rules for coding agents — powered by OpenAI-compatible & Anthropic APIs.

![Tool Window](docs/screenshots/tool-window.png)

## Features

### 📂 Scan & Organize
- Tool window scans all `.md` / `.markdown` files in the project (skips `node_modules`, `build`, `.git`, etc.)
- Four grouping modes: **by directory / smart category / flat / by date**, double-click to open
- One-click filter for 30+ AI tool directories (`.codex`, `.claude`, `.cursor`, `.workbuddy`, `.continue`, `.windsurf`, `.gemini`, `.qwen`, `.kiro`, `.trae`, `.cline`, and more — configurable)
- Right-click a doc to classify it (physically moves it into a category folder), run AI actions, or start a Q&A with it; multi-select docs and export them as a ZIP that follows your current grouping

### 🏷️ Classification — 4 engines, layered by priority
1. **Manual folder mappings** — path prefix → category, longest prefix wins
2. **Knowledge-base dirs** — docs inside a knowledge dir are grouped automatically
3. **AI classification** — batch (toolbar), single file (right-click), or conversational; candidates come from your category library + knowledge dirs
4. **Built-in keyword rules** — 90+ heuristic rules, works fully offline

The global category library (10 built-in categories) is editable in Settings.

### 📚 Knowledge bases → project AI rules
Four knowledge roles (requirement / example / result / error) whose directories you pick from the category library. The toolbar **Sync AI Rules** button writes these conventions into a project rules file (`AGENTS.md` by default, `CLAUDE.md` etc. supported) using non-destructive marked blocks — Codex / Claude Code / Cursor will then follow them: read requirements first, prefer examples, archive outputs, avoid recorded mistakes.

### 💬 AI chat & actions
- Multi-turn chat with **SSE streaming output**, any document selectable as context (up to 15,000 chars injected), Ctrl+Enter to send, stop anytime
- One-click AI actions: **summarize / translate / polish / outline / extract TODOs** — copy the result, append it to the source doc, or save it as a new file

### 🔌 Dual-protocol providers
| Provider | Protocol | Base URL example | Model example |
|---|---|---|---|
| OpenAI | OpenAI-compatible | `https://api.openai.com` | `gpt-4o-mini` |
| DeepSeek | OpenAI-compatible | `https://api.deepseek.com` | `deepseek-chat` |
| Zhipu GLM | OpenAI-compatible | `https://open.bigmodel.cn/api/paas/v4` | `glm-4.6` |
| MiniMax | Anthropic | `https://api.minimax.cn/anthropic` | `MiniMax-M3` |
| Anthropic | Anthropic | `https://api.anthropic.com` | `claude-sonnet-4-5` |

![Settings](docs/screenshots/settings.png)

### 🌐 Bilingual UI
Settings → Tools → **EasyMDAssistant AI** → UI language: Follow IDE / 简体中文 / English.

## Installation

Compatible with **IntelliJ IDEA 2021.1+** (Community & Ultimate, pure Java, zero third-party runtime dependencies).

- **JetBrains Marketplace** — listing in review
- **From disk**: `Settings → Plugins → ⚙ → Install Plugin from Disk…` → select the released zip → restart

## Quick Start

1. Open any project, click the **EasyMDAssistant** tool window on the left
2. `Settings → Tools → EasyMDAssistant AI` — configure provider / API key / model, click **Test Connection** to verify
3. (Optional) configure knowledge dirs, AI rules file, folder mappings
4. Toolbar: rescan · smart classify · AI-docs filter toggle · sync AI rules · settings
5. Right-click a doc: open / classify / AI actions / ask about this doc

## Build from Source

Requires JDK 17+ (only for running Gradle; the artifact targets Java 11 bytecode).

```bash
./gradlew buildPlugin        # output: build/distributions/*.zip
./gradlew runIde             # run in a sandbox IDE
./gradlew runPluginVerifier  # compatibility check (IC-2021.1 / IC-2024.2.1)
```

The first build downloads the IntelliJ 2021.1 SDK (~700 MB). The Gradle distribution uses the Tencent mirror.

## Known Limitations

- Proxy: uses the IDE HTTP Proxy settings first (PAC supported, resolved off the UI thread with a 5s cap), then `HTTPS_PROXY` / `HTTP_PROXY` / `ALL_PROXY` env vars, then direct. Authenticated proxies (407) are not supported yet
- Chat context truncated to 15,000 chars; AI action docs to 20,000 chars
- Max 5,000 docs per project; max 30 lessons summarized into AI rules
- OpenAI protocol omits `max_tokens` automatically for `o1` / `o3` / `gpt-5*` models

## License

[Apache-2.0](LICENSE)
