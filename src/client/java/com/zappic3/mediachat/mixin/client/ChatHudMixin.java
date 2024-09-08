package com.zappic3.mediachat.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zappic3.mediachat.Utility;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

import static com.zappic3.mediachat.MediaChatClient.CONFIG;
import static com.zappic3.mediachat.Utility.*;
import static com.zappic3.mediachat.Utility.MessageHasTag;
import static com.zappic3.mediachat.MediaChat.LOGGER;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    MinecraftClient client = MinecraftClient.getInstance();

    @Shadow
    protected abstract int getLineHeight();

    @Shadow
    private int scrolledLines;

    @Final
    @Shadow
    private List<ChatHudLine.Visible> visibleMessages;

    @Shadow @Final
    private List<ChatHudLine> messages;

    @Redirect(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"))
    public int drawTextWithShadow(DrawContext instance, TextRenderer textRenderer, OrderedText text, int x, int y, int color) {
        String plainMessage = OrderedTextToString(text);
        if (plainMessage.contains("...") && !MessageHasTag(text, MESSAGE_TAG.BufferGenerated)) {
            // find message position in the array of all chat messages and add more lines
            for (int i = 0; i < visibleMessages.size(); ++i) {
                ChatHudLine.Visible visible = visibleMessages.get(i);
                if (visible.content() == text && !MessageHasTag(text, MESSAGE_TAG.BufferGenerated)) {
                    addBufferLines(visibleMessages, i);
                    break;
                }
            }

        } else if (MessageHasTagValue(text, MESSAGE_TAG.BufferGenerated) && (Integer.parseInt(getMessageTagValue(text, MESSAGE_TAG.BufferGenerated)) != CONFIG.mediaChatHeight())) {
            int oldSize = Integer.parseInt(getMessageTagValue(text, MESSAGE_TAG.BufferGenerated));
            int newSize = CONFIG.mediaChatHeight();
            if (newSize < oldSize) {scrolledLines = Math.max(scrolledLines-(oldSize - newSize), 0);}

            List<ChatHudLine.Visible> newVisibleMessages = new ArrayList<>();
            boolean foundCurrentMessage = false;
            int currentMessageIndex = 0;
            boolean finishedCurrentMessage = false;
            for (int i = 0; i < visibleMessages.size()-1; i++) {
                ChatHudLine.Visible visible = visibleMessages.get(visibleMessages.size()-1-i);
                if (!foundCurrentMessage) {
                    if (visible.content() == text) {
                        foundCurrentMessage = true;
                        ChatHudLine.Visible visibleWithoutTag = new ChatHudLine.Visible(
                                visible.addedTime(),
                                MessageRemoveTag(visible.content(), MESSAGE_TAG.BufferGenerated),
                                visible.indicator(),
                                visible.endOfEntry());
                        newVisibleMessages.addFirst(visibleWithoutTag);
                    } else {
                        newVisibleMessages.addFirst(visible);
                    }
                } else if (!finishedCurrentMessage) {
                    if (MessageHasTag(visible.content(), MESSAGE_TAG.LowestOfBuffer)) {
                        finishedCurrentMessage = true;
                    } else if (!MessageHasTag(visible.content(), MESSAGE_TAG.Buffer)) {
                        newVisibleMessages.addFirst(visible);
                    }
                } else {
                    newVisibleMessages.addFirst(visible);
                    currentMessageIndex += 1;
                }
            }
            if (foundCurrentMessage) {
                addBufferLines(newVisibleMessages, currentMessageIndex);
                visibleMessages.clear();
                visibleMessages.addAll(newVisibleMessages);
            } else {
                LOGGER.error("Error adjusting media buffer size!\nIf this error persists, try relogging / clearing chat.");
            }


        } else if (MessageHasTag(text, MESSAGE_TAG.LowestOfBuffer)) {
            int renderColor = 0xFF0000;
            int renderColorWithAlpha = (color & 0xFF000000) | renderColor;

            int alpha = (color >> 24) & 0xFF;

            int y1 = y + calculateChatHeight(1);
            int y2 = y-calculateChatHeight(CONFIG.mediaChatHeight());

            //instance.fill(x, y1, x + 100, y2, renderColorWithAlpha);
            Identifier texture = Identifier.of("media-chat", "textures/test.png");
            float ySpace = Math.abs(y1-y2);
            float xSpace = client.inGameHud.getChatHud().getWidth() * CONFIG.maxMediaWidth();
            renderTexture(instance, client, texture, x, y2, 64, 64, xSpace, ySpace, alpha / 255.0F);
            instance.drawTextWithShadow(textRenderer, text, x, y, color);

        } else {
            instance.drawTextWithShadow(textRenderer, text, x, y, color);
        }
        return 0;
    }

    @Unique
    private void addBufferLines(List<ChatHudLine.Visible> messageList, int currentMessagePos) {
        ChatHudLine.Visible currentMessage = messageList.get(currentMessagePos);
        int lineCount = CONFIG.mediaChatHeight();
        if (lineCount >= 1) {
            messageList.add(currentMessagePos+1, new ChatHudLine.Visible(currentMessage.addedTime(), addMessageTagValue(currentMessage.content(), Utility.MESSAGE_TAG.BufferGenerated, CONFIG.mediaChatHeight()+""), currentMessage.indicator(), true));
            messageList.set(currentMessagePos, new ChatHudLine.Visible(currentMessage.addedTime(), addMessageTag("0", MESSAGE_TAG.Buffer), currentMessage.indicator(), false));

            int i = 1;
            while (i < lineCount) {
                messageList.add(currentMessagePos, new ChatHudLine.Visible(currentMessage.addedTime(), addMessageTag(i+"", MESSAGE_TAG.Buffer), currentMessage.indicator(), false));
                i++;
            }
            // modify last message;
            messageList.set(currentMessagePos, new ChatHudLine.Visible(currentMessage.addedTime(), addMessageTag(i-1+"", MESSAGE_TAG.LowestOfBuffer), currentMessage.indicator(), false));
        }
    }

    @Unique
    private int calculateChatHeight(int numberOfLines) {
        MinecraftClient client = MinecraftClient.getInstance();
        double chatLineSpacing = (Double) client.options.getChatLineSpacing().getValue();
        int lineHeight = this.getLineHeight();
        return (int) ((chatLineSpacing + lineHeight) * numberOfLines);
    }

    @Unique
    private void renderTexture(DrawContext context, MinecraftClient client,  Identifier texture, int x, int y, int width, int height, float maxWidth, float maxHeight, float opacity) {
        int corrected_width = 4 * width;
        int corrected_height = 4 * height;

        MatrixStack matrices = context.getMatrices();
        matrices.push();

        // Opacity
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float alpha = opacity;
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);

        // position & scaling
        matrices.translate(x, y, 1.0F);
        float widthRatio = maxWidth / corrected_width;
        float heightRatio =  maxHeight / corrected_height;
        float finalScaleFactor = Math.min(widthRatio, heightRatio);
        matrices.scale(finalScaleFactor, finalScaleFactor, 1.0F);

        // Obtain chatbox boundaries
        ChatHud chatHud = client.inGameHud.getChatHud();
        int chatHeight = chatHud.getHeight();
        double chatScale = chatHud.getChatScale();
        int windowHeight = client.getWindow().getScaledHeight();
        int chatLineHeight = calculateChatHeight(1);
        int roundedChatHeight = MathHelper.floor(chatHeight / chatLineHeight) * chatLineHeight;
        int chatDistanceFromWindowBottom = 40;
        int ScissorY  = (int)(windowHeight - ((roundedChatHeight * chatScale) + chatDistanceFromWindowBottom));

        // Draw texture
        context.enableScissor(0, ScissorY, client.getWindow().getWidth(), windowHeight);
        //context.fill(-999999999, -999999999, 999999999, 999999999, 0x66FF0000); // just for debugging
        context.drawTexture(texture, 0, 0, 0, 0, corrected_width, corrected_height);
        context.disableScissor();

        // Restore previous state
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        matrices.pop();
    }
}