package com.zappic3.mediachat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import com.zappic3.mediachat.MediaChatConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MediaChatClient implements ClientModInitializer {
	public static final MediaChatConfig CONFIG = MediaChatConfig.createAndLoad();


	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
		HudRenderCallback.EVENT.register(this::onHudRender);
	}

	private void onClientTick(MinecraftClient client) {

	}

	private void onHudRender(DrawContext context, RenderTickCounter renderTickCounter) {
		// This is where you draw your rectangle
		// Example: Draw a red rectangle at position (10, 10) with width 100 and height 50

		int x = 10;
		int y = 10;
		int width = 100;
		int height = 50;
		int color = 0xFFFF0000; // ARGB format: Alpha, Red, Green, Blue

		//context.fill(x, y, x + width, y + height, color);
	}
}