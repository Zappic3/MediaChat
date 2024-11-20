package com.zappic3.mediachat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ServerDownloadManager {
    private static final Path CACHE_DIR =  FabricLoader.getInstance().getGameDir().resolve("MediaChatTempFiles");

    private ServerDownloadManager() {}

    public static void init() {
        try {
            Files.createDirectories(CACHE_DIR);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void downloadMedia() {

    }

}
