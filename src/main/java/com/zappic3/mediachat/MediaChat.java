package com.zappic3.mediachat;

import com.zappic3.mediachat.filesharing.filesharing.DownloadedMedia;
import io.wispforest.owo.network.OwoNetChannel;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class MediaChat implements ModInitializer {
	public static final String MOD_ID = "media-chat";
	public static final com.zappic3.mediachat.MediaChatConfig CONFIG = com.zappic3.mediachat.MediaChatConfig.createAndLoad();
	public static final OwoNetChannel MEDIA_CHANNEL = OwoNetChannel.create(Identifier.of(MOD_ID, "media_sync"));
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final SizeLimitedCache SERVER_CACHE = new SizeLimitedCache(MediaCache.getModRoot().resolve("MediaChatTempFiles"), CONFIG.serverMaxCacheSize());

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		sendStartupMessage();
		Commands.register();
		ServerNetworking.registerPackets();

		// do some server only stuff
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			FeatureTracker.isServer(true);
			if (server.isDedicated()) {
				FeatureTracker.isDedicatedServer(true);
				ServerDownloadManager.init();
				DownloadedMedia.setTranslationProvider(new ServerTranslationProvider());
			}
		});
	}

	private void sendStartupMessage() {
		String[] messages = {"Hello Fabric world!",
				"I'm nowt gonna cwash, i pwomise (>﹏<)",
				"And the universe said I love you because you are love",
				"It's pronounced geoff!"
		};

		int rnd = new Random().nextInt(messages.length);
		LOGGER.info(messages[rnd]);
	}

	private void registerCommands() {

	}
}