package com.zappic3.mediachat;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.zappic3.mediachat.MediaChat.CONFIG;
import static com.zappic3.mediachat.MediaChat.LOGGER;

// todo add a confic option to set the locale / use player language setting
public class TenorService {
    private final static String _clientKey = MinecraftClient.getInstance().player.getUuidAsString();
    private static CategoryResponse _cachedCategoryResponse = null;
    private static long _cachedCategoryResponseMaxAge = 0;

    private TenorService() {}

    public static CompletableFuture<TenorQueryResponse<Iterator<Category>>> getFeatured() {
        // return cached result if it's still valid
        if (_cachedCategoryResponseMaxAge >= System.currentTimeMillis() && _cachedCategoryResponse != null) {
            return CompletableFuture.supplyAsync(() -> new TenorQueryResponse<>(_cachedCategoryResponse.tags.iterator()));
        }
        return CompletableFuture.supplyAsync(() -> {
            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://tenor.googleapis.com/v2/categories?key=%s&client_key=%s&locale=%s"
                                .formatted(CONFIG.tenorApiKey(), _clientKey, MinecraftClient.getInstance().options.language)))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Gson gson = new Gson();
                    CategoryResponse categoryResponse = gson.fromJson(response.body(), CategoryResponse.class);

                    _cachedCategoryResponse = categoryResponse;
                    Optional<String> cacheControlHeader = response.headers().firstValue("cache-control");
                    cacheControlHeader.ifPresent(cacheControl -> {
                        String[] directives = cacheControl.split(",");
                        for (String directive : directives) {
                            if (directive.startsWith("max-age=")) {
                                String maxAgeValue = directive.substring(8);
                                _cachedCategoryResponseMaxAge = System.currentTimeMillis() + (Long.parseLong(maxAgeValue) * 1000);
                            }
                        }
                    });

                    if (_cachedCategoryResponseMaxAge < System.currentTimeMillis()) {
                        _cachedCategoryResponseMaxAge = System.currentTimeMillis()  + (600 * 1000);
                    }


                    return new TenorQueryResponse<>(categoryResponse.tags.iterator());
                } else if (response.statusCode() == 403) {
                    return new TenorQueryResponse<>(connectionStatus.INVALID_API_KEY);
                }
            } catch (HttpTimeoutException | ConnectException e) {
                LOGGER.error("Error while downloading Tenor suggested categories.\n" +
                        "Make sure you are connected to the internet");
                return new TenorQueryResponse<>(connectionStatus.NO_INTERNET);
            } catch (Exception e) {
                LOGGER.error("Error while downloading Tenor search results", e);
                return new TenorQueryResponse<>(connectionStatus.UNKNOWN);
            }

            return null;
        });
    }

    public static CompletableFuture<TenorQueryResponse<SearchResponse>> getSearchResults(String query, String pos) {
        int result_count = 8;
        String posArg;

        String cleanedQuery = sanitizeQuery(query);

        if (pos != null && !pos.isEmpty()) {
            posArg = "&pos="+pos;
        } else {
            posArg = "";
        }

        return CompletableFuture.supplyAsync(() -> {
            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(("https://tenor.googleapis.com/v2/search?key=%s&client_key=%s&locale=%s&q=%s&media_filter=gif,tinygif&limit=%d"+posArg)
                                .formatted(CONFIG.tenorApiKey(), _clientKey, MinecraftClient.getInstance().options.language, cleanedQuery, result_count)))
                        .GET()
                        .timeout(Duration.ofSeconds(3))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 ||response.statusCode() == 202) {
                    Gson gson = new Gson();
                    SearchResponse result =  gson.fromJson(response.body(), SearchResponse.class);
                    return new TenorQueryResponse<>(result);
                } else if (response.statusCode() == 403) {
                    return new TenorQueryResponse<>(connectionStatus.INVALID_API_KEY);
                }
            } catch (HttpTimeoutException | ConnectException e) {
                LOGGER.error("Error while downloading Tenor search results.\n" +
                        "Make sure you are connected to the internet");
                return new TenorQueryResponse<>(connectionStatus.NO_INTERNET);
            } catch (IOException | InterruptedException e) {
                LOGGER.error("Error while downloading Tenor search results", e);
                return new TenorQueryResponse<>(connectionStatus.UNKNOWN);
            }

            return null;
        });
    }

    public static String sanitizeQuery(String query) {
        return URLEncoder.encode(query, StandardCharsets.UTF_8);
    }

    public static CompletableFuture<TenorQueryResponse<SearchResponse>> getSearchResults(String query) {
        return getSearchResults(query, null);
    }

    public static void registerShare(String id) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://tenor.googleapis.com/v2/registershare?key=%s&id=%s&client_key=%s&locale=%s"
                            .formatted(CONFIG.tenorApiKey(), id, _clientKey, MinecraftClient.getInstance().options.language)))
                    .GET()
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofString());

        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }
    }


    // classes needed to parse json responses
    public static class TenorQueryResponse<T> {
        private final T responseValue;
        private final connectionStatus connectionStatus;

        public TenorQueryResponse(@NotNull T responseValue) {
            this.responseValue = responseValue;
            connectionStatus = TenorService.connectionStatus.CONNECTED;
        }

        public TenorQueryResponse(connectionStatus connectionStatus) {
            this.responseValue = null;
            this.connectionStatus = connectionStatus;
        }

        public T getResponseValue() {
            return responseValue;
        }

        public connectionStatus getConnectionStatus() {
            return connectionStatus;
        }

        public boolean isConnected() {
            return connectionStatus == TenorService.connectionStatus.CONNECTED;
        }

    }

    public enum connectionStatus {
        CONNECTED,
        NO_INTERNET,
        INVALID_API_KEY,
        UNKNOWN
    }

    public record CategoryResponse(String locale, List<Category> tags) {}
    public record Category(String searchterm, String path, String image, String name) {}

    public record SearchResponse(List<SearchResult> results, String next) {}
    public record SearchResult(String id, String title, MediaFormats media_formats) {}
    public record MediaFormats(MediaFormat gif, MediaFormat tinygif) {}
    public record MediaFormat(String url, float duration, String preview, List<Integer> dims, int size) {}
}
