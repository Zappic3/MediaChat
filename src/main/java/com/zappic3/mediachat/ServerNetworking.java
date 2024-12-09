package com.zappic3.mediachat;

import com.sksamuel.scrimage.nio.AnimatedGif;
import com.sksamuel.scrimage.nio.internal.GifSequenceReader;
import com.zappic3.mediachat.filesharing.filesharing.DownloadedMedia;
import io.wispforest.owo.network.ServerAccess;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.mojang.text2speech.Narrator.LOGGER;
import static com.zappic3.mediachat.MediaChat.*;
import static com.zappic3.mediachat.NetworkManager.serializeAnimatedGif;
import static com.zappic3.mediachat.NetworkManager.serializeBufferedImage;
import static com.zappic3.mediachat.ServerDownloadManager.*;

public class ServerNetworking {
    private final static Map<UUID, DataAssembler> _uncompleteDataAssemblers = new HashMap<>();

    private ServerNetworking(){}

    public static void registerPackets() {
        MEDIA_CHANNEL.registerServerbound(NetworkManager.ServerboundMediaSyncUploadImagePacket.class, (message, access) -> {
            LOGGER.info("received upload image message: "+message);
            DataAssembler assembler = receiveData(message.mediaId(), message.data(), message.currentChunk(), message.totalChunks(), access);
            // todo check conditions & disrupt upload if needed (e.g. package size)
            if (assembler != null && assembler.isComplete()) {
                try (ByteArrayInputStream bais = new ByteArrayInputStream(assembler.assemble())) {
                    BufferedImage image = ImageIO.read(bais);
                    String mediaLocator = "server:" + message.mediaId();
                    LOGGER.info("saved media under name: " + mediaLocator + " and hash: " + mediaLocator.hashCode());
                    SERVER_CACHE.saveMediaToCache(image, mediaLocator.hashCode(), "png");
                    MEDIA_CHANNEL.serverHandle(access.player()).send(new NetworkManager.ClientboundMediaSyncUploadResponsePacket(message.mediaId(), mediaLocator));
                } catch (IOException e) {
                    MEDIA_CHANNEL.serverHandle(access.player()).send(
                            new NetworkManager.ClientboundMediaSyncUploadErrorPacket(message.mediaId(), "An error occurred during upload: " + e.getMessage())); //todo check if this can be localized
                }
            }


        });

        MEDIA_CHANNEL.registerServerbound(NetworkManager.ServerboundMediaSyncUploadGifPacket.class, (message, access) -> {
            LOGGER.info("received upload gif message: "+message);
            DataAssembler assembler = receiveData(message.mediaId(), message.data(), message.currentChunk(), message.totalChunks(), access);
            if (assembler != null && assembler.isComplete()) {
                try (ByteArrayInputStream bais = new ByteArrayInputStream(assembler.assemble())) {
                    GifSequenceReader reader = new GifSequenceReader();
                    reader.read(bais);
                    AnimatedGif gif = new AnimatedGif(reader);
                    String mediaLocator = "server:" + message.mediaId();
                    SERVER_CACHE.saveGifToCache(gif, mediaLocator.hashCode());
                    MEDIA_CHANNEL.serverHandle(access.player()).send(new NetworkManager.ClientboundMediaSyncUploadResponsePacket(message.mediaId(), mediaLocator));
                } catch (IOException e) {
                    MEDIA_CHANNEL.serverHandle(access.player()).send(
                            new NetworkManager.ClientboundMediaSyncUploadErrorPacket(message.mediaId(), "An error occurred during upload: " + e.getMessage())); //todo check if this can be localized
                }
            }
        });

        MEDIA_CHANNEL.registerServerbound(NetworkManager.ServerboundMediaSyncRequestDownloadPacket.class, (message, access) -> {
            LOGGER.info("received download message from "+access.player().getName().getString());
            LOGGER.info("request: " + message.mediaLocation() + ", hash: " + message.mediaLocation().hashCode());
            if (SERVER_CACHE.isFileInCache(message.mediaLocation().hashCode())) {
                LOGGER.info("file is in cache");
                OneOfTwo<BufferedImage, AnimatedGif> data =  SERVER_CACHE.loadFileFromCache(message.mediaLocation().hashCode());
                if (data.getFirst() != null) {
                    List<DataChunker.Chunk> chunks = new DataChunker(serializeBufferedImage(data.getFirst())).splitIntoChunks();
                    sendImageChunksToPlayers(message.mediaLocation(), chunks, Collections.singletonList(access.player()));
                } else if (data.getSecond() != null) {
                    List<DataChunker.Chunk> chunks = new DataChunker(serializeAnimatedGif(data.getSecond())).splitIntoChunks();
                    sendGifChunksToPlayers(message.mediaLocation(), chunks, Collections.singletonList(access.player()));
                }
            } else if (message.mediaLocation().startsWith("server:")) {
                MEDIA_CHANNEL.serverHandle(access.player()).send(
                        new NetworkManager.ClientboundMediaSyncDownloadErrorPacket(message.mediaLocation().hashCode(), DownloadedMedia.DownloadError.NOT_FOUND));
            } else {
                CompletableFuture.runAsync(() -> requestDownload(message.mediaLocation(), access.player()));
            }
        });

        MEDIA_CHANNEL.registerClientboundDeferred(NetworkManager.ClientboundMediaSyncUploadResponsePacket.class);
        MEDIA_CHANNEL.registerClientboundDeferred(NetworkManager.ClientboundMediaSyncUploadErrorPacket.class);
        MEDIA_CHANNEL.registerClientboundDeferred(NetworkManager.ClientboundMediaSyncDownloadImagePacket.class);
        MEDIA_CHANNEL.registerClientboundDeferred(NetworkManager.ClientboundMediaSyncDownloadGifPacket.class);
        MEDIA_CHANNEL.registerClientboundDeferred(NetworkManager.ClientboundMediaSyncDownloadErrorPacket.class);
    }

    private static DataAssembler receiveData(UUID uuid, byte[] data, int currentChunk, int totalChunks, ServerAccess access) {
        // todo check conditions & disrupt upload if needed (e.g. package size)
        long minTotalSize = (long) (totalChunks - 1) * data.length;
        if (minTotalSize > (long) CONFIG.serverMaxFileSize() * 1024 * 1024 * 8) {
            MEDIA_CHANNEL.serverHandle(access.player()).send(
                    new NetworkManager.ClientboundMediaSyncUploadErrorPacket(uuid, "File is too big to upload")); //todo check if this can be localized
            return null;
        }

        DataAssembler assembler = _uncompleteDataAssemblers.computeIfAbsent(uuid, k -> new DataAssembler(totalChunks));
        assembler.addChunk(currentChunk, data);
        return assembler;
    }
}
