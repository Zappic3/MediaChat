package com.zappic3.mediachat.mixin;

import com.zappic3.mediachat.ConfigModel;
import com.zappic3.mediachat.PlayerListEntry;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

import static com.zappic3.mediachat.MediaChat.CONFIG;
import static com.zappic3.mediachat.MediaMessageUtility.isMediaMessage;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayerNetworkHandlerMixin {

    @Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
    public void onChatMessage(ChatMessageC2SPacket packet, CallbackInfo ci) {
        if (CONFIG.serverMediaPermissionMode() != ConfigModel.ServerMediaPermissionMode.OFF && isMediaMessage(packet.chatMessage(), false)) {
            ServerPlayNetworkHandler handler = (ServerPlayNetworkHandler) (Object) this;
            ServerPlayerEntity player = handler.player; // Get the player who sent the message
            PlayerListEntry entry = new PlayerListEntry(player.getGameProfile().getName(), player.getGameProfile().getId());

            if (CONFIG.serverMediaPermissionMode() == ConfigModel.ServerMediaPermissionMode.WHITELIST) {
                List<PlayerListEntry> whitelist = CONFIG.serverWhitelist();
                if (!player.hasPermissionLevel(3) && whitelist.stream().noneMatch(existingEntry -> existingEntry.equals(entry))) {
                    player.sendMessage(Text.translatable("text.mediachat.chatMessageBlocked").formatted(Formatting.RED));
                    ci.cancel();
                }
            } else if (CONFIG.serverMediaPermissionMode() == ConfigModel.ServerMediaPermissionMode.BLACKLIST) {
                List<PlayerListEntry> blacklist = CONFIG.serverBlacklist();
                if (!player.hasPermissionLevel(3) && blacklist.stream().anyMatch(existingEntry -> existingEntry.equals(entry))) {
                    player.sendMessage(Text.translatable("text.mediachat.chatMessageBlocked").formatted(Formatting.RED));
                    ci.cancel();
                }
            }
        }
    }
}
