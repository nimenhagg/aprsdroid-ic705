# APRSdroid Activities and UI Architecture

* **BaseRecyclerActivity** (AndroidX AppCompatActivity) -- permission handling and common APRS actions
  * **HubActivity** -- Compose `HubStationScreen` (station list, local position beaconing, tracking)
  * **LogActivity** -- Compose `LogScreen` (packet monitor, filtering, search, raw log export)
  * **ConversationsActivity** -- Compose `ConversationsScreen` (messaging conversations overview)
  * **MessageActivity** -- Compose `MessageChatScreen` (per-callsign direct messaging)
  * **StationActivity** -- Compose `StationDetailScreen` (callsign detail, packet history, digipeater path)
  * **PrefSymbolAct** -- Compose `SymbolPickerScreen` (APRS table & symbol picker)
  * **Ic705RxDiagnosticActivity** -- Compose `Ic705RxDiagnosticScreen` (IC-705 Wi-Fi diagnostics & event stream)
* **MapLoaderBase** (AndroidX AppCompatActivity) -- shared station loading for map screens
  * **MapAct** -- MapLibre Native online raster maps (AMap, OpenStreetMap, custom)
  * **GoogleMapAct** -- Google Maps SDK map and satellite modes
* **PrefsAct** (AndroidX PreferenceActivity) -- central preferences
* **ProfileImportActivity** (Android Activity) -- JSON profile file import
* **KeyfileImportActivity** (Android Activity) -- SSL key (.p12) file import
