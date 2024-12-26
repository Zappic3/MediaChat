package com.zappic3.mediachat;

import com.mojang.logging.LogUtils;
import com.zappic3.mediachat.mixin.client.ChatScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.zappic3.mediachat.MediaChat.CONFIG;
import static com.zappic3.mediachat.MediaChat.MOD_ID;

public class Utility {
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

    public static IdentifierAndSize registerTexture(BufferedImage bufferedImage, String fileName) throws IOException {
        // Convert BufferedImage to InputStream for NativeImage
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", baos); // Write the BufferedImage as a PNG into the ByteArrayOutputStream
        InputStream inputStream = new java.io.ByteArrayInputStream(baos.toByteArray());

        // Load NativeImage from InputStream
        NativeImage nativeImage = NativeImage.read(inputStream);

        // Create a NativeImageBackedTexture from the NativeImage
        NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);

        // Generate a valid Identifier for the texture
        Identifier textureId = Identifier.of(MOD_ID, "textures/dynamic/" + fileName.toLowerCase().replace(" ", "_"));

        // Register the texture in the Minecraft texture manager
        MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);

        return new IdentifierAndSize(textureId, calculateTextureMemoryUsage(bufferedImage));
    }

    public record IdentifierAndSize(Identifier identifier, long size) {}
    public record IdentifiersAndSize(List<Identifier> identifiers, long size) {}

    public static long calculateTextureMemoryUsage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int bytesPerPixel = 4; // 4 bytes for RGBA (32-bit)

        return (long) width * height * bytesPerPixel;
    }

    public static String formatBits(long bits) {
        // Convert bits to bytes
        double bytes = bits / 8.0;

        if (bytes < 1024) {
            return String.format("%.2f B", bytes);
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else if (bytes < 1024L * 1024 * 1024 * 1024) {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        } else {
            return String.format("%.2f TB", bytes / (1024.0 * 1024 * 1024 * 1024));
        }
    }


    public static long megabytesToBits(double megabytes) {
        return (long)(megabytes * 1024 * 1024 * 8);
    }

    public static void unregisterTexture(Identifier textureId) {
        TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
        var texture = textureManager.getTexture(textureId);

        if (texture instanceof NativeImageBackedTexture nativeTexture) {
            nativeTexture.close(); // Frees the memory associated with the texture
        }
        textureManager.destroyTexture(textureId); //removes texture from the TextureManager so it can be garbage collected
    }
}
