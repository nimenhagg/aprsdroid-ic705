package org.aprsdroid.app;

import org.mapsforge.v3.android.maps.mapgenerator.tiledownloader.TileDownloader;
import org.mapsforge.v3.core.Tile;
import java.net.URI;

public class CustomTileDownloader extends TileDownloader {
    private final String hostName;
    private final String protocol;
    private final String pathTemplate;
    private final String subdomains;
    private static final byte ZOOM_MAX = 19;

    public CustomTileDownloader(String template, String subdomains) {
        this.subdomains = subdomains != null ? subdomains : "";
        setUserAgent("APRSdroid/1.5.4 (Android)");
        String tempHost = "webrd01.is.autonavi.com";
        String tempProto = "https";
        String tempPath = "/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x={x}&y={y}&z={z}";
        try {
            URI uri = URI.create(template);
            if (uri.getHost() != null) tempHost = uri.getHost();
            if (uri.getScheme() != null) tempProto = uri.getScheme();
            String rawPath = uri.getRawPath();
            String rawQuery = uri.getRawQuery();
            tempPath = (rawPath != null ? rawPath : "") + (rawQuery != null ? "?" + rawQuery : "");
        } catch (Exception ignored) {}
        this.hostName = tempHost;
        this.protocol = tempProto;
        this.pathTemplate = tempPath;
    }

    @Override
    public String getHostName() {
        return hostName;
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public String getTilePath(Tile tile) {
        String s = "";
        if (!subdomains.isEmpty()) {
            int idx = Math.floorMod(tile.tileX + tile.tileY, subdomains.length());
            s = String.valueOf(subdomains.charAt(idx));
        }
        return pathTemplate
                .replace("{s}", s)
                .replace("{x}", String.valueOf(tile.tileX))
                .replace("{y}", String.valueOf(tile.tileY))
                .replace("{z}", String.valueOf(tile.zoomLevel));
    }

    @Override
    public byte getZoomLevelMax() {
        return ZOOM_MAX;
    }
}
