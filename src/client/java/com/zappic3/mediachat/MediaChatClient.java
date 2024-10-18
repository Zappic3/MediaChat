package com.zappic3.mediachat;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.layers.Layers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.zappic3.mediachat.CacheManager.registerCachedTextures;
import static com.zappic3.mediachat.ConfigControls.registerConfigObserver;
import static com.zappic3.mediachat.MediaChat.LOGGER;
import static com.zappic3.mediachat.Utility.registerTexture;
import static com.zappic3.mediachat.ui.GifBrowserUI.addGifUIToChatScreen;


public class MediaChatClient implements ClientModInitializer {


	@Override
	public void onInitializeClient() {
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			registerConfigObserver();
			registerCachedTextures();

			MouseClickCallback.EVENT.register((window, button, action, mods) -> {
				MediaElement.reactToMouseClick(button, action);
				return ActionResult.PASS;
			});

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