# APRSdroid Activities and UI Architecture

Current release baseline: `Mod-v2.2.4`. See [AGENT.md](AGENT.md) for maintenance rules and [CHANGELOG.md](CHANGELOG.md) for release history.

## Main navigation

`HubActivity` hosts four peer Compose destinations in a single Navigation Compose graph. Root transitions do not animate the entire screen. `BaseRecyclerActivity` extends AndroidX `ComponentActivity` and supplies permissions and common APRS actions.

- **Stations — HubStationScreen:** tracking status, station list, and explicit position sending. A top-right magnifier expands a callsign/comment search field in the toolbar. Filtering runs before the 300-result limit and preserves station age and distance order.
- **Map — EmbeddedMapScreen:** embedded MapLibre for AMap / OpenStreetMap / custom raster tiles, or Google Maps normal / hybrid modes. MapView follows destination and host lifecycle; station snapshots and stable image/marker caches avoid rebuilding every icon on reception.
- **Messages — ConversationsScreen:** conversation overview and entry to per-callsign chat.
- **Packets — LogScreen:** packet monitor, filtering, search, and log export.

Hub refreshes the visible destination only. The map additionally reads the local station position. Station, map, and packet ViewModels are owned by the Activity ViewModelStore, and their 250 ms conflated query queues serialize database refreshes. Bitmap rasterization and GeoJSON preparation run off the UI thread; Map SDK calls remain on the UI thread.

## Secondary and compatibility activities

- **MessageActivity — MessageChatScreen:** per-callsign messaging.
- **StationActivity — StationDetailScreen:** callsign detail, packet history, and digipeater path.
- **PrefsAct:** `ComponentActivity` with Compose preference screens; no AndroidX PreferenceActivity.
- **PrefSymbolAct — SymbolPickerScreen:** APRS symbol selection.
- **Ic705RxDiagnosticActivity — Ic705RxDiagnosticScreen:** IC-705 receive diagnostics and event stream.
- **LogActivity / ConversationsActivity:** compatibility and secondary entry points; the main bottom tabs stay inside HubActivity.
- **MapAct / GoogleMapAct:** coordinate picking and compatibility map entry points, not the primary map tab. MapLoaderBase retains their shared loading behavior.
- **ProfileImportActivity / KeyfileImportActivity:** configuration JSON and PKCS#12 key import entry points.

Secondary activities use Android BackDispatcher / predictive back. Keep [AI_CONTEXT.md](AI_CONTEXT.md) as a pointer to AGENT.md rather than duplicating the maintenance specification.
