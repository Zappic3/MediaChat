package com.zappic3.mediachat;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utility {
    private final static String MESSAGE_TAG_FORMAT = "#%s;";
    private final static String MESSAGE_TAG_VALUE_FORMAT = "#%s;%s";
    private final static String MESSAGE_TAG_VALUE_REGEX = "#%s;([^#]+)";

    public enum MESSAGE_TAG {
        BufferGenerated,
        Buffer,
        LowestOfBuffer
    }

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
        Pattern pattern = Pattern.compile(MESSAGE_TAG_VALUE_REGEX.formatted(tagToString(tag)));

        String resultText = plainText.replaceAll(pattern.pattern(), "");
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

}
