# NumAI Plus

**English** / [Русский](README.ru.md)

NumAI Plus is a modern fork of [NumAI](https://github.com/gohoski/numAi).
The original project focuses on extreme legacy support for Android 1.0+.
This fork takes a different direction: a richer client, a cleaner UI, multi-chat workflow, and personalization features for older but still usable devices.

NumAI Plus is focused on **Android 4.0+**.



## Community

Telegram (RU): https://t.me/lev_landon
Twitter / X (EN): https://x.com/lev_landon



## Screenshots

<img src="img/scr1.jpg" width="300"/>
<img src="img/scr2.jpg" width="300"/>
<img src="img/scr3.jpg" width="300"/>

## What NumAI Plus Changes

- Modernized client based on NumAI
- Chat list with local history, search, rename, and delete
- Bubble-style controls and improved chat layout
- Per-user personalization: nickname, avatar, role, goals, style, detail level, emotionality
- Custom assistant instructions on top of the normal system prompt
- Better provider management for local and cloud APIs
- Separate chat and thinking model configuration

## Features

- Works with many **OpenAI-compatible APIs**
- Built-in provider presets: `VoidAI`, `Ollama`, `NavyAI`, `OpenRouter`, `Baseten`, `Gemini`, `Together`, `Upstage`, `LM Studio`
- Custom provider URL support
- Multi-chat storage with local SQLite history
- Chat search
- Thinking mode with separate reasoning model selection
- Streaming responses with automatic fallback if provider streaming is broken
- Vision support with image attachments
- Message actions: copy, select text, edit, regenerate, retry
- Text-to-speech for assistant replies
- Avatar picker and cropper
- English and Russian interface
- Provider diagnostics and connection checks

## Quick Start

1. Install APK from [Releases](https://github.com/levlandon/numAi-plus/releases).
2. Open **Settings**.
3. Choose a provider or enter a custom OpenAI-compatible endpoint.
4. Paste or import API key.
5. Load models and select chat / thinking models.
6. Start chatting.

## Recommended Use Cases

- Old phones and tablets that are too weak for modern heavy clients
- Local LLM access through `Ollama` or `LM Studio`
- Cloud LLM access through OpenAI-compatible providers
- Personal AI setup with profile-based behavior tuning

## Compatibility

- Upstream NumAI aims at Android `1.0+`
- NumAI Plus is focused on **Android 4.0+**
- Main target: old devices that still need a more modern UX than the upstream app

If you need maximum historical compatibility, check the original [NumAI repository](https://github.com/gohoski/numAi).
If you want chats, better controls, and personalization, use NumAI Plus.

## Build

Project uses the classic Android/Gradle app structure.

Build options:

- Android Studio
- `gradlew.bat assembleDebug`
- `gradlew.bat assembleRelease`

## Roadmap Direction

- Better multi-chat UX
- More provider presets
- More polish for old Android layouts
- Continued modernization without turning the app into a heavy client

## Bugs and Feedback

- Issues: [github.com/levlandon/numAi-plus/issues](https://github.com/levlandon/numAi-plus/issues)
- Upstream project: [github.com/gohoski/numAi](https://github.com/gohoski/numAi)

When reporting bugs, include:

- Android version
- Device model
- Provider name
- Selected model
- Whether streaming was enabled

## Credits

- Original project: [gohoski/numAi](https://github.com/gohoski/numAi)
- NNJSON: [shinovon/NNJSON](https://github.com/shinovon/NNJSON)
- This fork keeps building on the original legacy-focused idea, but moves the client toward a more modern experience

## License

The project inherits the original licensing structure:

- main project: [WTFPL v2](LICENSE)
- NNJSON: [MIT](LICENSE-NNJSON)
