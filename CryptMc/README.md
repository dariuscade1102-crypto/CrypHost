# CryptMc — local Minecraft server host, in Kotlin

A scaffold for an Anvil-MC/ARM-MC-style Android app: runs a real Paper/Fabric
server jar on-device, tunnels it to the internet with zero port forwarding,
auto-installs Geyser/Floodgate for Bedrock crossplay, and gives you a live
console + mod installer, all from a foreground service that survives
backgrounding.

## What's implemented here

| Requirement | File(s) |
|---|---|
| 1. Local self-hosting engine | `server/ServerProcessManager.kt`, `server/JreProvisioner.kt` |
| 2. Zero-port-forward tunnel | `tunnel/TunnelManager.kt` |
| 3. Java/Bedrock crossplay | `network/ModrinthRepository.kt` (`installBedrockCrossplay`) |
| 4. Management dashboard | `ui/DashboardScreen.kt`, `ui/DashboardViewModel.kt` |
| 5. Mod/plugin installer | `network/ModrinthApi.kt`, `network/ModrinthRepository.kt` |
| 6. Background execution | `service/ServerForegroundService.kt` |

This is a working architecture and compiles conceptually against the listed
Gradle dependencies, but it is **not a drop-in-and-ship APK** — three pieces
are binaries/assets too large and licensing-specific to generate here.

## Three things you must supply yourself

### 1. An embedded ARM JRE (required — nothing runs without this)
Android has no `java` binary. Server jars need a real JVM. Options, easiest
first:
- **Termux's `openjdk-17` package** — extract the `.deb` from
  `https://packages.termux.dev`, pull out `data/data/com.termux/files/usr`,
  zip it as `jre-arm64.zip`, drop into `app/src/main/assets/`.
- **Azul Zulu / BellSoft Liberica ARM builds** — both publish tar.gz JDKs for
  `linux-aarch64` that run fine under Android's Linux kernel via `exec()`,
  though you may need to patch a few `/proc` reads some JVMs do at startup
  (this varies by vendor/version — test on-device before shipping).

Either way: `JreProvisioner` expects `assets/jre-arm64.zip` (and optionally
`jre-armv7.zip`) containing a `bin/java` at its root.

### 2. The playit.gg agent binary (required for requirement #2)
Download the ARM64 build from `https://github.com/playit-cloud/playit-agent`
releases, rename it `libplayit_agent.so`, place under
`app/src/main/jniLibs/arm64-v8a/`. The `.so` extension and `jniLibs` location
are required by Android's APK packaging even though it's an ordinary
executable, not a shared library — this is the standard trick self-hosting
apps use to ship arbitrary ELF binaries.

Verify `TunnelManager`'s log-parsing regexes against whatever agent version
you actually bundle — the exact stdout wording isn't a stable API.

### 3. Server jars / Bedrock binary
PaperMC jars are fetched from `https://api.papermc.io` per version+build; add
a small `PaperDownloader` (same Retrofit + OkHttp pattern as
`ModrinthRepository`) if you want in-app "pick a version, download it" rather
than the user sideloading a jar manually. Bedrock Dedicated Server has no
public download API — Mojang requires clicking through their site — so that
path realistically needs the user to download it once and pick the file via
Storage Access Framework.

## Building, the Gradle wrapper, and signing

This checkout includes `gradlew`/`gradlew.bat` and
`gradle/wrapper/gradle-wrapper.properties` (pinned to Gradle 8.9, which
AGP 8.5.2 requires). **`gradle/wrapper/gradle-wrapper.jar` itself is not
included** — it's a binary that Gradle normally fetches for you, and this
scaffold was put together without network access to pull it down. Before
`./gradlew` will run, do one of:

- With Gradle already installed anywhere: `gradle wrapper --gradle-version 8.9`
  from the project root — this generates the jar in place.
- Without Gradle installed: download `gradle-8.9-bin.zip` from
  <https://gradle.org/releases/>, unzip it, run
  `<unzipped>/bin/gradle wrapper` from this project root once.
- Opening the project in Android Studio also offers to do this
  automatically the first time you sync.

**Signing.** `app/build.gradle.kts` has a `release` signing config wired up,
but it reads the keystore path and passwords from Gradle properties rather
than hardcoding them (never commit real signing credentials):

1. Run `keystore/generate-release-keystore.sh` — it calls `keytool` (ships
   with any JDK) to create `keystore/cryptmc-release.keystore.jks` using
   passwords *you* choose and that only you know.
