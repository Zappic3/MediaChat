package com.zappic3.mediachat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.zappic3.mediachat.MediaChat.CONFIG;
import static com.zappic3.mediachat.MediaChat.LOGGER;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;


public class ClientCommands {

    public static void registerClientCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> command = literal("mediachat")
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

                                context.getSource().sendFeedback(result);
                                return 1;
                            }));

            dispatcher.register(command);
        });
    }
}
