package com.zappic3.mediachat;

import com.zappic3.mediachat.ui.GifBrowserUI;
import io.wispforest.owo.Owo;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.layers.Layers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static com.zappic3.mediachat.CacheManager.registerCachedTextures;
import static com.zappic3.mediachat.ConfigControls.registerConfigObserver;
import static com.zappic3.mediachat.MediaChat.LOGGER;
import static com.zappic3.mediachat.Utility.registerTexture;
import static com.zappic3.mediachat.ui.GifBrowserUI.addGifUIToChatScreen;
import static net.minecraft.server.command.CommandManager.*;


public class MediaChatClient implements ClientModInitializer {
	private long _lastSlowUpdate = System.currentTimeMillis();
	private Duration _slowUpdateInterval = Duration.ofSeconds(5);

	@Override
	public void onInitializeClient() {
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			registerConfigObserver();
			registerCachedTextures();
			addGifUIToChatScreen();

			MouseClickCallback.EVENT.register((window, button, action, mods) -> {
				MediaElement.reactToMouseClick(button, action);
				return ActionResult.PASS;
			});

		});

		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

		// register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("mediachat")
				.executes(context -> {
					ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
					context.getSource().sendFeedback(() -> Text.literal(player.getName().getString()), false);
					return 1;
				})
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