package com.firefly.repl;

import java.lang.reflect.Array;
import java.util.*;

/**
 * IPython-style pretty printer for REPL output.
 */
public class PrettyPrinter {
    
    private static final int MAX_COLLECTION_ITEMS = 100;
    private static final int MAX_STRING_LENGTH = 1000;
    private static final int MAX_DEPTH = 10;
    
    /**
     * Pretty print an object with colors and truncation.
     */
    public static String prettyPrint(Object obj) {
        return prettyPrint(obj, 0);
    }
    
    private static String prettyPrint(Object obj, int depth) {
        if (obj == null) {
            return ReplUI.Colors.DIM + "null" + ReplUI.Colors.RESET;
        }
        
        if (depth > MAX_DEPTH) {
            return ReplUI.Colors.DIM + "..." + ReplUI.Colors.RESET;
        }
        
        Class<?> clazz = obj.getClass();
        
        // Primitives and wrappers
        if (clazz.isPrimitive() || obj instanceof Number) {
            return ReplUI.Colors.CYAN + obj.toString() + ReplUI.Colors.RESET;
        }
        
        // Booleans
        if (obj instanceof Boolean) {
            return ReplUI.Colors.MAGENTA + obj.toString() + ReplUI.Colors.RESET;
        }
        
        // Strings
        if (obj instanceof String) {
            String str = (String) obj;
            if (str.length() > MAX_STRING_LENGTH) {
                str = str.substring(0, MAX_STRING_LENGTH) + "...";
            }
            return ReplUI.Colors.YELLOW + "\"" + escapeString(str) + "\"" + ReplUI.Colors.RESET;
        }
        
        // Characters
        if (obj instanceof Character) {
            return ReplUI.Colors.YELLOW + "'" + obj + "'" + ReplUI.Colors.RESET;
        }
        
        // Arrays
        if (clazz.isArray()) {
            return prettyPrintArray(obj, depth);
        }
        
        // Collections
        if (obj instanceof Collection) {
            return prettyPrintCollection((Collection<?>) obj, depth);
        }
        
        // Maps
        if (obj instanceof Map) {
            return prettyPrintMap((Map<?, ?>) obj, depth);
        }
        
        // Default: toString with type info
        String typeName = clazz.getSimpleName();
        String value = obj.toString();
        if (value.length() > MAX_STRING_LENGTH) {
            value = value.substring(0, MAX_STRING_LENGTH) + "...";
        }
        return ReplUI.Colors.BRIGHT_CYAN + "<" + typeName + "> " + ReplUI.Colors.RESET + value;
    }
    
    private static String prettyPrintArray(Object arr, int depth) {
        int len = Array.getLength(arr);
        StringBuilder sb = new StringBuilder();
        sb.append(ReplUI.Colors.BRIGHT_WHITE + "[" + ReplUI.Colors.RESET);
        
        int limit = Math.min(len, MAX_COLLECTION_ITEMS);
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(ReplUI.Colors.DIM + ", " + ReplUI.Colors.RESET);
            sb.append(prettyPrint(Array.get(arr, i), depth + 1));
        }
        
        if (len > MAX_COLLECTION_ITEMS) {
            sb.append(ReplUI.Colors.DIM + ", ... (" + (len - MAX_COLLECTION_ITEMS) + " more)" + ReplUI.Colors.RESET);
        }
        
        sb.append(ReplUI.Colors.BRIGHT_WHITE + "]" + ReplUI.Colors.RESET);
        return sb.toString();
    }
    
    private static String prettyPrintCollection(Collection<?> coll, int depth) {
        StringBuilder sb = new StringBuilder();
        String typeName = coll.getClass().getSimpleName();
        sb.append(ReplUI.Colors.BRIGHT_CYAN + typeName + ReplUI.Colors.RESET);
        sb.append(ReplUI.Colors.BRIGHT_WHITE + "(" + ReplUI.Colors.RESET);
        
        int count = 0;
        for (Object item : coll) {
            if (count > 0) sb.append(ReplUI.Colors.DIM + ", " + ReplUI.Colors.RESET);
            if (count >= MAX_COLLECTION_ITEMS) {
                sb.append(ReplUI.Colors.DIM + "... (" + (coll.size() - MAX_COLLECTION_ITEMS) + " more)" + ReplUI.Colors.RESET);
                break;
            }
            sb.append(prettyPrint(item, depth + 1));
            count++;
        }
        
        sb.append(ReplUI.Colors.BRIGHT_WHITE + ")" + ReplUI.Colors.RESET);
        return sb.toString();
    }
    
    private static String prettyPrintMap(Map<?, ?> map, int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(ReplUI.Colors.BRIGHT_CYAN + map.getClass().getSimpleName() + ReplUI.Colors.RESET);
        sb.append(ReplUI.Colors.BRIGHT_WHITE + "{" + ReplUI.Colors.RESET);
        
        int count = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (count > 0) sb.append(ReplUI.Colors.DIM + ", " + ReplUI.Colors.RESET);
            if (count >= MAX_COLLECTION_ITEMS) {
                sb.append(ReplUI.Colors.DIM + "... (" + (map.size() - MAX_COLLECTION_ITEMS) + " more)" + ReplUI.Colors.RESET);
                break;
            }
            sb.append(prettyPrint(entry.getKey(), depth + 1));
            sb.append(ReplUI.Colors.DIM + ": " + ReplUI.Colors.RESET);
            sb.append(prettyPrint(entry.getValue(), depth + 1));
            count++;
        }
        
        sb.append(ReplUI.Colors.BRIGHT_WHITE + "}" + ReplUI.Colors.RESET);
        return sb.toString();
    }
    
    private static String escapeString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t")
                  .replace("\"", "\\\"");
    }
}
