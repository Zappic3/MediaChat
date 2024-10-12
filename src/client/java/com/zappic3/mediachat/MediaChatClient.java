package com.zappic3.mediachat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.zappic3.mediachat.CacheManager.registerCachedTextures;
import static com.zappic3.mediachat.ConfigControls.registerConfigObserver;
import static com.zappic3.mediachat.MediaChat.LOGGER;
import static com.zappic3.mediachat.Utility.registerTexture;


public class MediaChatClient implements ClientModInitializer {


	@Override
	public void onInitializeClient() {
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			registerConfigObserver();
			registerCachedTextures();
		});

	}

	public static Path getModDataFolderPath() {
		Path folderPath = FabricLoader.getInstance().getGameDir().resolve("MediaChat");
        try {
            Files.createDirectories(folderPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return folderPath;
	}

	public static Path getModDataFolderPath(String subdirectories) {
		Path folderPath = FabricLoader.getInstance().getGameDir().resolve("MediaChat").resolve(subdirectories);
		try {
			Files.createDirectories(folderPath);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return folderPath;
	}
}