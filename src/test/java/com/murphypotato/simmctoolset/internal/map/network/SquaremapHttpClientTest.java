package com.murphypotato.simmctoolset.internal.map.network;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SquaremapHttpClientTest {
    @Test
    void identityAndGzipResponsesAreDecoded() throws Exception {
        byte[] payload = "{\"markers\":[]}".getBytes(StandardCharsets.UTF_8);
        try (TestServer server = new TestServer((exchange, count) -> {
            byte[] response = payload;
            if (exchange.getRequestURI().getPath().endsWith("/gzip")) {
                exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                response = gzip(payload);
            }
            respond(exchange, 200, response);
        }); SquaremapHttpClient client = client(Duration.ofSeconds(2), 1024)) {
            HttpResult identity = client.get(server.uri("/identity"), HttpValidators.EMPTY).join();
            HttpResult gzip = client.get(server.uri("/gzip"), HttpValidators.EMPTY).join();
            assertEquals(HttpStatus.SUCCESS, identity.status());
            assertArrayEquals("{\"markers\":[]}".getBytes(StandardCharsets.UTF_8), identity.body());
            assertEquals(HttpStatus.SUCCESS, gzip.status());
            assertArrayEquals(identity.body(), gzip.body());
            assertEquals("gzip", server.lastEncoding);
        }
    }

    @Test
    void malformedGzipIsRetryableAndDecompressedLimitIsEnforced() throws Exception {
        try (TestServer server = new TestServer((exchange, count) -> {
            if (exchange.getRequestURI().getPath().endsWith("/bad")) {
                exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                respond(exchange, 200, new byte[]{1, 2, 3});
            } else {
                respond(exchange, 200, new byte[65]);
            }
        }); SquaremapHttpClient client = client(Duration.ofSeconds(2), 64)) {
            assertEquals(HttpStatus.RETRYABLE, client.get(server.uri("/bad"), HttpValidators.EMPTY).join().status());
            assertEquals(HttpStatus.TOO_LARGE, client.get(server.uri("/large"), HttpValidators.EMPTY).join().status());
        }
    }

    @Test
    void slowBodyNeedsLongMarkerDeadlineAndTimeoutPoliciesDoNotCoalesce() throws Exception {
        byte[] payload = "slow-marker".getBytes(StandardCharsets.UTF_8);
        try (TestServer server = new TestServer((exchange, count) -> {
            exchange.sendResponseHeaders(200, payload.length);
            try {
                Thread.sleep(250);
                exchange.getResponseBody().write(payload);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        }); SquaremapHttpClient client = client(Duration.ofMillis(100), 1024)) {
            HttpResult shortResult = client.get(server.uri("/slow"), HttpValidators.EMPTY).join();
            HttpResult longResult = client.get(server.uri("/slow"), HttpValidators.EMPTY, Duration.ofSeconds(2)).join();
            assertEquals(HttpStatus.TIMEOUT, shortResult.status());
            assertEquals(HttpStatus.SUCCESS, longResult.status());
            assertEquals(2, server.requests.get());
        }
    }

    @Test
    void etagRevalidationReturnsNotModified() throws Exception {
        byte[] payload = "ok".getBytes(StandardCharsets.UTF_8);
        try (TestServer server = new TestServer((exchange, count) -> {
            if ("v1".equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
                respond(exchange, 304, new byte[0]);
            } else {
                exchange.getResponseHeaders().set("ETag", "v1");
                respond(exchange, 200, payload);
            }
        }); SquaremapHttpClient client = client(Duration.ofSeconds(2), 1024)) {
            HttpResult first = client.get(server.uri("/etag"), HttpValidators.EMPTY).join();
            HttpResult second = client.get(server.uri("/etag"), new HttpValidators(first.validators().etag(), null)).join();
            assertEquals(HttpStatus.SUCCESS, first.status());
            assertEquals(HttpStatus.NOT_MODIFIED, second.status());
            assertEquals("v1", second.validators().etag());
        }
    }

    private static SquaremapHttpClient client(Duration timeout, long maxBody) {
        return new SquaremapHttpClient(HttpClient.newHttpClient(), timeout, maxBody, "test");
    }

    private static void respond(HttpExchange exchange, int status, byte[] bytes) throws IOException {
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static byte[] gzip(byte[] bytes) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(bytes);
        }
        return output.toByteArray();
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange, AtomicInteger count) throws Exception;
    }

    private static final class TestServer implements AutoCloseable {
        final HttpServer server;
        final AtomicInteger requests = new AtomicInteger();
        volatile String lastEncoding;

        TestServer(Handler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                requests.incrementAndGet();
                lastEncoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
                try { handler.handle(exchange, requests); }
                catch (Exception failure) { exchange.close(); }
            });
            server.start();
        }

        URI uri(String path) { return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path); }

        @Override public void close() { server.stop(0); }
    }
}
