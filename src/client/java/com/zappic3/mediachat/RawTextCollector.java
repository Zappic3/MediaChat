package com.zappic3.mediachat;

import net.minecraft.text.CharacterVisitor;
import net.minecraft.text.Style;

public class RawTextCollector implements CharacterVisitor {
    private final StringBuilder builder = new StringBuilder();

    public boolean accept(int index, Style style, int codePoint) {
        // Append the character represented by the codePoint to the StringBuilder
        builder.append(Character.toChars(codePoint));
        // Return true to continue visiting characters
        return true;
    }

    public String getText() {
        return builder.toString();
    }

}