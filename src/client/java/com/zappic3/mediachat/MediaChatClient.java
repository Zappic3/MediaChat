package com.zappic3.mediachat;

import com.zappic3.mediachat.filesharing.filesharing.DownloadedMedia;
import com.zappic3.mediachat.ui.GifBrowserUI;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;

import java.nio.file.Path;
import java.time.Duration;


import static com.zappic3.mediachat.ConfigControls.registerConfigObserver;
import static com.zappic3.mediachat.MediaChat.*;
import static com.zappic3.mediachat.ui.GifBrowserUI.addGifUIToChatScreen;


public class MediaChatClient implements ClientModInitializer {
	public static final SizeLimitedCache CLIENT_CACHE = new SizeLimitedCache(MediaCache.getModRoot().resolve("cache"), CONFIG.maxCacheSize());
	private long _lastSlowUpdate = System.currentTimeMillis();
	private Duration _slowUpdateInterval = Duration.ofSeconds(5);

	@Override
	public void onInitializeClient() {
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			registerConfigObserver();
			addGifUIToChatScreen();
			DownloadedMedia.setTranslationProvider(new ClientTranslationProvider());
			ClientCommands.registerClientCommands();

			MouseClickCallback.EVENT.register((window, button, action, mods) -> {
				MediaElement.reactToMouseClick(button, action);
				return ActionResult.PASS;
			});

		});

		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

		// register networking stuff
		ClientNetworking.registerPackets();

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
}