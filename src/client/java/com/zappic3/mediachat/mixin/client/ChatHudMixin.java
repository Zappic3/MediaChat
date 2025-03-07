package com.zappic3.mediachat.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zappic3.mediachat.MediaElement;
import com.zappic3.mediachat.RandomString;
import com.zappic3.mediachat.RawTextCollector;
import com.zappic3.mediachat.ui.FavoriteWidget;
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
import org.slf4j.Logger;
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
import static com.zappic3.mediachat.MediaMessageUtility.*;

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

    @Shadow @Final private static Logger LOGGER;
    @Unique
    private List<UUID> renderedMediaElements = new ArrayList<>();
    @Unique
    private boolean hasRenderedMediaElement = false;

    @Redirect(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"))
    public int drawTextWithShadow(DrawContext instance, TextRenderer textRenderer, OrderedText text, int x, int y, int color) {
        String plainMessage = orderedTextToString(text);
        if (plainMessage == null) {
            LOGGER.info("null");
        }
        // clear the currently hovered message if it's not currently visible (aka it didn't render this cycle)
        if (getLowestMessage().content().equals(text)) { // the if statement checks that this is the last message being rendered, so that this will only run once every render-cycle
            if (MediaElement.hovered() != null && !(renderedMediaElements.contains(MediaElement.hovered().elementId()))) {
                MediaElement.hovered(null);
                FavoriteWidget.resetHover();
            }
            renderedMediaElements.clear();
            // clear the current hovered message, if no message is being hovered
            if (!hasRenderedMediaElement && MediaElement.hovered() != null) {
                MediaElement.hovered(null);
                FavoriteWidget.resetHover();
            }
            hasRenderedMediaElement = false;
        }

        // if the message contains the media starting element, isolate the message
        if (plainMessage.contains(CONFIG.startMediaUrl()) && !messageHasTag(text, MESSAGE_TAG.MessageID) && !isMediaMessage(plainMessage, true)) {
            isolateMediaMessage(getMessagePos(text));
        }

        int targetMessageLineCount = targetMessageLineCount(text);

        if (isMediaMessage(plainMessage, true) && !messageHasTag(text, MESSAGE_TAG.BufferGenerated)) {
            // init newly received media message
            for (int i = 0; i < visibleMessages.size(); ++i) {
                ChatHudLine.Visible visible = visibleMessages.get(i);
                if (visible.content() == text && !messageHasTag(text, MESSAGE_TAG.BufferGenerated)) {
                    initMessageStructure(visibleMessages, i);
                    break;
                }
            }
        } else if (targetMessageLineCount != -1 && ((messageHasTagValue(text, MESSAGE_TAG.BufferGenerated) && (Integer.parseInt(getMessageTagValue(text, MESSAGE_TAG.BufferGenerated)) != targetMessageLineCount))
                || (messageHasTagValue(text, MESSAGE_TAG.LowestOfBuffer) && !plainMessage.startsWith((targetMessageLineCount-1)+"#")))) {

            //the message height has been changed in the options, needs to be corrected
            int oldSize = -1;
            if ((messageHasTagValue(text, MESSAGE_TAG.BufferGenerated) && (Integer.parseInt(getMessageTagValue(text, MESSAGE_TAG.BufferGenerated)) != targetMessageLineCount))) {
                if (messageHasTagValue(text, MESSAGE_TAG.MaxLines)) {

                }
                oldSize = Integer.parseInt(getMessageTagValue(text, MESSAGE_TAG.BufferGenerated));
            } else {
                int index = plainMessage.indexOf("#");
                if (index != -1) {
                    if (!plainMessage.substring(0, index).isEmpty()) {
                        oldSize = Integer.parseInt(plainMessage.substring(0, index)) + 1;
                    }
                }
            }

            //get the position of the lowest line of this MediaMessage
            String messageId = getMessageTagValue(text, MESSAGE_TAG.MessageID);
            int lowestOfBufferIndex = -1;

            for (int i = 0; i < visibleMessages.size(); ++i) {
                ChatHudLine.Visible visible = visibleMessages.get(i);
                OrderedText message = visible.content();
                String currentMessageId = getMessageTagValue(message, MESSAGE_TAG.MessageID);

                if (currentMessageId != null && currentMessageId.equals(messageId)) {
                    if (messageHasTagValue(message, MESSAGE_TAG.LowestOfBuffer)) {
                        lowestOfBufferIndex = i;
                        break;
                    }
                }
            }

            if (oldSize != -1 && lowestOfBufferIndex != -1) {
                int newSize = targetMessageLineCount;
                ChatHudLine.Visible lowestOfBuffer = visibleMessages.get(lowestOfBufferIndex);

                if (newSize > oldSize) {
                    // add new buffer lines
                    for (int i = oldSize; i < newSize; i++) {
                        OrderedText message = addMessageTagValue((i-1)+"", MESSAGE_TAG.MessageID, messageId);
                        ChatHudLine.Visible visible = new ChatHudLine.Visible(lowestOfBuffer.addedTime(), addMessageTag(message, MESSAGE_TAG.Buffer), lowestOfBuffer.indicator(), false);
                        visibleMessages.add(lowestOfBufferIndex+1, visible);
                    }
                } else {
                    //remove buffer lines
                    for (int i = oldSize; i > newSize; i--) {
                        visibleMessages.remove(lowestOfBufferIndex+1);
                    }
                }
                // update lowest of buffer with new value
                String[] parts = orderedTextToString(lowestOfBuffer.content()).split("#", 2);
                String newContent = newSize-1 + "#" + parts[1];
                ChatHudLine.Visible editedLowestOfBuffer = new ChatHudLine.Visible(lowestOfBuffer.addedTime(), stringToOrderedText(newContent), lowestOfBuffer.indicator(), lowestOfBuffer.endOfEntry());
                visibleMessages.set(lowestOfBufferIndex, editedLowestOfBuffer);

                // update top of buffer with new value
                int topOfBufferIndex = lowestOfBufferIndex + newSize;
                ChatHudLine.Visible oldTopOfBuffer = visibleMessages.get(topOfBufferIndex);
                OrderedText newTopOfBufferContent = setMessageTagValue(oldTopOfBuffer.content(), MESSAGE_TAG.BufferGenerated, newSize+"");
                ChatHudLine.Visible newTopOfBuffer = new ChatHudLine.Visible(oldTopOfBuffer.addedTime(), newTopOfBufferContent, oldTopOfBuffer.indicator(), oldTopOfBuffer.endOfEntry());
                visibleMessages.set(topOfBufferIndex, newTopOfBuffer);
            }

        } else if (messageHasTag(text, MESSAGE_TAG.LowestOfBuffer) || isLowestMessage(text)) {
            // render the image if the current message is the lowest visible of the "message chain"
            String imageSource = "";
            int lineShift = 0;

            if (messageHasTag(text, MESSAGE_TAG.LowestOfBuffer)) {
                imageSource = getMessageTagValue(text, MESSAGE_TAG.LowestOfBuffer);
            } else {
                OrderedText lowestVisibleMsg = getLowestMessage().content();
                String messageId = getMessageTagValue(lowestVisibleMsg, MESSAGE_TAG.MessageID);
                for (int i = scrolledLines; i >= 0; i--) {
                    OrderedText currentMsg = visibleMessages.get(i).content();
                    if ((messageHasTag(currentMsg, MESSAGE_TAG.LowestOfBuffer)) && Objects.equals(getMessageTagValue(currentMsg, MESSAGE_TAG.MessageID), messageId)) {
                        imageSource  = getMessageTagValue(currentMsg, MESSAGE_TAG.LowestOfBuffer);
                        lineShift = scrolledLines - i;
                        break;
                    }
                }
            }

            y = y + calculateChatHeight(lineShift);
            int alpha = (color >> 24) & 0xFF;
            int y1 = y + calculateChatHeight(1);
            int y2 = y-calculateChatHeight(targetMessageLineCount);
            MediaElement mediaElement = MediaElement.of(imageSource);
            // set media element message tag value for later use
            renderedMediaElements.add(mediaElement.elementId());

            float ySpace = Math.abs(y1-y2);
            float xSpace = client.inGameHud.getChatHud().getWidth() * CONFIG.maxMediaWidth();
            int heightBuffer = 5;
            boolean textureHovered = renderTextureAndCheckIfHovered(instance, client, mediaElement, x, y2, mediaElement.width(), mediaElement.height(), xSpace, ySpace, heightBuffer, alpha / 255.0F);
            updateHoveredStatus(mediaElement, textureHovered);

            // calculate and update the maximally necessary vertical height (number of lines)
            float maxNeededHeight = (mediaElement.height()*4) * (xSpace / ((mediaElement.width()*4)+heightBuffer)); // the 4 is the same as the value applied in the renderTextureAndCheckIfHovered method.
            int maxNeededChatLines = (int) Math.ceil(maxNeededHeight / calculateChatHeight(1))-1; // subtract one line, because the first line doesn't count towards this limit

            int index = plainMessage.indexOf("#");
            if (index != -1) {
                int topMessageIndex = getMessagePos(text) + Integer.parseInt(plainMessage.substring(0, index))+1;
                ChatHudLine.Visible visible = visibleMessages.get(topMessageIndex);
                OrderedText topMessageContent = visible.content();
                if (messageHasTagValue(topMessageContent, MESSAGE_TAG.MaxLines)) {
                    int oldMaxLines = Integer.parseInt(getMessageTagValue(topMessageContent, MESSAGE_TAG.MaxLines));
                    if (oldMaxLines != maxNeededChatLines) {
                        OrderedText newTopMessage = setMessageTagValue(topMessageContent, MESSAGE_TAG.MaxLines, maxNeededChatLines+"");
                        visibleMessages.set(topMessageIndex, new ChatHudLine.Visible(visible.addedTime(), newTopMessage, visible.indicator(), visible.endOfEntry()));
                    }
                } else {
                    OrderedText newTopMessage = addMessageTagValue(topMessageContent, MESSAGE_TAG.MaxLines, maxNeededChatLines+"");
                    visibleMessages.set(topMessageIndex, new ChatHudLine.Visible(visible.addedTime(), newTopMessage, visible.indicator(), visible.endOfEntry()));
                }
            }

            // display image
            if (CONFIG.debugOptions.renderHiddenChatMessages()) {
                instance.drawTextWithShadow(textRenderer, text, x, y, color);
            }

        } else {
            if (!(messageHasTag(text, MESSAGE_TAG.MessageID)) || CONFIG.debugOptions.renderHiddenChatMessages()) {
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
                if (orderedTextToString(currentMsg).contains(CONFIG.endMediaUrl())) {
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
        return  (getLowestMessage().content() == text) && (messageHasTag(text, MESSAGE_TAG.MessageID));
    }

    @Unique
    private ChatHudLine.Visible getLowestMessage() {
        return visibleMessages.get(scrolledLines);
    }

    // This method adds buffer chat lines and adds tags to messages
    @Unique
    private void initMessageStructure(List<ChatHudLine.Visible> messageList, int currentMessagePos) {
        ChatHudLine.Visible currentMessage = messageList.get(currentMessagePos);
        String currentMessageContent = orderedTextToString(currentMessage.content());
        Matcher matcher = Pattern.compile(getMediaMessageRegex()).matcher(currentMessageContent);
        //todo replace this default image
        String mediaUrl = "https://www.minecraft.net/content/dam/games/minecraft/screenshots/PLAYTOGETHERPDPScreenshotRefresh2024_exitingPortal_01.png"; // default image
        if (matcher.find()) {
            mediaUrl = matcher.group(1);
            if (mediaUrl == null) {
                mediaUrl = matcher.group(2);
            }
        }
        String messageChainId = randomStringGenerator.nextString();
        int lineCount = CONFIG.mediaChatHeight();
        if (lineCount >= 1) {
            OrderedText sanitizedText = stringToOrderedText(currentMessageContent.substring(CONFIG.startMediaUrl().length(), CONFIG.endMediaUrl().length())); // remove start and end media tag to prevent infinite loops
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

    @Unique
    private int targetMessageLineCount(OrderedText text) {
        OrderedText topMessage = null;
        int currentMessageIndex = getMessagePos(text);


        if (messageHasTag(text, MESSAGE_TAG.MessageID) && currentMessageIndex != -1) {
            if (!messageHasTag(text, MESSAGE_TAG.BufferGenerated)) {
                String textString = orderedTextToString(text);
                int index = textString.indexOf("#");
                if (index != -1) {
                    int topMessageIndex = currentMessageIndex + Integer.parseInt(textString.substring(0, index))+1;
                    ChatHudLine.Visible visible = visibleMessages.get(topMessageIndex);
                    topMessage = visible.content();
                }
            } else {
                topMessage = text;
            }

            if (topMessage != null) {
                String maxLines = null;
                if (messageHasTagValue(topMessage, MESSAGE_TAG.MaxLines)) {
                    maxLines = getMessageTagValue(topMessage, MESSAGE_TAG.MaxLines);
                }

                if (maxLines != null) {
                    return Math.min(Integer.parseInt(maxLines), CONFIG.mediaChatHeight());
                }
                return CONFIG.mediaChatHeight();
            }
        }
        return -1;
    }

    // todo: find out why images with transparency lose quality on hover. Example: https://cdn.pixabay.com/photo/2017/09/01/00/15/png-2702691_1280.png
    @Unique
    private boolean renderTextureAndCheckIfHovered(DrawContext context, MinecraftClient client,  MediaElement mediaElement, int x, int y, int width, int height, float maxWidth, float maxHeight, float heightBuffer, float opacity) {
        Identifier texture = mediaElement.currentFrame();
        boolean isTextureHovered = false;
        int corrected_width = 4 * width;       // IDK why the size needs to be multiplied by 4
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

        // run when image is hovered
        int borderThickness = (int) (2 / finalScaleFactor);
        if ((MinecraftClient.getInstance().currentScreen instanceof ChatScreen && isHovered)) {
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

            // render favorite widget in the corner
            if (isTextureHovered) {
                float widgetScale = 2.2f;

                int widgetLeftBorder = (int)(corrected_width-(FavoriteWidget.defaultSize*widgetScale));
                int widgetBottomBorder = (int)(0+(FavoriteWidget.defaultSize*widgetScale));

                boolean widgetHovered = inverseMouseX >= widgetLeftBorder && inverseMouseX <= corrected_width-borderThickness &&
                        inverseMouseY >= limitedTopBorderHeight+borderThickness && inverseMouseY <= widgetBottomBorder;

                if (widgetBottomBorder >= limitedTopBorderHeight) {
                    FavoriteWidget.configure(mediaElement.source(), widgetLeftBorder, 0, widgetScale)
                            .renderWidget(context, widgetHovered);
                }
            }
            context.disableScissor();
        }


        // Restore previous state
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        matrices.pop();

        return isTextureHovered;
    }
}