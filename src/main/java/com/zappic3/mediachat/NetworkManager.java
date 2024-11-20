package com.zappic3.mediachat;

import java.awt.image.BufferedImage;

public class NetworkManager {

    // client uploads media to the server
    public record ServerboundMediaSyncUploadPacket(String image) {}
    // server returns new media locator of uploaded media
    public record ClientboundMediaSyncUploadSuccessPacket(String mediaLocation) {}
    // client requests media from server via a  link or locator
    public record ServerboundMediaSyncRequestDownloadPacket(String mediaLocation) {}
    // server sends requested media to the client
    public record ClientboundMediaSyncDownloadPacket(BufferedImage image) {}
}
