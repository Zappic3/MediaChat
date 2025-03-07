package com.zappic3.mediachat.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zappic3.mediachat.MediaElement;
import io.wispforest.owo.mixin.ui.access.ClickableWidgetAccessor;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.HoveredTooltipPositioner;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import static com.zappic3.mediachat.MediaChat.LOGGER;

import java.util.function.Consumer;

public class PressableGifComponent extends ButtonWidget {
    protected boolean textShadow = true;
    private boolean autoHeight = true;
    private final String _sourceURL;
    private final String _favoriteUrl;

    public PressableGifComponent(String sourceUrl, Consumer<PressableGifComponent> onPress, String favoriteUrl) {
        super(0, 0, 0, 0, Text.empty(), button -> {
            if(!FavoriteWidget.hovered()) {
                onPress.accept((PressableGifComponent) button);
            } else {
                FavoriteWidget.resolveClick();
            }
            }, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
        _sourceURL = sourceUrl;
        _favoriteUrl = (favoriteUrl == null || favoriteUrl.isEmpty()) ? sourceUrl : favoriteUrl;
    }

    public PressableGifComponent(String sourceURL, Consumer<PressableGifComponent> onPress) {
        this(sourceURL, onPress, sourceURL);
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        MediaElement gif = MediaElement.of(_sourceURL, false, MediaElement.Importance.LOW);
        try {
            calcAutoHeight(gif, this.width);
            int renderV = 0;
            if (!this.active) {
                renderV += this.height * 2;
            } else if (this.isHovered()) {
                renderV += this.height;
            }
            RenderSystem.enableDepthTest();
            context.drawTexture(gif.currentFrame(), this.getX(), this.getY(), 0, renderV, this.width, this.height, this.width, this.height);

            // if this element has an assigned text or is hovered, darken the background for better readability / highlighting current selection
            if (!this.getMessage().equals(Text.empty()) || hovered) {
                context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0x80000000);

                if (this.getMessage().equals(Text.empty())) {
                    float widgetScale = 0.15f;
                    FavoriteWidget.configure(_favoriteUrl, (int)((this.getX()+this.width())-FavoriteWidget.defaultSize*widgetScale), this.getY(),widgetScale)
                            .renderWidget(context, mouseX, mouseY, delta);
                }
            }
        } catch (Exception e) {
            LOGGER.error("An error occurred while trying to render a gif widget", e);
        }

        /// ###################################

        var textRenderer = MinecraftClient.getInstance().textRenderer;
        int color = this.active ? 0xffffff : 0xa0a0a0;

        MatrixStack matrices = context.getMatrixStack();
        matrices.push();

        float scale = 0.75f;
        matrices.scale(scale, scale, 1.0f);

        float x = (this.getX() + this.width / 2f - textRenderer.getWidth(this.getMessage()) / 2f * scale) / scale;
        float y = (this.getY() + (this.height - 8) / 2f) / scale;

        //context.drawCenteredTextWithShadow(textRenderer, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, color);
        context.drawText(textRenderer, this.getMessage(), (int) x, (int) y, color, this.textShadow);
        matrices.pop();

        var tooltip = ((ClickableWidgetAccessor) this).owo$getTooltip();
        if (this.hovered && tooltip.getTooltip() != null)
            context.drawTooltip(textRenderer, tooltip.getTooltip().getLines(MinecraftClient.getInstance()), HoveredTooltipPositioner.INSTANCE, mouseX, mouseY);
    }

    private void calcAutoHeight(MediaElement gif, int elementWidth) {
        if (this.autoHeight && !gif.isLoading()) {
            this.verticalSizing(Sizing.fixed((gif.height() * elementWidth) / gif.width()));
        }
    }

    @SuppressWarnings("RedundantMethodOverride")
    @Override
    public boolean canFocus(FocusSource source) {
        return false;
    }


    public PressableGifComponent setAutoHeight(boolean autoHeight) {
        this.autoHeight = autoHeight;
        return this;
    }
}

