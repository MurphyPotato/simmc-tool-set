package com.murphypotato.simmctoolset.internal.map.network;

import java.util.concurrent.CompletableFuture;

/** Explicit testable boundary for the three public squaremap resources. */
public interface SquaremapDataSource {
    CompletableFuture<HttpResult> fetchSettings(HttpValidators validators);
    CompletableFuture<HttpResult> fetchMarkers(HttpValidators validators);
    CompletableFuture<HttpResult> fetchPlayers(HttpValidators validators);
}
