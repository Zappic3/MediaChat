package com.zappic3.mediachat;

import net.minecraft.util.Identifier;

public class MediaIdentifierInfo {
    private final Identifier _id;
    private final int _width;
    private final int _height;
    public MediaIdentifierInfo(Identifier id, int width, int height) {
        this._id = id;
        this._width = width;
        this._height = height;
    }

    public Identifier getId() {
        return _id;
    }

    public int getWidth() {
        return _width;
    }

    public int getHeight() {
        return _height;
    }
}
