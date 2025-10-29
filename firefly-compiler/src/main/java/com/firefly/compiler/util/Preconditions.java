package com.firefly.compiler.util;

import java.util.Collection;

/**
 * Professional preconditions and validation utilities.
 * Similar to Guava's Preconditions but tailored for compiler needs.
 */
public final class Preconditions {
    
    private Preconditions() {
        // Utility class
    }
    
    /**
     * Check that object is not null
     */
    public static <T> T checkNotNull(T reference, String errorMessage) {
        if (reference == null) {
            throw new NullPointerException(errorMessage);
        }
        return reference;
    }
    
    /**
     * Check that object is not null with formatted message
     */
    public static <T> T checkNotNull(T reference, String errorMessageTemplate, Object... args) {
        if (reference == null) {
            throw new NullPointerException(String.format(errorMessageTemplate, args));
        }
        return reference;
    }
    
    /**
     * Check that condition is true
     */
    public static void checkArgument(boolean expression, String errorMessage) {
        if (!expression) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
    
    /**
     * Check that condition is true with formatted message
     */
    public static void checkArgument(boolean expression, String errorMessageTemplate, Object... args) {
        if (!expression) {
            throw new IllegalArgumentException(String.format(errorMessageTemplate, args));
        }
    }
    
    /**
     * Check that state is valid
     */
    public static void checkState(boolean expression, String errorMessage) {
        if (!expression) {
            throw new IllegalStateException(errorMessage);
        }
    }
    
    /**
     * Check that state is valid with formatted message
     */
    public static void checkState(boolean expression, String errorMessageTemplate, Object... args) {
        if (!expression) {
            throw new IllegalStateException(String.format(errorMessageTemplate, args));
        }
    }
    
    /**
     * Check that string is not null or empty
     */
    public static String checkNotBlank(String value, String errorMessage) {
        checkNotNull(value, errorMessage);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value;
    }
    
    /**
     * Check that collection is not null or empty
     */
    public static <T extends Collection<?>> T checkNotEmpty(T collection, String errorMessage) {
        checkNotNull(collection, errorMessage);
        if (collection.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return collection;
    }
    
    /**
     * Check that index is within bounds
     */
    public static int checkIndex(int index, int size, String errorMessage) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(String.format("%s (index=%d, size=%d)", errorMessage, index, size));
        }
        return index;
    }
    
    /**
     * Check that value is within range
     */
    public static int checkRange(int value, int min, int max, String errorMessage) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                String.format("%s (value=%d, min=%d, max=%d)", errorMessage, value, min, max));
        }
        return value;
    }
    
    /**
     * Require that object is not null
     */
    public static <T> T require(T reference) {
        return checkNotNull(reference, "Object cannot be null");
    }
    
    /**
     * Safe cast with type check
     */
    public static <T> T safeCast(Object value, Class<T> type, String errorMessage) {
        if (value == null) {
            throw new NullPointerException(errorMessage);
        }
        if (!type.isInstance(value)) {
            throw new ClassCastException(String.format("%s: expected %s but got %s", 
                errorMessage, type.getSimpleName(), value.getClass().getSimpleName()));
        }
        return type.cast(value);
    }
}
