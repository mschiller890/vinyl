# vinyl

> Control your music from Minecraft.

`vinyl` is a Fabric mod for Minecraft 26.2 that surfaces the song currently
playing on your Windows machine directly inside the game — on a small HUD in
the corner of your screen and as previous / play-pause / next buttons on the
inventory screen. It reads live metadata from whatever app is producing sound
(Spotify, YouTube Music, Chrome, Edge, the Windows Media Player… anything the
OS knows about) and lets you control it without alt-tabbing away.

<img width="282" height="145" alt="image" src="https://github.com/user-attachments/assets/ef78bbc6-114f-44af-b615-5c5416820b88" />

## Features

- **Now-playing HUD** — a compact, scaled text overlay in the top-left of the
  screen showing the song title and artist, updated live as tracks change.
- **Inventory controls** — previous, play/pause and next buttons rendered on
  both the survival and creative inventory screens. Click them to control the
  active media session.
- **Real Windows integration** — uses the official
  [Global System Media Transport Controls](https://learn.microsoft.com/en-us/windows/uwp/audio-video-camera/system-media-transport-controls)
  (GSMTC) API to read metadata and send transport commands. No browser
  automation, no memory scraping, no third-party extensions.
- **Graceful fallback** — if the GSMTC transport can't start (e.g. on a system
  without the required Windows runtime, or while running outside Windows), the
  buttons fall back to sending global media key events so playback control
  still works, just without metadata.
- **Zero-config** — JNA is bundled inside the mod jar, so there's nothing extra
  to install. Drop the mod in your `mods` folder and play.

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | ≥ 0.19.3 |
| Fabric API | any |
| Java | ≥ 25 |
| OS | Windows 10 1903+ or Windows 11 (for metadata; media-key fallback works anywhere) |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/) and
   [Fabric API](https://modrinth.com/mod/fabric-api).
2. Download the latest `vinyl` release `.jar` from the
   [releases page](https://github.com/mschiller890/vinyl/releases).
3. Drop it into your `.minecraft/mods` folder.
4. Launch the game with the Fabric profile.

That's it — start playing music in any supported app and the HUD will pick it
up automatically.

## How it works

`vinyl` keeps a long-lived PowerShell host process running in the background.
On startup the host loads the `Windows.Media.Control` WinRT projection,
requests a `GlobalSystemMediaTransportControlsSessionManager`, and signals
readiness back to the Java side over stdout. A daemon thread then polls the
current session roughly twice a second and pushes metadata updates to the HUD
on the Minecraft client thread.

Transport commands (previous / play-pause / next) from the inventory buttons
are sent through the same host to the active GSMTC session. When the host can't
start, the controller degrades to sending Windows media key events via JNA's
`user32` bindings.

### Why PowerShell?

The GSMTC API is a WinRT type — there's no tidy JNA vtable for it and a full
WinRT projection layer would balloon the mod's footprint. Shelling out to a
tiny PowerShell snippet that uses the built-in .NET `Windows.Media.Control`
projection keeps the mod dependency-light while still using the official,
supported Windows API. The init script is delivered via `-EncodedCommand`
(base64, UTF-16LE) so it survives any quoting edge cases, and the command loop
runs over stdin/stdout so we only spawn one process for the lifetime of the
game session.

## Building from source

```bash
git clone https://github.com/mschiller890/vinyl.git
cd vinyl
./gradlew build
```

The built jar will be in `build/libs/`.

## Screenshots

<p align="center">
  <img src="https://github.com/user-attachments/assets/ae7dafe4-54f3-41f8-8f7a-5f1b124d01d6" width="49%" alt="Screenshot 1">
  <img src="https://github.com/user-attachments/assets/d9b14bf4-166f-4fd5-9fa6-4c34f6ccd287" width="49%" alt="Screenshot 2">
</p>

## Compatibility

- Tested on Minecraft 26.2 with Fabric Loader 0.19.3 and Fabric API 0.154.2.
- The inventory-screen button injection works around the MC 26.2 retained-mode
  rendering pipeline (`extractRenderState` / `GuiGraphicsExtractor`), which no
  longer picks up widgets appended after `init()`. Buttons are owned, rendered
  in `ScreenEvents.afterExtract`, and receive clicks via `beforeTick`.
- On non-Windows systems the mod loads cleanly but shows "No song playing" and
  the buttons are inert.

## License

[CC0 1.0](./LICENSE) — public domain. Do whatever you like.
