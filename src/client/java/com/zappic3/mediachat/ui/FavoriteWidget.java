package com.zappic3.mediachat.ui;

import com.zappic3.mediachat.FavoritesManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Objects;
import java.util.Vector;

import static com.zappic3.mediachat.MediaChat.LOGGER;
import static com.zappic3.mediachat.MediaChat.MOD_ID;

public final class FavoriteWidget extends ButtonWidget {
    private static final Identifier FAVORITE_ICON_TEXTURE = Identifier.of(MOD_ID, "textures/favorite_icon.png");
    public static final int defaultSize = 96;
    private static float scale = 1.0f;

    private static FavoriteWidget INSTANCE = null;
    private static String currentElementUrl = null;
    private static boolean currentElementIsFavorite = false;

    private FavoriteWidget(String elementUrl, int x, int y, float scale, NarrationSupplier narrationSupplier) {
        super(x, y, defaultSize, defaultSize, Text.empty(), (t) -> {LOGGER.info("Favorited!!");}, narrationSupplier);

        FavoriteWidget.scale = scale;
        currentElementUrl = elementUrl;
        currentElementIsFavorite = FavoritesManager.getInstance().isFavorite(elementUrl.hashCode());

        INSTANCE = this;
    }

    public static FavoriteWidget configure(String elementUrl, int x, int y, float scale) {
        if (INSTANCE == null) {
            return new FavoriteWidget(elementUrl, x, y, scale, null);
        } else {
            return INSTANCE.update(elementUrl, x, y, scale);
        }
    }

    private FavoriteWidget update(String elementUrl, int x, int y, float scale) {
        this.setX(x);
        this.setY(y);
        FavoriteWidget.scale = scale;

        if (!Objects.equals(elementUrl, currentElementUrl)) {
            currentElementUrl = elementUrl;
           currentElementIsFavorite = FavoritesManager.getInstance().isFavorite(elementUrl.hashCode());
        }
        return this;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        Matrix4f mat = context.getMatrices().peek().getPositionMatrix();
        Vector4f vec = new Vector4f((float) mouseX, (float) mouseY, 0, 1);
        vec.mul(mat);

        renderWidget(context, this.isMouseOver(vec.x, vec.y));
    }


    public void renderWidget(DrawContext context, boolean mouseOver) {
        this.hovered = mouseOver;

        MatrixStack matrices = context.getMatrixStack();
        matrices.push();

        matrices.translate(this.getX(), this.getY(), 0);
        matrices.scale(scale, scale, 1.0f);

        int tex_shift = currentElementIsFavorite ? defaultSize*2 : (mouseOver ? defaultSize : 0);
        context.drawTexture(FAVORITE_ICON_TEXTURE, 0, 0, tex_shift, 0, defaultSize, defaultSize, 288, 96);

        matrices.pop();
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        //todo this doesnt really work with scaled / translated matrices,
        // but I spent way to much time on this and im giving up
        
        // Convert mouse coordinates to match the transformed widget space
        double translatedMouseX = (mouseX - this.getX()); // Account for translation
        double translatedMouseY = (mouseY - this.getY()); // Account for translation

        // Scale the mouse position to match the scaled widget space
        double inverseMouseX = translatedMouseX / scale;
        double inverseMouseY = translatedMouseY / scale;

        return inverseMouseX >= 0 && inverseMouseX < defaultSize
                && inverseMouseY >= 0 && inverseMouseY < defaultSize;
    }

    public static boolean hovered() {
        return INSTANCE != null && INSTANCE.isHovered();
    }

    public static void resolveClick() {
        INSTANCE.playDownSound(MinecraftClient.getInstance().getSoundManager());

        FavoritesManager.getInstance().toggleFavorite(currentElementUrl);
        currentElementIsFavorite = !currentElementIsFavorite;
    }

    public static void resetHover() {
        INSTANCE.hovered = false;
    }
}
