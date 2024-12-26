package com.zappic3.mediachat;

import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.zappic3.mediachat.MediaChat.CONFIG;

public class MediaMessageUtility {
    private final static String MESSAGE_TAG_FORMAT = "#%s;";
    private final static String MESSAGE_TAG_VALUE_FORMAT = "#%s;%s";
    private final static String MESSAGE_TAG_VALUE_REGEX = "#%s;([^#]+)"; // detect tags with tag value
    private final static String MESSAGE_TAG_REGEX = "#%s;[^#]*";         // detect any tag

    private final static String DETECT_MEDIA_MESSAGE_REGEX = "((?:https?:\\/\\/)?[-a-zA-Z0-9@:%._\\+~#=*]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b[-a-zA-Z0-9()@:%_\\+.~#?&\\/\\/=*]*)";
    private final static String DETECT_MEDIA_FROM_SERVER_REGEX = "(server:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"; // detects "server:<UUID>"

    public enum MESSAGE_TAG {
        BufferGenerated,
        Buffer,
        LowestOfBuffer,
        MessageID
    }

    private static String tagToString(MESSAGE_TAG tag) {
        return CONFIG.debugOptions.useNameInsteadOfID() ? tag.toString() : tag.ordinal()+"";
    }

    public static OrderedText addMessageTag(String text, MESSAGE_TAG tag) {
        String resultText = text + MESSAGE_TAG_FORMAT.formatted(tagToString(tag));
        return StringToOrderedText(resultText);
    }

    public static OrderedText addMessageTag(OrderedText text, MESSAGE_TAG tag) {
        String plainText = OrderedTextToString(text);
        return addMessageTag(plainText, tag);
    }

    public static OrderedText addMessageTagValues(OrderedText text, List<MESSAGE_TAG> tags, List<String> values) {
        if (tags.size() != values.size()) {return null;}
        for (int i = 0; i < tags.size(); i++) {
            String value = values.get(i);
            MESSAGE_TAG tag = tags.get(i);
            text = addMessageTagValue(text, tag, value);
        }
        return text;
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
        return textCollector.getText();
    }

    public static List<RawTextCollector.CharacterWithStyle> OrderedTextToCharacterWithStyle(OrderedText text) {
        RawTextCollector textCollector = new RawTextCollector();
        text.accept(textCollector);
        return textCollector.getCharactersWithStyles();
    }

    public static OrderedText StringToOrderedText(String text) {
        return Language.getInstance().reorder(Text.of(text));
    }

    public static boolean isMediaMessage(String message, Boolean exactMatch) {
        String fullRegex =  Pattern.quote(CONFIG.startMediaUrl()) + DETECT_MEDIA_MESSAGE_REGEX + Pattern.quote(CONFIG.endMediaUrl());
        Pattern regex = Pattern.compile(fullRegex);
        boolean matched = exactMatch ? regex.matcher(message).matches() : regex.matcher(message).find();

        if (!matched) {
            Pattern newRegex = Pattern.compile(Pattern.quote(CONFIG.startMediaUrl()) + DETECT_MEDIA_FROM_SERVER_REGEX + Pattern.quote(CONFIG.endMediaUrl()));
            matched = exactMatch ? newRegex.matcher(message).matches() : newRegex.matcher(message).find();
        }

        return matched;
    }

    public static String getMediaMessageRegex() {
        return Pattern.quote(CONFIG.startMediaUrl()) + DETECT_MEDIA_MESSAGE_REGEX + Pattern.quote(CONFIG.endMediaUrl()) + "|" + Pattern.quote(CONFIG.startMediaUrl()) + DETECT_MEDIA_FROM_SERVER_REGEX + Pattern.quote(CONFIG.endMediaUrl());
    }

    public static OrderedText characterWithStyleToOrderedText(List<RawTextCollector.CharacterWithStyle> chars) {
        List<OrderedText> styledTexts = new ArrayList<>();
        for (RawTextCollector.CharacterWithStyle charWithStyle : chars) {
            OrderedText styledChar = OrderedText.styled(charWithStyle.codePoint(), charWithStyle.style());
            styledTexts.add(styledChar);
        }
        return OrderedText.concat(styledTexts);
    }

    public static String getURLDetectionRegex() {
        return DETECT_MEDIA_MESSAGE_REGEX;
    }

    public static String getUrlFromModMessage(String message) {
        Pattern regex = Pattern.compile(DETECT_MEDIA_MESSAGE_REGEX);
        Matcher matcher = regex.matcher(message);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }
}
