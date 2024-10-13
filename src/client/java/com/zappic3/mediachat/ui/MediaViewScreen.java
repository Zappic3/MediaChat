package com.zappic3.mediachat.ui;

import com.zappic3.mediachat.MediaElement;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.TextureComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import static com.zappic3.mediachat.MediaChat.LOGGER;

public class MediaViewScreen extends BaseOwoScreen<FlowLayout> {
    MediaElement _mediaElement;
    TextureComponent _currentImageComp;
    FlowLayout _root;

    double _imagePosX = 0.0;
    double _imagePosY = 0.0;
    double _dragSpeed = 1.0;

    double _imageMin = 0.1;
    double _imageMax = 10.0;
    double _imageScaleSpeed = 0.1;
    double _imageScale = 1.0;

    public MediaViewScreen(MediaElement element) {
        _mediaElement = element;
        scaleImage(0.8);
        //centerImage(); // todo: center the image & scale it so it fits into the screen
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent
                .surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER)
                .padding(Insets.both(0, 0));
        _root = rootComponent;
    }

    @Override
    public void tick() {
        updateCurrentImage();
    }

    private void updateCurrentImage() {
        TextureComponent component = (TextureComponent) Components.texture(
                _mediaElement.currentFrame(),
                0,
                0,
                (int)(_mediaElement.width()*_imageScale),
                (int)(_mediaElement.height()*_imageScale),
                (int)(_mediaElement.width()*_imageScale),
                (int)(_mediaElement.height()*_imageScale)
        ).positioning(Positioning.absolute((int) _imagePosX, (int) _imagePosY));

        if (_root != null) {
            if (_currentImageComp != null) {
                _root.removeChild(_currentImageComp);
            }
            _root.child(component);
            _currentImageComp = component;
        }
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double newScale = Math.clamp(_imageScale+(_imageScaleSpeed*verticalAmount), _imageMin, _imageMax);
        scaleImage(newScale);
        return true;
    }

    private void scaleImage(double newScale) {
        double scaleChange = newScale - _imageScale;

        _imagePosX -= (_mediaElement.width() * scaleChange) / 2;
        _imagePosY -= (_mediaElement.height() * scaleChange) / 2;

        _imageScale = newScale;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        _imagePosX = _imagePosX + (_dragSpeed * deltaX);
        _imagePosY = _imagePosY + (_dragSpeed * deltaY);

        return true;
    }
}
