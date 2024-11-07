package com.zappic3.mediachat;

/**
 * A helper value object used to return one of two possible types
 * @param <T>
 * @param <K>
 */
public class OneOfTwo<T, K> {
    private final T _valueOne;
    private final K _valueTwo;

    private OneOfTwo(T valueOne, K valueTwo) {
        _valueOne = valueOne;
        _valueTwo = valueTwo;
    }

    public static <T, K> OneOfTwo<T, K> ofFirst(T valueOne) {
        return new OneOfTwo<>(valueOne, null);
    }

    public static <T, K> OneOfTwo<T, K> ofSecond(K valueTwo) {
        return new OneOfTwo<>(null, valueTwo);
    }

    public T getFirst() {
        return _valueOne;
    }

    public K getSecond() {
        return _valueTwo;
    }
}