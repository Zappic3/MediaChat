package com.zappic3.mediachat;

import net.minecraft.text.CharacterVisitor;
import net.minecraft.text.Style;

import java.util.ArrayList;
import java.util.List;

public class RawTextCollector implements CharacterVisitor {
    private final List<CharacterWithStyle> charactersWithStyles = new ArrayList<>();

    public record CharacterWithStyle(int codePoint, Style style) {
    }

    @Override
    public boolean accept(int index, Style style, int codePoint) {
        charactersWithStyles.add(new CharacterWithStyle(codePoint, style));
        return true;
    }

    public List<CharacterWithStyle> getCharactersWithStyles() {
        return charactersWithStyles;
    }

    public String getText() {
        StringBuilder builder = new StringBuilder();
        for (CharacterWithStyle charWithStyle : charactersWithStyles) {
            builder.append(Character.toChars(charWithStyle.codePoint));
        }
        return builder.toString(); // Return the raw text without styling
    }

    // #####################################################
    // List<CharacterWithStyle> manipulating utility methods
    // #####################################################
    public static String convertToPlainString(List<CharacterWithStyle> styledChars) {
        StringBuilder result = new StringBuilder();
        for (CharacterWithStyle cws : styledChars) {
            result.appendCodePoint(cws.codePoint());
        }
        return result.toString();
    }

    public static List<CharacterWithStyle> removeLeadingWhitespace(List<CharacterWithStyle> styledChars) {
        if (!styledChars.isEmpty() && styledChars.getFirst().codePoint() == 32) {
            styledChars.removeFirst();
        }
        return styledChars;
    }
}