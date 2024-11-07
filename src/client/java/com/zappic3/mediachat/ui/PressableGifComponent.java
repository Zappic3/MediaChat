package com.zappic3.mediachat.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zappic3.mediachat.MediaElement;
import io.wispforest.owo.mixin.ui.access.ButtonWidgetAccessor;
import io.wispforest.owo.mixin.ui.access.ClickableWidgetAccessor;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.util.NinePatchTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.HoveredTooltipPositioner;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static com.zappic3.mediachat.MediaChat.LOGGER;
import static com.zappic3.mediachat.MediaChat.MOD_ID;

import java.util.function.Consumer;

public class PressableGifComponent extends ButtonWidget {
    private static final Identifier GIF_CATEGORY_OVERLAY = Identifier.of(MOD_ID, "textures/gif_browser_category_overlay.png");

    protected boolean textShadow = true;
    private boolean autoHeight = true;
    private final String _sourceURL;

    public PressableGifComponent(String sourceURL, Consumer<PressableGifComponent> onPress) {
        super(0, 0, 0, 0, Text.empty(), button -> onPress.accept((PressableGifComponent) button), ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
        _sourceURL = sourceURL;
        this.sizing(Sizing.content());
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
            if (!this.getMessage().equals(Text.empty())) {
                //context.drawTexture(GIF_CATEGORY_OVERLAY, this.getX(), this.getY(), 0, renderV, this.width, this.height, this.width, this.height);
                // todo add overlay
            }
        } catch (Exception e) {
            LOGGER.error("An error occurred while trying to render a gif widget", e);
        }

        /// ###################################

        var textRenderer = MinecraftClient.getInstance().textRenderer;
        int color = this.active ? 0xffffff : 0xa0a0a0;

        if (this.textShadow) {
            context.drawCenteredTextWithShadow(textRenderer, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, color);
        } else {
            context.drawText(textRenderer, this.getMessage(), (int) (this.getX() + this.width / 2f - textRenderer.getWidth(this.getMessage()) / 2f), (int) (this.getY() + (this.height - 8) / 2f), color, false);
        }

        var tooltip = ((ClickableWidgetAccessor) this).owo$getTooltip();
        if (this.hovered && tooltip.getTooltip() != null)
            context.drawTooltip(textRenderer, tooltip.getTooltip().getLines(MinecraftClient.getInstance()), HoveredTooltipPositioner.INSTANCE, mouseX, mouseY);
    }

    private void calcAutoHeight(MediaElement gif, int elementWidth) {
        if (this.autoHeight && !gif.isLoading()) {
            this.verticalSizing(Sizing.fixed((gif.height() * elementWidth) / gif.width()));
        }
    }


    public PressableGifComponent onPress(Consumer<ButtonComponent> onPress) {
        ((ButtonWidgetAccessor) this).owo$setOnPress(button -> onPress.accept((ButtonComponent) button));
        return this;
    }

    public PressableGifComponent active(boolean active) {
        this.active = active;
        return this;
    }

    public boolean active() {
        return this.active;
    }

    public boolean autoHeight() {
        return this.autoHeight;
    }

    public PressableGifComponent setAutoHeight(boolean autoHeight) {
        this.autoHeight = autoHeight;
        return this;
    }
}

