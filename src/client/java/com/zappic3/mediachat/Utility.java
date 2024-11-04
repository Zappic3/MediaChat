package com.zappic3.mediachat;

import com.mojang.logging.LogUtils;
import com.zappic3.mediachat.mixin.client.ChatScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.zappic3.mediachat.MediaChat.CONFIG;
import static com.zappic3.mediachat.MediaChat.MOD_ID;

public class Utility {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final static String MESSAGE_TAG_FORMAT = "#%s;";
    private final static String MESSAGE_TAG_VALUE_FORMAT = "#%s;%s";
    private final static String MESSAGE_TAG_VALUE_REGEX = "#%s;([^#]+)"; // detect tags with tag value
    private final static String MESSAGE_TAG_REGEX = "#%s;[^#]*";         // detect any tag

    private final static String DETECT_MEDIA_MESSAGE_REGEX = "((?:https?:\\/\\/)?[-a-zA-Z0-9@:%._\\+~#=*]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b[-a-zA-Z0-9()@:%_\\+.~#?&\\/\\/=*]*)";

    public enum MESSAGE_TAG {
        BufferGenerated,
        Buffer,
        LowestOfBuffer,
        MessageID
    }

    // #######################
    // Chat Message Management
    // #######################
    private static String tagToString(MESSAGE_TAG tag) {
        return CONFIG.debugOptions.useNameInsteadOfID() ? tag.toString() : tag.ordinal()+"";
    }

    public static ChatHudLine.Visible addMessageTag(ChatHudLine.Visible text, MESSAGE_TAG tag) {
        return new ChatHudLine.Visible(text.addedTime(), addMessageTag(text.content(), tag), text.indicator(), text.endOfEntry());
    }

    public static OrderedText addMessageTags(String text, List<MESSAGE_TAG> tags) {
        for (MESSAGE_TAG tag : tags) {
            text = OrderedTextToString(addMessageTag(text, tag));
        }
        return StringToOrderedText(text);
    }

    public static OrderedText addMessageTag(String text, MESSAGE_TAG tag) {
        String resultText = text + MESSAGE_TAG_FORMAT.formatted(tagToString(tag));
        return StringToOrderedText(resultText);
    }

    public static OrderedText addMessageTag(OrderedText text, MESSAGE_TAG tag) {
        String plainText = OrderedTextToString(text);
        return addMessageTag(plainText, tag);
    }

    public static OrderedText addMessageTagValues(OrderedText text, List<MESSAGE_TAG> tags, List<String> values) {
        if (tags.size() != values.size()) {return null;}
        for (int i = 0; i < tags.size(); i++) {
            String value = values.get(i);
            MESSAGE_TAG tag = tags.get(i);
            text = addMessageTagValue(text, tag, value);
        }
        return text;
    }

    public static OrderedText addMessageTagValue(OrderedText text, MESSAGE_TAG tag, String value) {
        String plainText = OrderedTextToString(text);
        return addMessageTagValue(plainText, tag, value);
    }

    public static OrderedText addMessageTagValue(String text, MESSAGE_TAG tag, String value) {
        return StringToOrderedText(text + MESSAGE_TAG_VALUE_FORMAT.formatted(tagToString(tag), value));
    }

    public static boolean MessageHasTag(OrderedText text, MESSAGE_TAG tag) {
        String plainText = OrderedTextToString(text);
        return plainText.contains(MESSAGE_TAG_FORMAT.formatted(tagToString(tag)));
    }

    public static boolean MessageHasTagValue(OrderedText text, MESSAGE_TAG tag) {
        if (MessageHasTag(text, tag)) {
            Pattern pattern = Pattern.compile(MESSAGE_TAG_VALUE_REGEX.formatted(tagToString(tag)));
            Matcher matcher = pattern.matcher(OrderedTextToString(text));
            return matcher.find();
        }
        return false;
    }

    public static String getMessageTagValue(OrderedText text, MESSAGE_TAG tag) {
        Pattern pattern = Pattern.compile(MESSAGE_TAG_VALUE_REGEX.formatted(tagToString(tag)));
        Matcher matcher = pattern.matcher(OrderedTextToString(text));
        return matcher.find() ? matcher.group(1) : null;
    }

