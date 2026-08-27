/*
 * HTTP design informed by JR1258/EarthMC-Map-Addon, upstream commit
 * c85c5003855eb47868868b931624b951cffba74e, Apache-2.0.
 * Reimplemented for bounded asynchronous SIMMC conditional requests and request coalescing.
 */
package com.murphypotato.simmctoolset.internal.map.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class SquaremapHttpClient implements AutoCloseable {
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();
    private final HttpClient client;
    private final Duration requestTimeout;
    private final long maxBodyBytes;
    private final String userAgent;
    private final ExecutorService bodyExecutor;
    private final ScheduledExecutorService timeoutExecutor;
    private final Object lifecycleLock = new Object();
    private final Map<RequestKey, InFlight> inflight = new HashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public SquaremapHttpClient(HttpClient client, Duration requestTimeout, long maxBodyBytes, String userAgent) {
        this.client = Objects.requireNonNull(client, "client");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (requestTimeout.isZero() || requestTimeout.isNegative() || maxBodyBytes < 1) {
            throw new IllegalArgumentException("timeouts and limits must be positive");
        }
        this.maxBodyBytes = maxBodyBytes;
        this.userAgent = Objects.requireNonNull(userAgent, "userAgent");
        this.bodyExecutor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(32), runnable -> daemon(runnable, "simmc-http-body-"),
                new ThreadPoolExecutor.AbortPolicy());
        this.timeoutExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                runnable -> daemon(runnable, "simmc-http-timeout-"));
    }

    public CompletableFuture<HttpResult> get(URI uri, HttpValidators validators) {
        return get(uri, validators, requestTimeout);
    }

    public CompletableFuture<HttpResult> get(URI uri, HttpValidators validators, Duration timeout) {
        validateUri(uri);
        Objects.requireNonNull(validators, "validators");
        validateTimeout(timeout);
        RequestKey key = new RequestKey(uri.normalize(), validators, timeout);
        InFlight flight;
        boolean start;
        CompletableFuture<HttpResult> view = new CompletableFuture<>();
        synchronized (lifecycleLock) {
            if (closed.get()) throw new IllegalStateException("HTTP client is closed");
            flight = inflight.get(key);
            start = flight == null;
            if (start) {
                flight = new InFlight(key, validators);
                inflight.put(key, flight);
            }
            flight.subscribers.add(view);
        }
        InFlight subscribed = flight;
        view.whenComplete((ignored, failure) -> {
            if (view.isCancelled()) unsubscribe(subscribed, view);
        });
        if (start) start(flight, uri);
        return view;
    }

    private void unsubscribe(InFlight flight, CompletableFuture<HttpResult> view) {
        boolean abort = false;
        synchronized (lifecycleLock) {
            if (!flight.finished && flight.subscribers.remove(view) && flight.subscribers.isEmpty()) {
                flight.finished = true;
                inflight.remove(flight.key, flight);
                abort = true;
            }
        }
        if (abort) flight.cleanup(true);
    }

    private void start(InFlight flight, URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(flight.key.timeout()).header("User-Agent", userAgent)
                .header("Accept", "application/json,image/png")
                .header("Accept-Encoding", "gzip").GET();
        if (flight.validators.etag() != null && !flight.validators.etag().isBlank()) {
            builder.header("If-None-Match", flight.validators.etag());
        }
        if (flight.validators.lastModified() != null && !flight.validators.lastModified().isBlank()) {
            builder.header("If-Modified-Since", flight.validators.lastModified());
        }
        CompletableFuture<HttpResponse<InputStream>> transport;
        try {
            synchronized (flight) {
                if (flight.stopped) return;
                transport = client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
                flight.transport = transport;
                flight.timeout = timeoutExecutor.schedule(
                        () -> finish(flight, new HttpResult(HttpStatus.TIMEOUT, null, flight.validators, null), true),
                        flight.key.timeout().toNanos(), TimeUnit.NANOSECONDS);
            }
        } catch (RuntimeException startFailure) {
            finish(flight, new HttpResult(networkFailure(startFailure), null, flight.validators, null), true);
            return;
        }
        transport.whenComplete((response, failure) -> responseArrived(flight, response, failure));
    }

    private void responseArrived(InFlight flight, HttpResponse<InputStream> response, Throwable failure) {
        HttpResult immediate = null;
        synchronized (flight) {
            if (flight.stopped) {
                if (response != null) closeQuietly(response.body());
                return;
            }
            if (failure != null) {
                immediate = new HttpResult(networkFailure(failure), null, flight.validators, null);
            } else {
                flight.body.set(response.body());
                try {
                    flight.bodyTask = bodyExecutor.submit(() ->
                            finish(flight, consume(response, flight.validators), false));
                } catch (RejectedExecutionException saturated) {
                    immediate = new HttpResult(HttpStatus.RETRYABLE, null, flight.validators, null);
                }
            }
        }
        if (immediate != null) finish(flight, immediate, true);
    }

    /** Removes the matching registry entry and cleans resources before publishing to any caller view. */
    private void finish(InFlight flight, HttpResult value, boolean interrupt) {
        List<CompletableFuture<HttpResult>> subscribers;
        synchronized (lifecycleLock) {
            if (flight.finished) return;
            flight.finished = true;
            inflight.remove(flight.key, flight);
            subscribers = List.copyOf(flight.subscribers);
            flight.subscribers.clear();
        }
        flight.cleanup(interrupt);
        subscribers.forEach(view -> view.complete(value));
    }

    private HttpResult consume(HttpResponse<InputStream> response, HttpValidators previous) {
        try (InputStream body = response.body()) {
            int code = response.statusCode();
            HttpStatus status = switch (code) {
                case 200 -> HttpStatus.SUCCESS;
                case 304 -> HttpStatus.NOT_MODIFIED;
                case 404 -> HttpStatus.NOT_FOUND;
                case 408, 425, 429 -> HttpStatus.RETRYABLE;
                default -> code >= 500 && code <= 599 ? HttpStatus.RETRYABLE : HttpStatus.FAILED;
            };
            byte[] bytes = status == HttpStatus.SUCCESS ? readBounded(decodedBody(body, response)) : null;
            HttpValidators received = switch (status) {
                case SUCCESS -> new HttpValidators(
                        response.headers().firstValue("ETag").orElse(null),
                        response.headers().firstValue("Last-Modified").orElse(null));
                case NOT_MODIFIED -> new HttpValidators(
                        response.headers().firstValue("ETag").orElse(previous.etag()),
                        response.headers().firstValue("Last-Modified").orElse(previous.lastModified()));
                default -> previous;
            };
            return new HttpResult(status, status == HttpStatus.SUCCESS ? bytes : null, received,
                    response.headers().firstValue("Content-Type").orElse(null));
        } catch (BodyTooLargeException tooLarge) {
            return new HttpResult(HttpStatus.TOO_LARGE, null, previous, null);
        } catch (IOException unreadable) {
            return new HttpResult(HttpStatus.RETRYABLE, null, previous, null);
        }
    }

    private static InputStream decodedBody(InputStream body, HttpResponse<InputStream> response) throws IOException {
        String encoding = response.headers().firstValue("Content-Encoding").orElse("");
        for (String token : encoding.split(",")) {
            if (token.trim().equalsIgnoreCase("gzip")) return new GZIPInputStream(body);
        }
        return body;
    }

    private byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        for (int read; (read = input.read(buffer)) != -1;) {
            total += read;
            if (total > maxBodyBytes) throw new BodyTooLargeException();
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void validateUri(URI uri) {
        Objects.requireNonNull(uri, "uri");
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
            throw new IllegalArgumentException("Only absolute HTTP(S) URIs are supported");
        }
    }

    private static void validateTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
    }

    private static HttpStatus networkFailure(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause instanceof HttpTimeoutException ? HttpStatus.TIMEOUT
                : cause instanceof IOException || cause instanceof InterruptedException || cause instanceof CancellationException
                ? HttpStatus.RETRYABLE : HttpStatus.FAILED;
    }

    @Override
    public void close() {
        List<InFlight> flights;
        List<CompletableFuture<HttpResult>> subscribers = new ArrayList<>();
        synchronized (lifecycleLock) {
            if (!closed.compareAndSet(false, true)) return;
            flights = new ArrayList<>(inflight.values());
            inflight.clear();
            for (InFlight flight : flights) {
                flight.finished = true;
                subscribers.addAll(flight.subscribers);
                flight.subscribers.clear();
            }
        }
        flights.forEach(flight -> flight.cleanup(true));
        subscribers.forEach(view -> view.cancel(true));
        timeoutExecutor.shutdownNow();
        bodyExecutor.shutdownNow();
    }

    private static Thread daemon(Runnable runnable, String prefix) {
        Thread thread = new Thread(runnable, prefix + THREAD_SEQUENCE.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }

    private static void closeQuietly(InputStream stream) {
        if (stream == null) return;
        try { stream.close(); } catch (IOException ignored) { }
    }

    private record RequestKey(URI uri, HttpValidators validators, Duration timeout) {}

    private static final class BodyTooLargeException extends IOException {
        private BodyTooLargeException() { super("response body exceeds configured limit"); }
    }

    private static final class InFlight {
        final RequestKey key;
        final HttpValidators validators;
        final ArrayList<CompletableFuture<HttpResult>> subscribers = new ArrayList<>();
        final AtomicReference<InputStream> body = new AtomicReference<>();
        boolean finished;
        boolean stopped;
        CompletableFuture<HttpResponse<InputStream>> transport;
        Future<?> bodyTask;
        ScheduledFuture<?> timeout;

        InFlight(RequestKey key, HttpValidators validators) {
            this.key = key;
            this.validators = validators;
        }

        synchronized void cleanup(boolean interrupt) {
            if (stopped) return;
            stopped = true;
            ScheduledFuture<?> deadline = timeout;
            timeout = null;
            if (deadline != null) deadline.cancel(false);
            CompletableFuture<?> request = transport;
            transport = null;
            if (interrupt && request != null) request.cancel(true);
            closeQuietly(body.getAndSet(null));
            Future<?> reader = bodyTask;
            bodyTask = null;
            if (interrupt && reader != null) reader.cancel(true);
        }
    }
}
