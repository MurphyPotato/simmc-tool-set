/*
 * Derived from JR1258/EarthMC-Map-Addon, Apache-2.0.
 * Upstream: src/main/java/net/townymap/render/SquaremapTileRenderer.java
 * Commit: c85c5003855eb47868868b931624b951cffba74e
 * Modified for validated SIMMC connection profiles, dynamic worlds, bounded shared HTTP,
 * and removal of EarthMC/Towny/fixed-host behavior.
 */
package com.murphypotato.simmctoolset.internal.map.network;

import com.murphypotato.simmctoolset.internal.map.config.ConnectionProfile;
import com.murphypotato.simmctoolset.internal.map.render.TileFetchResult;
import com.murphypotato.simmctoolset.internal.map.render.TileKey;
import com.murphypotato.simmctoolset.internal.map.render.TileSource;
import com.murphypotato.simmctoolset.internal.map.render.TileValidators;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public final class HttpTileSource implements TileSource {
    private static final Pattern SAFE_WORLD = Pattern.compile("[A-Za-z0-9_.-]{1,128}");
    private final ConnectionProfile profile;
    private final HttpGetter http;

    public HttpTileSource(ConnectionProfile profile, SquaremapHttpClient http) {
        this(profile, Objects.requireNonNull(http, "http")::get);
    }

    public HttpTileSource(ConnectionProfile profile, HttpGetter http) {
        this.profile = validated(profile);
        this.http = Objects.requireNonNull(http, "http");
    }

    @Override public TileFetchResult fetch(TileKey key) {
        return request(key, TileValidators.EMPTY);
    }

    @Override public TileFetchResult revalidate(TileKey key, TileValidators validators) {
        return request(key, Objects.requireNonNull(validators, "validators"));
    }

    private TileFetchResult request(TileKey key, TileValidators validators) {
        HttpResult result = http.get(tileUri(profile, key),
                new HttpValidators(validators.etag(), validators.lastModified())).join();
        TileValidators received = new TileValidators(result.validators().etag(), result.validators().lastModified());
        return switch (result.status()) {
            case SUCCESS -> TileFetchResult.found(result.body(), received);
            case NOT_MODIFIED -> TileFetchResult.notModified(received);
            case NOT_FOUND -> TileFetchResult.notFound(received);
            case RETRYABLE, TIMEOUT, TOO_LARGE, FAILED -> TileFetchResult.failed(received);
        };
    }

    public static URI tileUri(ConnectionProfile profile, TileKey key) {
        ConnectionProfile safe = validated(profile);
        Objects.requireNonNull(key, "key");
        if (!safe.worldKey().equals(key.worldKey())) throw new IllegalArgumentException("tile world does not match profile");
        URI base = URI.create(safe.baseUrl());
        String baseText = base.toString();
        if (!baseText.endsWith("/")) baseText += "/";
        return URI.create(baseText).resolve("tiles/" + safe.worldKey() + "/" + key.zoom()
                + "/" + key.x() + "_" + key.z() + ".png");
    }

    private static ConnectionProfile validated(ConnectionProfile profile) {
        Objects.requireNonNull(profile, "profile");
        if (!SAFE_WORLD.matcher(Objects.requireNonNull(profile.worldKey(), "worldKey")).matches()) {
            throw new IllegalArgumentException("unsafe squaremap world key");
        }
        URI base = URI.create(Objects.requireNonNull(profile.baseUrl(), "baseUrl"));
        String scheme = base.getScheme() == null ? "" : base.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https")) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalArgumentException("invalid squaremap base URL");
        }
        return profile;
    }

    @FunctionalInterface
    public interface HttpGetter {
        CompletableFuture<HttpResult> get(URI uri, HttpValidators validators);
    }
}
