package com.zappic3.mediachat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utility {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final static String MESSAGE_TAG_FORMAT = "#%s;";
    private final static String MESSAGE_TAG_VALUE_FORMAT = "#%s;%s";
    private final static String MESSAGE_TAG_VALUE_REGEX = "#%s;([^#]+)"; // detect tags with tag value
    private final static String MESSAGE_TAG_REGEX = "#%s;[^#]*";         // detect any tag

    private final static String DETECT_MOD_MESSAGE_REGEX = " \\[(?:https?:\\/\\/)?([-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b[-a-zA-Z0-9()@:%_\\+.~#?&\\/\\/=]*)\\]   ";

    public enum MESSAGE_TAG {
        BufferGenerated,
        Buffer,
        LowestOfBuffer
    }

    // #######################
    // Chat Message Management
    // #######################
    private static String tagToString(MESSAGE_TAG tag) {
        return tag.name()+"";
    }

    public static ChatHudLine.Visible addMessageTag(ChatHudLine.Visible text, MESSAGE_TAG tag) {
        return new ChatHudLine.Visible(text.addedTime(), addMessageTag(text.content(), tag), text.indicator(), text.endOfEntry());
    }

    public static OrderedText addMessageTag(String text, MESSAGE_TAG tag) {
        String resultText = text + MESSAGE_TAG_FORMAT.formatted(tagToString(tag));
        return StringToOrderedText(resultText);
    }

    public static OrderedText addMessageTag(OrderedText text, MESSAGE_TAG tag) {
        String plainText = OrderedTextToString(text);
        return addMessageTag(plainText, tag);
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
        String plainText = textCollector.getText();
        return plainText;
    }

    public static OrderedText StringToOrderedText(String text) {
        return Language.getInstance().reorder(Text.of(text));
    }

    public static boolean isModMessage(String message) {
        Pattern regex = Pattern.compile(DETECT_MOD_MESSAGE_REGEX);
        return regex.matcher(message).matches();
    }

    public static String getUrlFromModMessage(String message) {
        Pattern regex = Pattern.compile(DETECT_MOD_MESSAGE_REGEX);
        Matcher matcher = regex.matcher(message);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }


    // #######################
    // Texture Loading
    // #######################
    public static Identifier loadTextureFromPath(String filePath) throws Exception {
        // Load the image file as a BufferedImage
        File file = new File(filePath);
        return registerTexture(ImageIO.read(file), file.getName());

    }

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
        Identifier textureId = Identifier.of("media-chat", "textures/dynamic/" + filenName.toLowerCase().replace(" ", "_"));

        // Register the texture in the Minecraft texture manager
        MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);

        // Return the Identifier, now registered and usable in-game
        return textureId;
    }
}
