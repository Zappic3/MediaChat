package com.zappic3.mediachat;

import com.sksamuel.scrimage.nio.AnimatedGif;
import com.zappic3.mediachat.filesharing.filesharing.DownloadedGif;
import com.zappic3.mediachat.filesharing.filesharing.DownloadedMedia;
import com.zappic3.mediachat.filesharing.filesharing.FileSharingService;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.zappic3.mediachat.MediaChat.*;
import static com.zappic3.mediachat.NetworkManager.serializeAnimatedGif;
import static com.zappic3.mediachat.NetworkManager.serializeBufferedImage;

public class ServerDownloadManager {
    private static final Path CACHE_DIR =  FabricLoader.getInstance().getGameDir().resolve("MediaChatTempFiles");
    private static final Map<Integer, List<ServerPlayerEntity>> _requests = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(10); // todo check if this is enough / make this a config option


    private ServerDownloadManager() {}

    public static void init() {
        try {
            Files.createDirectories(CACHE_DIR);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean addRequest(Integer key, ServerPlayerEntity player) {
        boolean wasEmpty = !_requests.containsKey(key) || _requests.get(key).isEmpty();
        _requests.computeIfAbsent(key, k -> new LinkedList<>()).add(player);
        return wasEmpty;
    }

    public static void requestDownload(String source, ServerPlayerEntity player) {
        boolean wasEmpty = addRequest(source.hashCode() , player);
        // only start a new download if there isn't already one running for this source
        if (wasEmpty) {
            downloadMedia(source, player);
        }
    }

    private static void downloadMedia(String source, ServerPlayerEntity player) {
        try {
            URL url = new URI(source).toURL();
            FileSharingService service = FileSharingService.getDownloadServiceFor(url);
            DownloadedMedia downloadedMedia = service.downloadWithChecks(url);

            if (downloadedMedia != null && !downloadedMedia.hasError()) {
                if(downloadedMedia instanceof DownloadedGif) {
                    AnimatedGif gif = ((DownloadedGif) downloadedMedia).getOriginalGif();
                    SERVER_CACHE.saveGifToCache(gif, source.hashCode());
                    byte[] gifBytes = serializeAnimatedGif(gif);
                    if (gifBytes != null) {
                        List<DataChunker.Chunk> chunks = new DataChunker(gifBytes).splitIntoChunks();
                        sendGifChunksToPlayers(source, chunks);
                    } else {
                        sendErrorToPlayers(source, DownloadedMedia.DownloadError.GENERIC);
                    }

                } else {
                    BufferedImage image = downloadedMedia.getDownloadedMedia().getFirst();
                    SERVER_CACHE.saveMediaToCache(image, source.hashCode(), "png");
                    byte[] imageBytes = serializeBufferedImage(image);
                    if (imageBytes != null) {
                        List<DataChunker.Chunk> chunks = new DataChunker(imageBytes).splitIntoChunks();
                        sendImageChunksToPlayers(source, chunks);
                    } else {
                        sendErrorToPlayers(source, DownloadedMedia.DownloadError.GENERIC);
                    }

                }
            } else if (downloadedMedia != null){
                sendErrorToPlayers(source, downloadedMedia.getDownloadError());
            } else {
                sendErrorToPlayers(source, DownloadedMedia.DownloadError.GENERIC);
            }
        } catch (Exception e) {
            LOGGER.error("an error occurred during media download", e);
        }
    }

    private static void sendImageChunksToPlayers(String source, List<DataChunker.Chunk> chunks) {
        sendImageChunksToPlayers(source, chunks, _requests.get(source.hashCode()));
    }
    private static void sendGifChunksToPlayers(String source, List<DataChunker.Chunk> chunks) {
        sendGifChunksToPlayers(source, chunks, _requests.get(source.hashCode()));
    }
    private static void sendErrorToPlayers(String source, DownloadedMedia.DownloadError error) {
        sendErrorToPlayers(source, error, _requests.get(source.hashCode()));
    }

    public static void sendImageChunksToPlayers(String source, List<DataChunker.Chunk> chunks, List<ServerPlayerEntity> players) {
        sendChunksToPlayers(source, chunks, players, (player, chunk) ->
                MEDIA_CHANNEL.serverHandle(player).send(
                        new NetworkManager.ClientboundMediaSyncDownloadImagePacket(
                                source.hashCode(),
                                chunk.getData(),
                                chunk.getChunkIndex(),
                                chunk.getTotalChunks()
                        )
                )
        );
    }

    public static void sendGifChunksToPlayers(String source, List<DataChunker.Chunk> chunks, List<ServerPlayerEntity> players) {
        sendChunksToPlayers(source, chunks, players, (player, chunk) ->
                MEDIA_CHANNEL.serverHandle(player).send(
                        new NetworkManager.ClientboundMediaSyncDownloadGifPacket(
                                source.hashCode(),
                                chunk.getData(),
                                chunk.getChunkIndex(),
                                chunk.getTotalChunks()
                        )
                )
        );
    }

    public static void sendErrorToPlayers(String source, DownloadedMedia.DownloadError error, List<ServerPlayerEntity> players) {
        sendToPlayers(source, players, (player) ->
                MEDIA_CHANNEL.serverHandle(player).send(
                        new NetworkManager.ClientboundMediaSyncDownloadErrorPacket(source.hashCode(), error)
                )
        );
    }

    private static void sendChunksToPlayers(String source, List<DataChunker.Chunk> chunks,
                                            List<ServerPlayerEntity> players,
                                            BiConsumer<ServerPlayerEntity, DataChunker.Chunk> sendChunk) {
        try {
            players.forEach(player ->
                    CompletableFuture.runAsync(() ->
                            chunks.forEach(chunk -> sendChunk.accept(player, chunk)), executor
                    )
            );
        } catch (Exception e) {
            LOGGER.error("Error sending chunks to players:\n{}", e.getMessage());
        }
    }

    private static void sendToPlayers(String source, List<ServerPlayerEntity> players, Consumer<ServerPlayerEntity> sendPacket) {
        try {
            players.forEach(player ->
                    CompletableFuture.runAsync(() -> sendPacket.accept(player), executor)
            );
        } catch (Exception e) {
            LOGGER.error("Error sending data to players:\n{}", e.getMessage());
        }
    }
}
