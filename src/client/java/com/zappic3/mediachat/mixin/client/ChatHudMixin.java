package com.zappic3.mediachat.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zappic3.mediachat.MediaElement;
import com.zappic3.mediachat.RandomString;
import com.zappic3.mediachat.RawTextCollector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.zappic3.mediachat.MediaChat.CONFIG;
import static com.zappic3.mediachat.MediaChat.LOGGER;
import static com.zappic3.mediachat.Utility.*;
import static com.zappic3.mediachat.Utility.MessageHasTagValue;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Unique
    MinecraftClient client = MinecraftClient.getInstance();

    @Unique
    final private static RandomString randomStringGenerator = new RandomString();

    @Shadow
    protected abstract int getLineHeight();

    @Shadow
    private int scrolledLines;

    @Final
    @Shadow
    private List<ChatHudLine.Visible> visibleMessages;

    @Shadow @Final
    private List<ChatHudLine> messages;

    @Unique
    private List<UUID> renderedMediaElements = new ArrayList<>();
    @Unique
    private boolean hasRenderedMediaElement = false;

    @Redirect(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"))
    public int drawTextWithShadow(DrawContext instance, TextRenderer textRenderer, OrderedText text, int x, int y, int color) {
        String plainMessage = OrderedTextToString(text);

        // clear the currently hovered message if it's not currently visible (aka it didn't render this cycle)
        if (getLowestMessage().content().equals(text)) { // the if statement checks that this is the last message being rendered, so that this will only run once every render-cycle
            if (MediaElement.hovered() != null && !(renderedMediaElements.contains(MediaElement.hovered().elementId()))) {
                MediaElement.hovered(null);
            }
            renderedMediaElements.clear();
            // clear the current hovered message, if no message is being hovered
            if (!hasRenderedMediaElement && MediaElement.hovered() != null) {
                MediaElement.hovered(null);
            }
            hasRenderedMediaElement = false;
        }



        // if the message contains the media starting element, isolate the message
        if (plainMessage.contains(CONFIG.startMediaUrl()) && !MessageHasTag(text, MESSAGE_TAG.MessageID) && !isMediaMessage(plainMessage, true)) {
            isolateMediaMessage(getMessagePos(text));
        }

        if (isMediaMessage(plainMessage, true) && !MessageHasTag(text, MESSAGE_TAG.BufferGenerated)) {
            // init newly received media message
            for (int i = 0; i < visibleMessages.size(); ++i) {
                ChatHudLine.Visible visible = visibleMessages.get(i);
                if (visible.content() == text && !MessageHasTag(text, MESSAGE_TAG.BufferGenerated)) {
                    initMessageStructure(visibleMessages, i);
                    break;
                }
            }

        } else if (MessageHasTagValue(text, MESSAGE_TAG.BufferGenerated) && (Integer.parseInt(getMessageTagValue(text, MESSAGE_TAG.BufferGenerated)) != CONFIG.mediaChatHeight())) {
            //the message height has been changed in the options, needs to be corrected
            // todo this is broken (probably because of the new message detection & isolation)
            // todo: rethink the chat line buffer height approach, because wide images have too much buffer, which makes it look wierd (maybe buffer is max, but use less lines if they are not needed?)
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
                initMessageStructure(newVisibleMessages, currentMessageIndex);
                visibleMessages.clear();
                visibleMessages.addAll(newVisibleMessages);
            } else {
                LOGGER.error("Error adjusting media buffer size!\nIf this error persists, try relogging / clearing chat.");
            }


        } else if (MessageHasTag(text, MESSAGE_TAG.LowestOfBuffer) || isLowestMessage(text)) {
            // render the image, if the current message is the lowest visible of the "message chain"
            String imageSource = "";
            int lineShift = 0;

            if (MessageHasTag(text, MESSAGE_TAG.LowestOfBuffer)) {
                imageSource = getMessageTagValue(text, MESSAGE_TAG.LowestOfBuffer);
            } else {
                OrderedText lowestVisibleMsg = getLowestMessage().content();
                String messageId = getMessageTagValue(lowestVisibleMsg, MESSAGE_TAG.MessageID);
                for (int i = scrolledLines; i >= 0; i--) {
                    OrderedText currentMsg = visibleMessages.get(i).content();
                    if ((MessageHasTag(currentMsg, MESSAGE_TAG.LowestOfBuffer)) && Objects.equals(getMessageTagValue(currentMsg, MESSAGE_TAG.MessageID), messageId)) {
                        imageSource  = getMessageTagValue(currentMsg, MESSAGE_TAG.LowestOfBuffer);
                        lineShift = scrolledLines - i;
                        break;
                    }
                }
            }

            y = y + calculateChatHeight(lineShift);
            int alpha = (color >> 24) & 0xFF;
            int y1 = y + calculateChatHeight(1);
            int y2 = y-calculateChatHeight(CONFIG.mediaChatHeight());
            MediaElement mediaElement = MediaElement.of(imageSource);
            // set media element message tag value for later use
            renderedMediaElements.add(mediaElement.elementId());

            float ySpace = Math.abs(y1-y2);
            float xSpace = client.inGameHud.getChatHud().getWidth() * CONFIG.maxMediaWidth();
            boolean textureHovered = renderTextureAndCheckIfHovered(instance, client, mediaElement.currentFrame(), x, y2, mediaElement.width(), mediaElement.height(), xSpace, ySpace, 5, alpha / 255.0F);
            updateHoveredStatus(mediaElement, textureHovered);

            if (CONFIG.debugOptions.renderHiddenChatMessages()) {
                instance.drawTextWithShadow(textRenderer, text, x, y, color);
            }

        } else {
            if (!(MessageHasTag(text, MESSAGE_TAG.MessageID)) || CONFIG.debugOptions.renderHiddenChatMessages()) {
                instance.drawTextWithShadow(textRenderer, text, x, y, color);
            }
        }
        return 0;
    }

    @Unique
    private void updateHoveredStatus(MediaElement element, boolean isHovered) {
        if (isHovered) {
            hasRenderedMediaElement = true;
            MediaElement.hovered(element);
            MediaElement.renderTooltip();
        }
    }

    @Unique
    private void isolateMediaMessage(int messagePos) {
        ChatHudLine.Visible visible = visibleMessages.get(messagePos);
        List<RawTextCollector.CharacterWithStyle> concMsg = RawTextCollector.removeLeadingWhitespace(OrderedTextToCharacterWithStyle(visible.content()));
        List<Integer> toDelete = new ArrayList<>();
        toDelete.add(messagePos);
        if (!(RawTextCollector.convertToPlainString(concMsg).contains(CONFIG.endMediaUrl()))) {
            for (int i = messagePos - 1; i >= 0; i--) {
                OrderedText currentMsg = visibleMessages.get(i).content();
                List<RawTextCollector.CharacterWithStyle> currentMsgAsChars = OrderedTextToCharacterWithStyle(currentMsg);
                RawTextCollector.removeLeadingWhitespace(currentMsgAsChars);
                concMsg.addAll(currentMsgAsChars);
                toDelete.add(i);
                if (OrderedTextToString(currentMsg).contains(CONFIG.endMediaUrl())) {
                    break;
                }
            }
        }
        // only proceed, if the string contains a valid mediaMessage
        String concString = RawTextCollector.convertToPlainString(concMsg);
        if (isMediaMessage(concString, false)) {
            for (Integer pos : toDelete) {
                if (pos >= 0 && pos < visibleMessages.size()) {
                    visibleMessages.set(pos, null);
                }
            }

            List<RawTextCollector.CharacterWithStyle> concMsgCharList = concMsg;
            List<RawTextCollector.CharacterWithStyle> beforeMediaMessage = new LinkedList<>();
            List<RawTextCollector.CharacterWithStyle> mediaMessage = new LinkedList<>();
            List<RawTextCollector.CharacterWithStyle> afterMediaMessage = new LinkedList<>();

            Pattern pattern = Pattern.compile(getMediaMessageRegex(), Pattern.DOTALL);
            Matcher matcher = pattern.matcher(concString);

            if (matcher.find()) {
                int startIndex = matcher.start();
                int endIndex = matcher.end();

                for (int i = 0; i < startIndex; i++) {
                    beforeMediaMessage.add(concMsgCharList.get(i));
                }
                for (int i = startIndex; i < endIndex; i++) {
                    mediaMessage.add(concMsgCharList.get(i));
                }
                for (int i = endIndex; i < concString.length(); i++) {
                    afterMediaMessage.add(concMsgCharList.get(i));
                }

                if (!beforeMediaMessage.isEmpty()) {
                    OrderedText beforeMediaMessageText = characterWithStyleToOrderedText(beforeMediaMessage);
                    visibleMessages.add(messagePos, new ChatHudLine.Visible(visible.addedTime(), beforeMediaMessageText, visible.indicator(), true));
                }
                if (!mediaMessage.isEmpty()) {
                    OrderedText mediaMessageText = characterWithStyleToOrderedText(mediaMessage);
                    visibleMessages.add(messagePos, new ChatHudLine.Visible(visible.addedTime(), mediaMessageText, visible.indicator(), true));
                }
                if (!afterMediaMessage.isEmpty()) {
                    OrderedText afterMediaMessageText = characterWithStyleToOrderedText(afterMediaMessage);
                    visibleMessages.add(messagePos, new ChatHudLine.Visible(visible.addedTime(), afterMediaMessageText, visible.indicator(), true));
                }

            } else {
                LOGGER.error("Error formating media message");
            }

            // Remove the previously added null elements
                visibleMessages.removeIf(Objects::isNull);
        }
    }

    @Unique
    private int getMessagePos(OrderedText text) {
        for (int i = 0; i < visibleMessages.size(); ++i) {
            ChatHudLine.Visible visible = visibleMessages.get(i);
            if (visible.content() == text) {
                return i;
            }
        }
        return -1;
    }

    @Unique
    private boolean isLowestMessage(OrderedText text) {
        return  (getLowestMessage().content() == text) && (MessageHasTag(text, MESSAGE_TAG.MessageID));
    }

    @Unique
    private ChatHudLine.Visible getLowestMessage() {
        return visibleMessages.get(scrolledLines);
    }

    // This method adds buffer chat lines and adds tags to messages
    @Unique
    private void initMessageStructure(List<ChatHudLine.Visible> messageList, int currentMessagePos) {
        ChatHudLine.Visible currentMessage = messageList.get(currentMessagePos);
        String currentMessageContent = OrderedTextToString(currentMessage.content());
        Matcher matcher = Pattern.compile(getMediaMessageRegex()).matcher(currentMessageContent);
        //todo replace this default image
        String mediaUrl = "https://www.minecraft.net/content/dam/games/minecraft/screenshots/PLAYTOGETHERPDPScreenshotRefresh2024_exitingPortal_01.png"; // default image
        if (matcher.find()) {
            mediaUrl = matcher.group(1);
        }
        String messageChainId = randomStringGenerator.nextString();
        int lineCount = CONFIG.mediaChatHeight();
        if (lineCount >= 1) {
            OrderedText sanitizedText = StringToOrderedText(currentMessageContent.substring(CONFIG.startMediaUrl().length(), CONFIG.endMediaUrl().length())); // remove start and end media tag to prevent infinite loops
            messageList.add(currentMessagePos+1, new ChatHudLine.Visible(currentMessage.addedTime(), addMessageTagValues(sanitizedText, Arrays.asList(MESSAGE_TAG.MessageID, MESSAGE_TAG.BufferGenerated), Arrays.asList(messageChainId, CONFIG.mediaChatHeight()+"")), currentMessage.indicator(), true));
            OrderedText message = addMessageTagValue("0", MESSAGE_TAG.MessageID, messageChainId);
            messageList.set(currentMessagePos, new ChatHudLine.Visible(currentMessage.addedTime(), addMessageTag(message, MESSAGE_TAG.Buffer), currentMessage.indicator(), false));

            int i = 1;
            while (i < lineCount) {
                message = addMessageTagValue(i+"", MESSAGE_TAG.MessageID, messageChainId);
                messageList.add(currentMessagePos, new ChatHudLine.Visible(currentMessage.addedTime(), addMessageTag(message, MESSAGE_TAG.Buffer), currentMessage.indicator(), false));
                i++;
            }
            // modify last message;
            message = addMessageTagValue(i-1+"", MESSAGE_TAG.MessageID, messageChainId);
            messageList.set(currentMessagePos, new ChatHudLine.Visible(currentMessage.addedTime(), addMessageTagValue(message, MESSAGE_TAG.LowestOfBuffer, mediaUrl), currentMessage.indicator(), false));
        }
    }

    @Unique
    private int calculateChatHeight(int numberOfLines) {
        MinecraftClient client = MinecraftClient.getInstance();
        double chatLineSpacing = (Double) client.options.getChatLineSpacing().getValue();
        int lineHeight = this.getLineHeight();
        return (int) ((chatLineSpacing + lineHeight) * numberOfLines);
    }

    // todo: find out why images with transparency lose quality on hover. Example: https://cdn.pixabay.com/photo/2017/09/01/00/15/png-2702691_1280.png
    @Unique
    private boolean renderTextureAndCheckIfHovered(DrawContext context, MinecraftClient client,  Identifier texture, int x, int y, int width, int height, float maxWidth, float maxHeight, float heightBuffer, float opacity) {
        boolean isTextureHovered = false;
        int corrected_width = 4 * width;
        int corrected_height = 4 * height;

        MatrixStack matrices = context.getMatrices();
        matrices.push();

        // Opacity
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, opacity);

        // position & scaling
        int yWithBuffer = (int) (y + (heightBuffer / 2));
        matrices.translate(x, yWithBuffer, 1.0F);
        float widthRatio = maxWidth / corrected_width;
        float heightRatio =  (maxHeight - heightBuffer) / corrected_height;
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

        // ########################################################
        // clamp numbers inside the chatbox
        int relativeChatTopBorder = (int) ((ScissorY - yWithBuffer) / finalScaleFactor);
        int relativeChatBottomBorder = (int) (((windowHeight-chatDistanceFromWindowBottom) - yWithBuffer) / finalScaleFactor);
        int limitedTopBorderHeight = Math.max(0, relativeChatTopBorder);
        int limitedBottomBorderHeight = Math.min(corrected_height, relativeChatBottomBorder);
        // check mouse hovering
        double mouseX = client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
        double mouseY = client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
        double inverseMouseX = (mouseX - x - 5) / finalScaleFactor;
        double inverseMouseY = (mouseY - y) / finalScaleFactor;
        boolean isHovered = inverseMouseX >= 0 && inverseMouseX <= corrected_width &&
                inverseMouseY >= limitedTopBorderHeight && inverseMouseY <= limitedBottomBorderHeight;

        if ((MinecraftClient.getInstance().currentScreen instanceof ChatScreen && isHovered)) {
            int borderThickness = (int) (2 / finalScaleFactor);
            // border highlight lines
            context.fill(0, limitedTopBorderHeight -borderThickness, -borderThickness, limitedBottomBorderHeight+borderThickness, 0xFFFFFFFF); // y-left
            context.fill(corrected_width, limitedTopBorderHeight -borderThickness, corrected_width+borderThickness, limitedBottomBorderHeight+borderThickness, 0xFFFFFFFF); // y-right
            context.fill(0, limitedTopBorderHeight, corrected_width, limitedTopBorderHeight -borderThickness, 0xFFFFFFFF); // x-top
            context.fill(0, limitedBottomBorderHeight, corrected_width, limitedBottomBorderHeight+borderThickness, 0xFFFFFFFF); // x-bottom
            isTextureHovered = true;

        }
        // ########################################################


        context.enableScissor(0, ScissorY, client.getWindow().getWidth(), windowHeight-chatDistanceFromWindowBottom);
        if (CONFIG.debugOptions.displayScissorArea()) {context.fill(-999999999, -999999999, 999999999, 999999999, 0x66FF0000);}

        // draw image
        if (CONFIG.debugOptions.renderImages()) {
            context.drawTexture(texture, 0, 0, 0, 0, corrected_width, corrected_height, corrected_width, corrected_height);
        }

        context.disableScissor();

        // Restore previous state
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        matrices.pop();

        return isTextureHovered;
    }
}