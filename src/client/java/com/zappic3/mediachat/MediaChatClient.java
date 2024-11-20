package com.zappic3.mediachat;

import com.zappic3.mediachat.filesharing.filesharing.DownloadedMedia;
import com.zappic3.mediachat.ui.GifBrowserUI;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.zappic3.mediachat.NetworkManager.*;

import static com.zappic3.mediachat.CacheManager.registerCachedTextures;
import static com.zappic3.mediachat.ConfigControls.registerConfigObserver;
import static com.zappic3.mediachat.MediaChat.LOGGER;
import static com.zappic3.mediachat.MediaChat.MEDIA_CHANNEL;
import static com.zappic3.mediachat.ui.GifBrowserUI.addGifUIToChatScreen;


public class MediaChatClient implements ClientModInitializer {
	private long _lastSlowUpdate = System.currentTimeMillis();
	private Duration _slowUpdateInterval = Duration.ofSeconds(5);

	@Override
	public void onInitializeClient() {
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			registerConfigObserver();
			registerCachedTextures();
			addGifUIToChatScreen();
			DownloadedMedia.setTranslationProvider(new ClientTranslationProvider());

			MouseClickCallback.EVENT.register((window, button, action, mods) -> {
				MediaElement.reactToMouseClick(button, action);
				return ActionResult.PASS;
			});

		});

		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

		// register networking stuff
		MEDIA_CHANNEL.registerClientbound(ClientboundMediaSyncUploadSuccessPacket.class, ((message, access) -> {
			LOGGER.info("received media sync upload success packet");
		}));

	}

	private void onClientTick(MinecraftClient client) {
		GifBrowserUI.update();

		// update stuff that doesn't need updates every tick to save performance
		long currentTime = System.currentTimeMillis();
		if (currentTime > _lastSlowUpdate + _slowUpdateInterval.toMillis()) {
			_lastSlowUpdate = currentTime;
			MediaElement.removeUnusedElements();
		}
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