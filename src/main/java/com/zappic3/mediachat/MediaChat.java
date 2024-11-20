package com.zappic3.mediachat;

import com.zappic3.mediachat.filesharing.filesharing.DownloadedMedia;
import io.wispforest.owo.network.OwoNetChannel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zappic3.mediachat.MediaChatConfig;
import com.zappic3.mediachat.NetworkManager.*;

import java.util.Random;

import static net.minecraft.server.command.CommandManager.literal;

public class MediaChat implements ModInitializer {
	public static final String MOD_ID = "media-chat";
	public static final MediaChatConfig CONFIG = MediaChatConfig.createAndLoad();
	public static final OwoNetChannel MEDIA_CHANNEL = OwoNetChannel.create(Identifier.of(MOD_ID, "media_sync"));
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static boolean isDedicatedServer = false;

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		sendStartupMessage();
		registerNetworking();
		registerCommands();

		// do some server only stuff
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (server.isDedicated()) {
				isDedicatedServer = true;
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

	private void registerNetworking() {
		MEDIA_CHANNEL.registerServerbound(ServerboundMediaSyncUploadPacket.class, (message, access) -> {
			LOGGER.info("received upload message: "+message);
		});

		MEDIA_CHANNEL.registerServerbound(ServerboundMediaSyncRequestDownloadPacket.class, (message, access) -> {
			LOGGER.info("received download message: "+message);
			if (isDedicatedServer) {

			}
		});
		MEDIA_CHANNEL.registerClientboundDeferred(ClientboundMediaSyncUploadSuccessPacket.class);
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("mediachat")
				.executes(context -> {
					ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
					context.getSource().sendFeedback(() -> Text.literal( "hello " + player.getName().getString()), false);
					return 1;
				})
				.then(literal("network")
						.executes(context -> {
							//MEDIA_CHANNEL.clientHandle().send(new ServerboundMediaSyncUploadPacket("Sent Package to Client"));
							ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
							MEDIA_CHANNEL.serverHandle(player).send(new ClientboundMediaSyncUploadSuccessPacket("TEST"));
							context.getSource().sendFeedback(() -> Text.literal("package sent"), false);
							return 1;
						}))
				.then(literal("displayLoadedMedia")
						.executes(context -> {
							context.getSource().sendFeedback(() -> Text.literal("this is not implemented yet and you shouldn't be seeing this"), false);
							return 1;
						}))
				.then(literal("tenor")
						.executes(context -> {
							String configFilePath = FabricLoader.getInstance().getGameDir().resolve("config").resolve("mediachat.json5").toString();

							Text clickablePath = Text.literal(configFilePath)
									.styled(style -> style.withColor(Formatting.BLUE)
											.withUnderline(true)
											.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, configFilePath))
											.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to copy filepath to clipboard").styled(s -> s.withColor(Formatting.YELLOW)))));

							Text result = Text.empty()
									.append(Text.literal("\n"))
									.append(Text.translatable("text.mediachat.tenorApiKeyTutorial.beginning"))
									.append(Text.literal("\n\n"))
									.append(Text.translatable("text.mediachat.tenorApiKeyTutorial.1"))
									.append(Text.literal("\n\n"))
									.append(Text.translatable("text.mediachat.tenorApiKeyTutorial.2"))
									.append(Text.literal("\n\n"))
									.append(Text.translatable("text.mediachat.tenorApiKeyTutorial.3"))
									.append(Text.literal("\n\n"))
									.append(Text.translatable("text.mediachat.tenorApiKeyTutorial.4"))
									.append(Text.literal("\n\n"))
									.append(Text.translatable("text.mediachat.tenorApiKeyTutorial.5"))
									.append(Text.literal("\n\n"))
									.append(Text.translatable("text.mediachat.tenorApiKeyTutorial.6"))
									.append(Text.literal("\n\n"))
									.append(Text.translatable("text.mediachat.tenorApiKeyTutorial.7.1"))
									.append(Text.literal("\n\n"))
									.append(Text.translatable("text.mediachat.tenorApiKeyTutorial.7.2.1"))
									.append(clickablePath)
									.append(Text.translatable("text.mediachat.tenorApiKeyTutorial.7.2.2"))
									.append(Text.literal("\n\n"))
									.append(Text.translatable("text.mediachat.tenorApiKeyTutorial.disclaimer"));

							context.getSource().sendFeedback(() -> result, false);
							return 1;
						}))
		));
	}
}