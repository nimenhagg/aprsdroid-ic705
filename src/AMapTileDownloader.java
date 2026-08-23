package org.aprsdroid.app;

import org.mapsforge.v3.android.maps.mapgenerator.tiledownloader.TileDownloader;
import org.mapsforge.v3.core.Tile;

public class AMapTileDownloader extends TileDownloader {
    private static final String HOST_NAME = "webrd01.is.autonavi.com";
    private static final byte ZOOM_MAX = 18;
    private final StringBuilder stringBuilder = new StringBuilder();

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
        this.stringBuilder.setLength(0);
        this.stringBuilder.append("/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x=");
        this.stringBuilder.append(tile.tileX);
        this.stringBuilder.append("&y=");
        this.stringBuilder.append(tile.tileY);
        this.stringBuilder.append("&z=");
        this.stringBuilder.append(tile.zoomLevel);
        return this.stringBuilder.toString();
    }

    @Override
    public byte getZoomLevelMax() {
        return ZOOM_MAX;
    }
}
