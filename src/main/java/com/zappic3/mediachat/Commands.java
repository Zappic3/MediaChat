package com.zappic3.mediachat;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.GameProfileArgumentType;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.zappic3.mediachat.ConfigModel.ServerMediaPermissionMode.*;
import static com.zappic3.mediachat.MediaChat.CONFIG;
import static net.minecraft.server.command.CommandManager.literal;

public class Commands {
    private static final SimpleCommandExceptionType WHITELIST_ADD_FAILED_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("text.mediachat.commands.whitelist.addFailed"));
    private static final SimpleCommandExceptionType BLACKLIST_ADD_FAILED_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("text.mediachat.commands.blacklist.addFailed"));
    private static final SimpleCommandExceptionType WHITELIST_REMOVE_FAILED_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("text.mediachat.commands.whitelist.removeFailed"));
    private static final SimpleCommandExceptionType BLACKLIST_REMOVE_FAILED_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("text.mediachat.commands.blacklist.removeFailed"));
    private static final SimpleCommandExceptionType WHITELIST_ALREADY_ON_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("text.mediachat.commands.whitelist.alreadyOn"));
    private static final SimpleCommandExceptionType BLACKLIST_ALREADY_ON_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("text.mediachat.commands.blacklist.alreadyOn"));
    private static final SimpleCommandExceptionType WHITELIST_ALREADY_OFF_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("text.mediachat.commands.whitelist.alreadyOff"));
    private static final SimpleCommandExceptionType BLACKLIST_ALREADY_OFF_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("text.mediachat.commands.blacklist.alreadyOff"));
    private static final SimpleCommandExceptionType WHITELIST_ALREADY_OFF_BLACKLIST_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("text.mediachat.commands.whitelist.alreadyOff.blacklist"));
    private static final SimpleCommandExceptionType Blacklist_ALREADY_OFF_WHITELIST_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("text.mediachat.commands.blacklist.alreadyOff.whitelist"));


    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            dispatcher.register(literal("mediachatserver")
                    .requires(source -> source.hasPermissionLevel(3))
                    .then(literal("whitelist")
                            .then(CommandManager.literal("on").executes(context -> whitelistOn(context.getSource())))
                            .then(CommandManager.literal("off").executes(context -> whitelistOff(context.getSource())))
                            .then(CommandManager.literal("list").executes(context -> permissionListList(context.getSource(), WHITELIST)))
                            .then(literal("add")
                                    .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                            .suggests((context, builder) -> {
                                                PlayerManager playerManager = context.getSource().getServer().getPlayerManager();
                                                List<PlayerListEntry> whitelist = CONFIG.serverWhitelist();

                                                return CommandSource.suggestMatching(
                                                        playerManager.getPlayerList().stream()
                                                                .map(PlayerEntity::getGameProfile)
                                                                .filter(profile -> whitelist.stream()
                                                                        .noneMatch(entry -> entry.getUUID().equals(profile.getId())))
                                                                .map(GameProfile::getName),
                                                        builder
                                                );
                                            })
                                            .executes(context -> permissionListAdd(context.getSource(), GameProfileArgumentType.getProfileArgument(context, "player"), WHITELIST)))
                            )
                            .then(literal("remove")
                            .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                    .suggests((context, builder) -> {
                                        List<PlayerListEntry> whitelist = CONFIG.serverWhitelist();
                                        return CommandSource.suggestMatching(
                                                whitelist.stream()
                                                        .map(PlayerListEntry::getPlayer)
                                                , builder
                                        );
                                    })
                                    .executes(context -> permissionListRemove(context.getSource(), GameProfileArgumentType.getProfileArgument(context, "player"), WHITELIST))))
                    )
                    .then(literal("blacklist")
                            .then(CommandManager.literal("on").executes(context -> blacklistOn(context.getSource())))
                            .then(CommandManager.literal("off").executes(context -> blacklistOff(context.getSource())))
                            .then(CommandManager.literal("list").executes(context -> permissionListList(context.getSource(), BLACKLIST)))
                            .then(literal("add")
                                    .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                            .suggests((context, builder) -> {
                                                PlayerManager playerManager = context.getSource().getServer().getPlayerManager();
                                                List<PlayerListEntry> whitelist = CONFIG.serverBlacklist();

                                                return CommandSource.suggestMatching(
                                                        playerManager.getPlayerList().stream()
                                                                .map(PlayerEntity::getGameProfile)
                                                                .filter(profile -> whitelist.stream()
                                                                        .noneMatch(entry -> entry.getUUID().equals(profile.getId())))
                                                                .map(GameProfile::getName),
                                                        builder
                                                );
                                            })
                                            .executes(context -> permissionListAdd(context.getSource(), GameProfileArgumentType.getProfileArgument(context, "player"), BLACKLIST)))
                            )
                            .then(literal("remove")
                                    .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                            .suggests((context, builder) -> {
                                                List<PlayerListEntry> whitelist = CONFIG.serverBlacklist();
                                                return CommandSource.suggestMatching(
                                                        whitelist.stream()
                                                                .map(PlayerListEntry::getPlayer)
                                                        , builder
                                                );
                                            })
                                            .executes(context -> permissionListRemove(context.getSource(), GameProfileArgumentType.getProfileArgument(context, "player"), BLACKLIST))))
                    )
            );
        });
    }

    // region permission list code
    private static int whitelistOn(ServerCommandSource source) throws CommandSyntaxException {
        switch (CONFIG.serverMediaPermissionMode()) {
            case WHITELIST -> throw WHITELIST_ALREADY_ON_EXCEPTION.create();
            case BLACKLIST -> source.sendFeedback(() -> Text.translatable("text.mediachat.commands.whitelist.enabledOverBlacklist"), true);
            default -> source.sendFeedback(() -> Text.translatable("text.mediachat.commands.whitelist.enabled"), true);
        }
        CONFIG.serverMediaPermissionMode(WHITELIST);
        return 1;
    }

    private static int blacklistOn(ServerCommandSource source) throws CommandSyntaxException {
        switch (CONFIG.serverMediaPermissionMode()) {
            case BLACKLIST -> throw BLACKLIST_ALREADY_ON_EXCEPTION.create();
            case WHITELIST -> source.sendFeedback(() -> Text.translatable("text.mediachat.commands.blacklist.enabledOverWhitelist"), true);
            default -> source.sendFeedback(() -> Text.translatable("text.mediachat.commands.blacklist.enabled"), true);
        }
        CONFIG.serverMediaPermissionMode(BLACKLIST);
        return 1;
    }

    private static int whitelistOff(ServerCommandSource source) throws CommandSyntaxException {
        switch (CONFIG.serverMediaPermissionMode()) {
            case WHITELIST -> {
                CONFIG.serverMediaPermissionMode(ConfigModel.ServerMediaPermissionMode.OFF);
                source.sendFeedback(() -> Text.translatable("text.mediachat.commands.whitelist.disabled"), true);
                return 1;
            }
            case BLACKLIST -> throw WHITELIST_ALREADY_OFF_BLACKLIST_EXCEPTION.create();
            default -> throw WHITELIST_ALREADY_OFF_EXCEPTION.create();
        }
    }

    private static int blacklistOff(ServerCommandSource source) throws CommandSyntaxException {
        switch (CONFIG.serverMediaPermissionMode()) {
            case BLACKLIST -> {
                CONFIG.serverMediaPermissionMode(ConfigModel.ServerMediaPermissionMode.OFF);
                source.sendFeedback(() -> Text.translatable("text.mediachat.commands.blacklist.disabled"), true);
                return 1;
            }
            case WHITELIST -> throw Blacklist_ALREADY_OFF_WHITELIST_EXCEPTION.create();
            default -> throw BLACKLIST_ALREADY_OFF_EXCEPTION.create();
        }
    }

    private static int permissionListList(ServerCommandSource source, ConfigModel.ServerMediaPermissionMode mode) {
        List<PlayerListEntry> list = getPlayerList(mode);
        if (list.isEmpty()) {
            Text text = mode == WHITELIST
                    ? Text.translatable("text.mediachat.commands.whitelist.none")
                    : Text.translatable("text.mediachat.commands.blacklist.none");
            source.sendFeedback(() -> text, false);

        } else {
            String names = list.stream()
                    .map(PlayerListEntry::getPlayer)
                    .collect(Collectors.joining(", "));

            Text text = mode == WHITELIST
                    ? Text.translatable("text.mediachat.commands.whitelist.list", list.size(), names)
                    : Text.translatable("text.mediachat.commands.blacklist.list", list.size(), names);

            source.sendFeedback(() -> text, false);
        }
        return list.size();
    }

    private static int permissionListAdd(ServerCommandSource source, Collection<GameProfile> targets, ConfigModel.ServerMediaPermissionMode mode) throws CommandSyntaxException {
        List<PlayerListEntry> list = new ArrayList<>(getPlayerList(mode));
        int i = 0;

        for (GameProfile profile : targets) {
            PlayerListEntry entry = new PlayerListEntry(profile.getName(), profile.getId());
            if (list.stream().noneMatch(existingEntry -> existingEntry.equals(entry))) {
                list.add(entry);
                Text text = mode == WHITELIST
                        ? Text.translatable("text.mediachat.commands.whitelist.playerAdded", Text.literal(profile.getName()))
                        : Text.translatable("text.mediachat.commands.blacklist.playerAdded", Text.literal(profile.getName()));
                source.sendFeedback(() -> text, true);
                i++;
            }
        }
        setPlayerList(mode, list);

        if (i == 0) {
            if (mode == WHITELIST) {
                throw WHITELIST_ADD_FAILED_EXCEPTION.create();
            }
            throw BLACKLIST_ADD_FAILED_EXCEPTION.create();
        } else {
            return i;
        }
    }

    private static int permissionListRemove(ServerCommandSource source, Collection<GameProfile> targets, ConfigModel.ServerMediaPermissionMode mode) throws CommandSyntaxException {
        List<PlayerListEntry> list = new ArrayList<>(getPlayerList(mode));

        int i = 0;
        for (GameProfile profile : targets) {
            boolean removed = list.removeIf(entry -> entry.getUUID().equals(profile.getId() ));
            if (removed) {
                Text text = mode == WHITELIST
                        ? Text.translatable("text.mediachat.commands.whitelist.playerRemoved", Text.literal(profile.getName()))
                        : Text.translatable("text.mediachat.commands.blacklist.playerRemoved", Text.literal(profile.getName()));
                source.sendFeedback(() -> text, true);
                i++;
            }
        }
        setPlayerList(mode, list);

        if (i == 0) {
            if (mode == WHITELIST) {
                throw WHITELIST_REMOVE_FAILED_EXCEPTION.create();
            }
            throw BLACKLIST_REMOVE_FAILED_EXCEPTION.create();
        } else {
            return i;
        }
    }
    //endregion


    private static List<PlayerListEntry> getPlayerList(ConfigModel.ServerMediaPermissionMode mode) {
        if (mode == WHITELIST) {
            return CONFIG.serverWhitelist();
        } else {
            return CONFIG.serverBlacklist();
        }
    }

    private static void setPlayerList(ConfigModel.ServerMediaPermissionMode mode, List<PlayerListEntry> list) {
        if (mode == WHITELIST) {
            CONFIG.serverWhitelist(list);
        } else if (mode == BLACKLIST) {
            CONFIG.serverBlacklist(list);
        }
    }



}