2. Copy `gradle.properties.example` to `gradle.properties` (repo root,
   already gitignored) and fill in the real store/key passwords from step 1.
3. `./gradlew assembleRelease` now produces a properly signed release APK
   at `app/build/outputs/apk/release/`.

If `gradle.properties` isn't set up, release builds silently fall back to
the Android debug key so the build still succeeds — that output is
debug-signed and **not** valid for a Play Store upload. Building an actual
signed APK also requires the Android SDK/build-tools, which isn't
something this text-only scaffold can produce for you; run the two steps
above on a machine with Android Studio or the command-line SDK installed.

## Full app surface (this update)

The scaffold now mirrors the reference app's full navigation, not just a
single server's dashboard:

| Screen | File |
|---|---|
| Home (server list, tunnel selector) | `ui/home/HomeScreen.kt` |
| Guided Setup wizard (6 steps) | `ui/setup/GuidedSetupScreen.kt` |
| Server settings — General/Software/World/Mods & Plugins/Performance/Network | `ui/server/*.kt` |
| Console (live output, quick commands, player rail) | `ui/console/ConsoleScreen.kt` |
| Files | `ui/files/FilesScreen.kt` |
| Profile (Google Sign-In, plan tier) | `ui/profile/ProfileScreen.kt` |
| Navigation graph + bottom bar | `ui/nav/AppRoot.kt`, `ui/nav/Destinations.kt` |
| Shared server list/state | `data/ServerRepository.kt` |

`ServerRepository` is an in-memory singleton — every screen reads and
writes through it, which is why editing RAM in the Performance tab and
seeing it reflected on the Home card works without any manual wiring. Swap
its backing `MutableStateFlow<List<ServerConfig>>` for a Room DAO when you
need configs to survive a process death; nothing above it needs to change.

### Player management rail (Console screen)

