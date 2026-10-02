# APRSdroid Activities and UI Architecture

Current baseline: `Mod-v2.5.0`. This file describes the current Android navigation/activity structure; release history belongs in [CHANGELOG.md](CHANGELOG.md), maintenance invariants in [AGENT.md](AGENT.md), and stable implementation constraints in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Main navigation

`HubActivity` hosts the four primary Compose destinations in one Navigation Compose graph:

- **Stations — HubStationScreen:** station tracking/listing, explicit position sending, and callsign/comment search.
- **Map — EmbeddedMapScreen:** MapLibre for AMap/OpenStreetMap/custom raster sources, or Google Maps for Google modes.
- **Messages — ConversationsScreen:** conversation overview and entry to per-callsign chat.
- **Packets — LogScreen:** packet monitor, filtering, search, and log export.

The four root destinations are peers. Do not recreate the old full-screen XML navigation or add a second root navigation stack.

## Secondary activities

These remain explicit Android activity boundaries where platform Back / predictive back semantics are useful:

- `MessageActivity` — per-callsign chat;
- `StationActivity` — station details and packet history;
- `NotificationSettingsActivity` — notification settings;
- `PrefsAct` — Compose-based application settings;
- `PrefSymbolAct` — APRS symbol picker;
- `Ic705RxDiagnosticActivity` — IC-705 receive diagnostics;
- `MapAct` / `GoogleMapAct` — coordinate-picking and compatibility map entry points;
- `ProfileImportActivity` / `KeyfileImportActivity` — configuration and key-file import entry points.

`LogActivity` and `ConversationsActivity` remain compatibility/secondary entry points; the primary packet and message destinations are hosted by `HubActivity`.

## Navigation rules

- Toolbar Back and system/predictive Back must converge on the Android activity back dispatcher.
- Do not restore global `windowAnimationStyle` or a second Compose transition layer over secondary activities.
- Notification-to-chat navigation creates the required task stack directly; do not route through a Hub-side delayed navigation effect.
- System Settings intents are launched directly; application navigation must not wait for NotificationManager Binder work.
- Root destination switching must not animate the entire screen with cross-fade/slide/alpha effects.

## Performance ownership

- Database refreshes use the current ViewModel/repository boundaries and conflated query queues.
- Map rendering keeps immutable station snapshots and reuses marker/icon resources.
- MapLibre/Google Maps lifecycle follows the host destination/activity lifecycle.
- Visual navigation and predictive-back behavior require real-device verification; CI alone does not prove animation correctness.

For detailed state, networking, IC-705 session, PTT, diagnostics, and permission constraints, use [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
