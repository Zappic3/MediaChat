package com.zappic3.mediachat.mixin.client;

import com.zappic3.mediachat.MediaElement;
import com.zappic3.mediachat.Utility;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

import static com.zappic3.mediachat.MediaChat.CONFIG;
import static com.zappic3.mediachat.MediaChatClient.CLIENT_CACHE;

@Mixin(DebugHud.class)
public class DebugScreenMixin {
    @Inject(method = "getLeftText", at = @At("RETURN"), cancellable = true)
    private void addCustomLeftSideInfo(CallbackInfoReturnable<List<String>> cir) {
        if (CONFIG.displayDebugScreenInfos()) {
            List<String> debugText = cir.getReturnValue();

            // loaded media elements
            long rawRamUsage = MediaElement.totalMediaElementSize();
            String formattedRamUsage = Utility.formatBits(rawRamUsage);
            long rawMaxRamUsage = Utility.megabytesToBits(CONFIG.maxRamUsage());
            String formattedMaxRamUsage = Utility.formatBits(rawMaxRamUsage);
            float ramUsagePercent = ((float) 100 / rawMaxRamUsage) * rawRamUsage;
            int loadedMediaElement = MediaElement.loadedMediaElementCount();


            // cache
            long rawCacheUsage = CLIENT_CACHE.getCurrentCacheSize();
            String formattedCacheUsage = Utility.formatBits(rawCacheUsage);
            long rawMaxCacheUsage = Utility.megabytesToBits(CONFIG.maxCacheSize());
            String formattedMaxCacheUsage = Utility.formatBits(rawMaxCacheUsage);
            float cacheUsagePercent = ((float) 100 / rawMaxCacheUsage) * rawCacheUsage;
            int cachedElementCount = CLIENT_CACHE.getCachedElementCount();


            List<String> modifiedDebugText = new ArrayList<>(debugText);
            modifiedDebugText.add("");
            modifiedDebugText.add("§b┌─ §lMediaChat§r§b ────────────§r");
            modifiedDebugText.add("§b│§6RAM Usage:%s %f %%  (%s / %s),§r§6 %d Element%s Loaded".formatted(percentToColor(ramUsagePercent), ramUsagePercent, formattedRamUsage, formattedMaxRamUsage, loadedMediaElement, loadedMediaElement == 1 ? "" : "s"));
            modifiedDebugText.add("§b│§6Cache Usage:%s %f %% (%s / %s),§r§6 %d Element%s Cached".formatted(percentToColor(cacheUsagePercent), cacheUsagePercent, formattedCacheUsage, formattedMaxCacheUsage, cachedElementCount, cachedElementCount == 1 ? "" : "s"));
            modifiedDebugText.add("§b└ ────────────────────");

            cir.setReturnValue(modifiedDebugText);
        }
    }

    @Unique
    private String percentToColor(float percent) {
        if (percent >= 95) {
            return "§4§l";
        }
        else if (percent >= 80) {
            return "c";
        }
        else if (percent >= 60) {
            return "§e";
        }
        return "§a";
    }
}
