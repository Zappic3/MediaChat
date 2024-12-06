package com.zappic3.mediachat;

import com.sksamuel.scrimage.nio.AnimatedGif;
import com.zappic3.mediachat.filesharing.filesharing.DownloadedMedia;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import static com.zappic3.mediachat.MediaChat.LOGGER;

public class NetworkManager {

    // client uploads media to the server
    public record ServerboundMediaSyncUploadImagePacket(UUID mediaId, byte[] data, int currentChunk, int totalChunks) {}
    public record ServerboundMediaSyncUploadGifPacket(UUID mediaId, byte[] data, int currentChunk, int totalChunks) {}

    // server returns new media locator of uploaded media
    public record ClientboundMediaSyncUploadResponsePacket(UUID mediaId, String mediaLocator) {}
    public record ClientboundMediaSyncUploadErrorPacket(UUID mediaId, String errorMessage) {}

    // client requests media from server via a  link or locator
    public record ServerboundMediaSyncRequestDownloadPacket(String mediaLocation) {}

    // server sends requested media to the client
    public record ClientboundMediaSyncDownloadImagePacket(int mediaId, byte[] imageData, int currentChunk, int totalChunks) {}
    public record ClientboundMediaSyncDownloadGifPacket(int mediaId, byte[] gifData, int currentChunk, int totalChunks) {}
    public record ClientboundMediaSyncDownloadErrorPacket(int mediaId, DownloadedMedia.DownloadError error) {}

    public static byte[] serializeBufferedImage(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            LOGGER.error("Error serializing BufferedImage", e);
            return null;
        }
    }

    public static byte[] serializeAnimatedGif(AnimatedGif gif) {
        try {
            return gif.getBytes();
        } catch (IOException e) {
            LOGGER.error("Error serializing AnimatedGif", e);
            return null;
        }
    }
}
