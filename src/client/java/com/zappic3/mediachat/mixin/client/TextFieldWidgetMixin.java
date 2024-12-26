package com.zappic3.mediachat.mixin.client;

import com.zappic3.mediachat.IMediaChatPaste;
import com.zappic3.mediachat.MediaMessageUtility;
import com.zappic3.mediachat.Utility;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(TextFieldWidget.class)
public class TextFieldWidgetMixin implements IMediaChatPaste {
    @Shadow
    private boolean editable;

    @Shadow
    public void write(String text) {}


    @Unique
    private boolean isMediaChatPasteEnabled = false;

    public void mediaChat$enableMediaChatPaste(boolean enabled) {
        this.isMediaChatPasteEnabled = enabled;
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    public void keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (isMediaChatPasteEnabled) {
            if (keyCode == 86 && Screen.hasControlDown() && Screen.hasAltDown()) {
                if (editable) {
                    this.write(wrapUrls(MinecraftClient.getInstance().keyboard.getClipboard()));
                }
                cir.setReturnValue(true);
            }
        }
    }

    @Unique
    private String wrapUrls(String input) {
        String regex = MediaMessageUtility.getURLDetectionRegex();

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);

        StringBuilder result = new StringBuilder(input);

        int offset = 0;
        while (matcher.find()) {
            int start = matcher.start() + offset;
            int end = matcher.end() + offset;

            result.insert(start, "[");
            result.insert(end + 1, "]");

            offset += 2;
        }
        return result.toString();
    }
}
