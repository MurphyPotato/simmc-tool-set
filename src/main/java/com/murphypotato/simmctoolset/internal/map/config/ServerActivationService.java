package com.murphypotato.simmctoolset.internal.map.config;

import java.util.Locale;
import java.util.Optional;

public final class ServerActivationService {
    private static final ConnectionProfile DEFAULT_PROFILE = new ConnectionProfile(
            SimmcMapConfig.DEFAULT_MAP_URL,
            SimmcMapConfig.DEFAULT_WORLD_KEY
    );

    private final SimmcMapConfig config;

    public ServerActivationService(SimmcMapConfig config) {
        this.config = config;
    }

    public boolean isEnabledFor(String serverAddress) {
        return resolveFor(serverAddress).isPresent();
    }

    public Optional<ConnectionProfile> resolveFor(String serverAddress) {
        Optional<String> parsedHost = parseHost(serverAddress);
        if (parsedHost.isEmpty()) {
            return Optional.empty();
        }
        String host = parsedHost.orElseThrow();
        if (SimmcMapConfig.DEFAULT_SERVER_HOST.equals(host)) {
            return Optional.of(DEFAULT_PROFILE);
        }
        if (config.customServerEnabled() && config.customServerHost().equals(host)) {
            return Optional.of(new ConnectionProfile(config.customMapUrl(), config.customWorldKey()));
        }
        return Optional.empty();
    }

    private static Optional<String> parseHost(String serverAddress) {
        if (serverAddress == null || serverAddress.isBlank()) {
            return Optional.empty();
        }
        String address = serverAddress.trim();
        int firstColon = address.indexOf(':');
        String host = address;
        if (firstColon >= 0) {
            if (firstColon != address.lastIndexOf(':')) {
                return Optional.empty();
            }
            host = address.substring(0, firstColon);
            String port = address.substring(firstColon + 1);
            if (host.isEmpty() || !port.matches("[0-9]+")) {
                return Optional.empty();
            }
            try {
                int parsedPort = Integer.parseInt(port);
                if (parsedPort < 1 || parsedPort > 65535) {
                    return Optional.empty();
                }
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(host.toLowerCase(Locale.ROOT));
    }
}
