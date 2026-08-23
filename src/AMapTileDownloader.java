package org.aprsdroid.app;

import android.util.Log;
import org.mapsforge.v3.android.maps.mapgenerator.tiledownloader.TileDownloader;
import org.mapsforge.v3.core.Tile;

public class AMapTileDownloader extends TileDownloader {
    private static final String HOST_NAME = "webrd01.is.autonavi.com";
    private static final byte ZOOM_MAX = 18;

    public AMapTileDownloader() {
        setUserAgent("APRSdroid/1.5.4 (Android)");
    }

    @Override
    public String getHostName() {
        return HOST_NAME;
    }

    @Override
    public String getProtocol() {
        return "https";
    }

    @Override
    public String getTilePath(Tile tile) {
        String path = "/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x=" + tile.tileX + "&y=" + tile.tileY + "&z=" + tile.zoomLevel;
        Log.i("AMapTileDownloader", "Fetching: https://" + HOST_NAME + path);
        return path;
    }

    @Override
    public byte getZoomLevelMax() {
        return ZOOM_MAX;
    }
}
