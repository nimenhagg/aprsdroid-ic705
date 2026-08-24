# APRSdroid activities and inheritance

* **LoadingListActivity** (Android ListActivity) -- helper to show progress spinner while loading
  * **ConversationsActivity** -- shows all APRS message conversations
  * **MainListActivity** -- helper with start/stop action buttons at the bottom
    * **HubActivity** -- list of stations sorted by distance
    * **LogActivity** -- list of all packets sent and received
  * **StationHelper** -- helper for per-station menus
    * **MessageActivity** -- conversation with a callsign
    * **StationActivity** -- details of a callsign, list of all SSIDs, list of all packets
* **MapLoaderBase** (AndroidX AppCompatActivity) -- shared station loading for map screens
  * **MapAct** -- MapLibre Native online raster maps (AMap, OpenStreetMap, custom)
  * **GoogleMapAct** -- Google Maps SDK map and satellite modes
* **PrefsAct** (Android PreferenceActivity) -- central preferences 
* **PrefSymbolAct** (Android Activity) -- chooser for APRS symbol
* **ProfileImportActivity** (Android Activity) -- JSON profile file import
* **KeyfileImportActivity** (Android Activity) -- SSL key (.p12) file import
