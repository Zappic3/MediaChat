package com.zappic3.mediachat;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class MediaChat implements ModInitializer {
	public static final String MOD_ID = "media-chat";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		String[] messages = {"Hello Fabric world!", "I'm nowt gonna cwash, i pwomise (>﹏<)", "And the universe said I love you because you are love"};
		int rnd = new Random().nextInt(messages.length);
		LOGGER.info(messages[rnd]);
	}
}