Vanilla/Paper expose no structured player-roster API — the only way to know
who's online is parsing the `/list` command's own text response ("There are
2 of a max of 10 players online: Notch, Steve"). `ConsoleScreen.kt` does
exactly that (`parsePlayerList`) and surfaces the result as a rail with
kick/ban/whitelist per player: side-by-side with the console on wide
layouts (tablets, landscape), collapsed below it on phones. Re-issue `/list`
on an interval once this is wired to the real process (see the
`LaunchedEffect` comment in that file) so the roster doesn't go stale
between joins/leaves.

## Admin dashboard

Reached via the shield icon in Home's top bar (`ui/admin/AdminDashboardScreen.kt`).
Rolls up every server's live status (online/offline, player count) plus a
per-server collaborator list with three roles:

- **Owner** — implicit for whoever created the server; the only role that
  can remove other collaborators.
- **Admin** — full console + settings access, cannot remove the Owner.
- **Viewer** — read-only console, no settings access. (Enforcing the
  read-only part is a small gate to add in `ConsoleScreen.kt` — check the
  current user's role from `AdminRepository.collaboratorsFor(serverId)`
  before rendering the command input field/quick-command chips.)

Backed by `data/Collaborator.kt` + `data/AdminRepository.kt`, in-memory like
`ServerRepository`. One real limitation worth flagging: **"Invite by email"
currently only grants access in this device's local state.** For an invited
collaborator to actually see the server on *their* device, you need the
backend session layer mentioned in `GoogleAuthManager`'s class doc — some
server-side source of truth that maps "this Google-verified email" to
"these server IDs," which this device's local `AdminRepository` map can't
provide on its own. Local-only is fine for a single household sharing one
phone's app install; cross-device sharing needs that backend piece.

## Google Sign-In + password storage

- **`auth/GoogleAuthManager.kt`** — Sign in with Google via Credential
  Manager (the current, non-deprecated API; the old
  `GoogleSignInClient`/One Tap APIs are on their way out). You must:
  1. Create an OAuth 2.0 **Web application** client ID in Google Cloud
     Console (APIs & Services → Credentials) — yes, Web, not Android, even
     though this is an Android app; that's how the ID-token flow is
     designed.
  2. Also register an **Android** OAuth client in the same project, tied to
     this app's package name (`com.cryptmc.app`) and your signing
     certificate's SHA-1 fingerprint (`./gradlew signingReport` to get it).
  3. Replace `GOOGLE_WEB_CLIENT_ID` in `ProfileScreen.kt` with the Web
     client ID from step 1.
  - What you get back is an identity (email + a verifiable ID token), not
    an account system by itself. If servers/configs should sync across
    devices, that needs a backend that verifies the ID token server-side
    and issues its own session — this scaffold stops at "confirm who signed
    in," deliberately, since a backend is a separate piece of
    infrastructure this repo doesn't include.

- **`auth/SecureCredentialStore.kt`** — wraps `EncryptedSharedPreferences`
  (Android Keystore-backed AES-256) for any password this app stores
  locally. Currently used for the Network tab's RCON password field
  (`ui/server/NetworkTab.kt`) — deliberately kept **out** of `ServerConfig`,
  since that model isn't encrypted at rest. If you add more local secrets
  (an admin-panel password, a Discord bot webhook token), route them
  through this same store rather than adding fields to `ServerConfig`.

## Tablet / larger-screen support

- **ABIs**: `arm64-v8a` and `armeabi-v7a` cover phones and nearly all Android
  tablets. `x86_64` is now also built, for x86 tablets and Chromebooks in
  tablet mode — but remember each ABI needs its *own* embedded JRE zip and
  playit-agent binary (see the assets section above); adding the ABI to
  `build.gradle.kts` alone doesn't get you a working binary for it.
- **Layout**: `DashboardScreen` now branches on `WindowWidthSizeClass`
  (Compact vs Medium/Expanded) rather than checking device type — this is
  the correct Android-recommended signal, since a split-screened tablet or a
  phone in landscape can report a different class than the raw screen size
  would suggest. Wide layouts show the console and a player-management rail
  side-by-side; the rail is currently an empty slot (see the TODO comment in
  `DashboardScreen.kt`) since populating it needs a player-list data source
  (parsing `/list` output, or a small Paper plugin exposing an HTTP/RCON
  bridge) that isn't built yet.
- The manifest declares `configChanges` for orientation/screen size so
  rotating a device or unfolding a foldable mid-session re-triggers the
  layout switch live, instead of restarting the Activity (which would be
  jarring given the running server/console state this screen holds).

## New in this update

| Feature | File(s) |
|---|---|
| Manual + scheduled backups, retention, restore | `server/BackupManager.kt`, `ui/server/BackupsTab.kt` |
| Scheduled restarts, WorkManager wiring | `server/ServerScheduler.kt`, `ui/server/SchedulingTab.kt` |
| In-app Paper/Fabric/Vanilla jar downloader | `network/ServerJarApi.kt`, `network/ServerJarDownloader.kt` |
| Player playtime/session tracking | `data/PlayerStatsRepository.kt` |
| Discord chat/event relay (webhook, one-way) | `integrations/DiscordBridge.kt` |
| Desktop companion app (Win/macOS/Linux) | new `:desktop` Gradle module |

**Backups & scheduling.** `BackupsTab` and `SchedulingTab` are two new tabs
on the server settings screen. `ScheduleConfig` (in `data/ServerConfig.kt`)
holds the cadence; `ServerScheduler.reschedule()` turns that into WorkManager
periodic jobs and is called automatically from `ServerSettingsScreen`
whenever the schedule changes. `BackupManager` zips `world/` (+
`world_nether`/`world_the_end`, `server.properties`, optionally
`plugins`/`mods`) and prunes old *scheduled* backups down to the retention
count — manual backups are never auto-deleted. **Before wiring this to a
real running process:** issue `save-off` + `save-all` over the console pipe
and wait for the "Saved the game" line before zipping, and refuse
manual/scheduled backups while a restore for the same server is already
in flight. Both TODOs are marked inline in `ServerScheduler.BackupWorker`.

**Jar downloader.** `ServerJarDownloader` resolves and downloads Paper
(build API), Fabric (meta API), and Vanilla (Mojang manifest) server jars
for a given Minecraft version — the piece the original README flagged as
missing. NeoForge/Purpur/Bedrock still need manual sideloading (no simple
REST API on their end); wire this into `SoftwareTab.kt`'s version picker.

**Discord bridge.** Outbound-only by design — see the class doc on
`DiscordBridge.kt` for why two-way chat needs a bot process instead of
fitting into this app's lifecycle. Hook `relayChatMessage`/
`relayPlayerJoined`/`relayPlayerLeft` into whatever already parses
`ConsoleScreen.kt`'s log lines for the player roster (`parsePlayerList`) —
same regex matches feed `PlayerStatsRepository` too.

**Player stats.** `PlayerStatsRepository` derives per-player playtime from
join/leave events — feed it from the same place, then surface
`statsFor(serverId)` wherever you want a leaderboard (the Console screen's
player rail, or a new stats view).

## Desktop companion

New `:desktop` Gradle module (`./gradlew :desktop:run` for dev,
`:desktop:packageDistributionForCurrentOS` for a `.dmg`/`.msi`/`.deb` via
jpackage — which bundles its own JRE, so unlike Android there's no
ARM-JRE-asset step here). It's genuinely simpler than the phone app in one
respect: a desktop already has a real `java` on PATH/`JAVA_HOME`, so
`DesktopServerManager` just runs `ProcessBuilder` directly — no
`JreProvisioner`-style zip extraction, no per-ABI binaries.

**What it has:** server list, add-server dialog, start/stop, live console
with command input, multiple servers running concurrently (no foreground-
service memory pressure to dodge on desktop), and an AI Assistant panel
(see below).

**What it deliberately doesn't have yet:** the jar downloader, backups,
scheduling, Discord bridge, and mod installer from the Android app aren't
ported over — `:desktop` and `:app` don't share source. Doing that properly
means promoting `data/`, `server/`, and `network/` into a `:core` Kotlin
Multiplatform module (`commonMain`) that both platforms depend on, with
`expect`/`actual` splits for the genuinely Android-only pieces
(`WorkManager` in `ServerScheduler`, `EncryptedSharedPreferences` in
`SecureCredentialStore`). That's a real refactor worth doing deliberately,
not a can move silently — flagging it rather than skipping past it.

## AI Assistant (Android + desktop)

Both apps now have an in-app AI helper for setup and troubleshooting,
reachable from:

| Where | Android | Desktop |
|---|---|---|
| No server selected | Home top bar sparkle icon, Profile > AI Assistant row | "Ask AI" button on the empty detail pane |
| A specific server | Console/Server Settings top bar sparkle icon | "Ask AI" button next to Start/Stop |

| Piece | File(s) |
|---|---|
| Android chat screen | `ui/ai/AiAssistantScreen.kt` |
| Android cloud API client + prompt builder | `network/AiApi.kt`, `ai/AiAssistantRepository.kt` |
| Android on-device engine (downloadable model) | `ai/OnDeviceAiEngine.kt`, `network/ModelDownloader.kt` |
| Android key/mode storage | `auth/SecureCredentialStore.kt` (`setAnthropicApiKey`/`getAnthropicApiKey`, `setAiEngineMode`) |
| Desktop chat dialog | `desktop/Main.kt`'s `AiChatDialog` |
| Desktop API client + prompt builder | `desktop/ai/AiApi.kt`, `desktop/ai/AiAssistantManager.kt` |
| Desktop key storage | `desktop/ai/AiSettingsStore.kt` |

**Two ways to run the AI Assistant on Android now**, picked with a segmented
control at the top of Profile > AI Assistant:

- **Cloud API** (original) — calls Anthropic's public Messages API
  (`api.anthropic.com/v1/messages`) directly from the device, using an API
  key the user pastes in themselves (get one at console.anthropic.com) —
  there's no Anthropic-side proxy or CryptMc backend involved, and no key
  ships with the app.
- **On-device** (new) — runs a downloaded model entirely on-device via
  MediaPipe's LLM Inference API (`com.google.mediapipe:tasks-genai`), so
  chat messages never leave the phone and no key is needed. This needs a
  one-time model download: paste a URL to a MediaPipe `.task`-format model
  bundle (search "MediaPipe Gemma task file" — Google publishes ready-made
  Gemma 3 1B/2B builds on Kaggle/Hugging Face) into the download field.
  Same "you supply the binary" shape as the embedded JRE / playit-agent
  requirements above — the model itself (1-4 GB) isn't and can't be
  bundled with this scaffold. `OnDeviceAiEngine.kt`'s class doc has the
  details; `ModelDownloader.kt` handles the streamed download + progress.

If you'd rather users not need their own key *and* not need an on-device
download, put a small backend in front of the cloud call that holds a
shared key server-side instead; that's a different (and more involved)
architecture than either approach above.

**What makes it useful instead of generic chatbot trivia**: the request's
system prompt is built per-turn from the currently open server's real
config — loader, MC version, RAM, port, online-mode/whitelist, tunnel
mode, crossplay status, and (once wired to a live process — see the inline
TODOs) a tail of recent console output — so "why can't my friends join"
gets an answer grounded in *this* server's actual settings. See
`buildSystemPrompt` in either `AiAssistantRepository.kt` /
`AiAssistantManager.kt` for exactly what's sent.

**Storage honesty note:** Android's key is Keystore-encrypted via the same
`SecureCredentialStore` the RCON password uses. Desktop's is **not**
encrypted — `java.util.prefs.Preferences` (registry/plist/dotfile,
depending on OS) is used instead, since there's no single portable OS
keystore API callable from plain JVM code without adding a native
per-OS dependency. `AiSettingsStore.kt`'s class doc spells out the
options if you want to close that gap later.

**Model string.** Defaults to `claude-sonnet-5` in both clients — check
docs.claude.com for the current model lineup before shipping, since this
changes over time and isn't something this scaffold can keep in sync with.

## Local Hotspot Hosting (Android/tablet only)

A fourth tunnel mode, `TunnelMode.HOTSPOT`, alongside Local/playit.gg/
Cloudflare — turns the phone or tablet itself into the Wi-Fi access point
nearby players connect to, with **no router, guest network, or internet
connection required at all**. Useful anywhere there's no existing network
to join: a field, a road trip, a venue with no guest Wi-Fi.

| Piece | File(s) |
|---|---|
| LocalOnlyHotspot wrapper | `network/HotspotManager.kt` |
| UI (start/stop, SSID/password/IP, copy buttons) | `ui/server/NetworkTab.kt`'s "Local Hotspot Hosting" card |
| Permissions | `AndroidManifest.xml` (`ACCESS_FINE_LOCATION`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`) |

Built on `WifiManager.startLocalOnlyHotspot`, so it inherits that API's
platform constraints rather than working around them:

- **Runtime location permission is required to start it** — a platform
  rule for this specific API, not an CryptMc design choice; the app never
  reads GPS location itself. The Network tab requests it inline the first
  time you tap "Start Hotspot".
- **The app must stay foregrounded while the hotspot is meant to stay up**
  — Android tears down a `LocalOnlyHotspot` reservation automatically when
  the hosting app backgrounds. Unlike the server process itself (which
  survives backgrounding via `ServerForegroundService`), nothing in this
  scaffold works around that for the hotspot specifically — it's a real
  limitation worth testing against before you rely on it for a long play
  session, not something silently smoothed over here.
- **Only one reservation per app at a time**; `HotspotManager.start()` is a
  no-op if one's already active.
- The local IP shown next to "Direct Connect address" is a best-effort
  scan of non-loopback network interfaces (Android doesn't hand back the
  hotspot interface's IP directly) — on a device with more than one radio
  active at once it can occasionally pick up the wrong one; double-check
  against Settings > Wi-Fi > Hotspot details if a player reports it not
  working.
- **Not offered on desktop.** Windows/macOS/Linux don't expose one
  portable "become an access point" API the way `LocalOnlyHotspot` does,
  and a desktop is far more likely to already be on real Wi-Fi/Ethernet
  anyway, where the existing LOCAL_ONLY tunnel mode already covers
  same-network play.

## Design notes worth knowing before you extend this

- **Battery-optimization exemption.** The foreground service alone is not
  enough on MIUI/One UI/ColorOS-style OEM skins; you need a one-time runtime
  prompt (`Intent.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) or the OS
  will still kill the process under memory pressure. Not included in this
  scaffold — add a `BatteryOptimizationHelper` and call it from
  `MainActivity.onCreate`.
- **Storage permissions.** `MANAGE_EXTERNAL_STORAGE` in the manifest is a
  restricted permission on Play Store review; if you plan to publish (rather
  than sideload), keep everything in app-scoped storage (`filesDir`,
  `getExternalFilesDir`) instead and drop that permission entirely.
- **EULA.** `ServerProcessManager.ensureEula` only ever writes `eula=true` if
  your UI captured explicit consent — don't wire a "just make it work"
  shortcut that silently agrees to Mojang's EULA on the user's behalf.
- **iOS is not covered.** Apple's App Store sandboxing model prohibits
  running arbitrary downloaded/JIT-compiled binaries in a foreground app,
  which is exactly what this whole architecture depends on. A true iOS port
  isn't a porting exercise — it's not possible within App Store policy.
  Desktop (a Compose Multiplatform or Electron build) is the realistic
  cross-platform path if you want this off Android too.