    public static OrderedText MessageRemoveTag(OrderedText text, MESSAGE_TAG tag) {
        String plainText = OrderedTextToString(text);
        String resultText = plainText.replaceAll(MESSAGE_TAG_REGEX.formatted(tagToString(tag)), "");
        return StringToOrderedText(resultText);
    }

    public static String OrderedTextToString(OrderedText text) {
        RawTextCollector textCollector = new RawTextCollector();
        text.accept(textCollector);
        return textCollector.getText();
    }

    public static List<RawTextCollector.CharacterWithStyle> OrderedTextToCharacterWithStyle(OrderedText text) {
        RawTextCollector textCollector = new RawTextCollector();
        text.accept(textCollector);
        return textCollector.getCharactersWithStyles();
    }

    public static OrderedText StringToOrderedText(String text) {
        return Language.getInstance().reorder(Text.of(text));
    }

    public static boolean isMediaMessage(String message, Boolean exactMatch) {
        String fullRegex =  Pattern.quote(CONFIG.startMediaUrl()) + DETECT_MEDIA_MESSAGE_REGEX + Pattern.quote(CONFIG.endMediaUrl());
        Pattern regex = Pattern.compile(fullRegex);
        return exactMatch ? regex.matcher(message).matches() : regex.matcher(message).find();
    }

    public static String getMediaMessageRegex() {
        return Pattern.quote(CONFIG.startMediaUrl()) + DETECT_MEDIA_MESSAGE_REGEX + Pattern.quote(CONFIG.endMediaUrl());
    }

    public static OrderedText characterWithStyleToOrderedText(List<RawTextCollector.CharacterWithStyle> chars) {
        List<OrderedText> styledTexts = new ArrayList<>();
        for (RawTextCollector.CharacterWithStyle charWithStyle : chars) {
            OrderedText styledChar = OrderedText.styled(charWithStyle.codePoint(), charWithStyle.style());
            styledTexts.add(styledChar);
        }
        return OrderedText.concat(styledTexts);
    }

    public static String getUrlFromModMessage(String message) {
        Pattern regex = Pattern.compile(DETECT_MEDIA_MESSAGE_REGEX);
        Matcher matcher = regex.matcher(message);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    public static boolean isMouseInRegion(int regionX, int regionY, int regionWidth, int regionHeight) {
        MinecraftClient client = MinecraftClient.getInstance();
        double mouseX = client.mouse.getX();
        double mouseY = client.mouse.getY();

        int scaledWidth = client.getWindow().getScaledWidth();
        int scaledHeight = client.getWindow().getScaledHeight();

        double scaledMouseX = mouseX * scaledWidth / client.getWindow().getWidth();
        double scaledMouseY = mouseY * scaledHeight / client.getWindow().getHeight();

        return (scaledMouseX >= regionX && scaledMouseX <= regionX + regionWidth) && (scaledMouseY >= regionY && scaledMouseY <= regionY + regionHeight);
    }

    public static void insertStringAtCursorPos(String text) {
        if (MinecraftClient.getInstance().currentScreen instanceof ChatScreen screen) {
            TextFieldWidget chatField = ((ChatScreenAccessor) screen).getChatField();
            int oldCursorPos = chatField.getCursor();
            String oldText = chatField.getText();
            chatField.setText(oldText.substring(0, oldCursorPos) + text + oldText.substring(oldCursorPos));
            chatField.setCursor(oldCursorPos + text.length(), false);
        }
    }

    // #######################
    // Texture Loading
    // #######################

    public static Identifier registerTexture(BufferedImage bufferedImage, String filenName) throws Exception {
        // Convert BufferedImage to InputStream for NativeImage
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", baos); // Write the BufferedImage as a PNG into the ByteArrayOutputStream
        InputStream inputStream = new java.io.ByteArrayInputStream(baos.toByteArray());

        // Load NativeImage from InputStream
        NativeImage nativeImage = NativeImage.read(inputStream);

        // Create a NativeImageBackedTexture from the NativeImage
        NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);

        // Generate a valid Identifier for the texture (this part can be a unique mod-generated name)
        Identifier textureId = Identifier.of(MOD_ID, "textures/dynamic/" + filenName.toLowerCase().replace(" ", "_"));

        // Register the texture in the Minecraft texture manager
        MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);

        // Return the Identifier, now registered and usable in-game
        return textureId;
    }
}
