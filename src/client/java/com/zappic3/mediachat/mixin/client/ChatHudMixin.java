package com.zappic3.mediachat.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zappic3.mediachat.RawTextCollector;
import com.zappic3.mediachat.Utility;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

import static com.zappic3.mediachat.MediaChatClient.CONFIG;
import static com.zappic3.mediachat.Utility.*;
import static com.zappic3.mediachat.Utility.MessageHasTag;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    MinecraftClient client = MinecraftClient.getInstance();

    @Shadow
    protected abstract int getLineHeight();

    @Final
    @Shadow
    private List<ChatHudLine.Visible> visibleMessages;

    @Shadow @Final private List<ChatHudLine> messages;

    @Shadow @Final private static Logger LOGGER;

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
            //TODO: Scaling von bereits generierten buffern einfügen

        } else if (MessageHasTag(text, MESSAGE_TAG.LowestOfBuffer)) {
            int renderColor = 0xFF0000;
            int renderColorWithAlpha = (color & 0xFF000000) | renderColor;

            int alpha = (color >> 24) & 0xFF;

            int y1 = y + calculateChatHeight(1);
            int y2 = y-calculateChatHeight(CONFIG.mediaChatHeight());

            double scaling = client.inGameHud.getChatHud().getChatScale();

            //instance.fill(x, y1, x + 100, y2, renderColorWithAlpha);
            Identifier texture = Identifier.of("media-chat", "textures/test.png");
            renderTextureWithOpacity(instance, texture, x, y2/4, 256, 256, alpha / 255.0F);
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
            messageList.set(currentMessagePos, new ChatHudLine.Visible(currentMessage.addedTime(), addMessageTag(i+"", MESSAGE_TAG.LowestOfBuffer), currentMessage.indicator(), false));
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
    private void renderTextureWithOpacity(DrawContext context, Identifier texture, int x, int y, int width, int height, float opacity) {
        // Save the current state
        MatrixStack matrices = context.getMatrices();
        matrices.push();

        // Enable alpha blending
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Set up the opacity
        float alpha = opacity;
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);

        matrices.scale(0.25F, 0.25F, 1.0F);

        // Draw the texture
        context.drawTexture(texture, x, y, 0, 0, width, height);
        LOGGER.info(matrices.peek().toString());


        // Restore the previous state
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        matrices.pop();
    }
}