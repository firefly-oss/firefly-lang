package com.firefly.runtime.examples;

import com.firefly.runtime.collections.PersistentList;

/**
 * Example demonstrating the Firefly PersistentList.
 * 
 * This example shows:
 * - Creating persistent lists
 * - Immutability and structural sharing
 * - Functional operations (map, filter)
 * - Pattern matching style operations
 * - Performance characteristics
 */
public class PersistentListExample {

    public static void main(String[] args) {
        System.out.println("=== Firefly PersistentList Example ===\n");

        // Example 1: Creating lists
        example1_CreatingLists();
        
        // Example 2: Immutability
        example2_Immutability();
        
        // Example 3: Functional operations
        example3_FunctionalOperations();
        
        // Example 4: Pattern matching style
        example4_PatternMatching();
        
        // Example 5: Structural sharing
        example5_StructuralSharing();
        
        // Example 6: Performance
        example6_Performance();

        System.out.println("\n=== Example Complete ===");
    }

    static void example1_CreatingLists() {
        System.out.println("--- Example 1: Creating Lists ---");
        
        // Empty list
        PersistentList<Integer> empty = PersistentList.empty();
        System.out.println("Empty list: " + empty);
        System.out.println("Is empty? " + empty.isEmpty());
        
        // List from varargs
        PersistentList<Integer> numbers = PersistentList.of(1, 2, 3, 4, 5);
        System.out.println("Numbers: " + numbers);
        System.out.println("Size: " + numbers.size());
        
        // List from cons operations
        PersistentList<String> words = PersistentList.<String>empty()
            .cons("world")
            .cons("hello");
        System.out.println("Words: " + words);
        
        System.out.println();
    }

    static void example2_Immutability() {
        System.out.println("--- Example 2: Immutability ---");
        
        PersistentList<Integer> original = PersistentList.of(1, 2, 3);
        System.out.println("Original: " + original);
        
        // Prepending creates a new list
        PersistentList<Integer> withZero = original.cons(0);
        System.out.println("After cons(0): " + withZero);
        System.out.println("Original unchanged: " + original);
        
        // Tail creates a new list
        PersistentList<Integer> tail = original.tail();
        System.out.println("Tail of original: " + tail);
        System.out.println("Original still unchanged: " + original);
        
        System.out.println();
    }

    static void example3_FunctionalOperations() {
        System.out.println("--- Example 3: Functional Operations ---");
        
        PersistentList<Integer> numbers = PersistentList.of(1, 2, 3, 4, 5);
        System.out.println("Original: " + numbers);
        
        // Map: transform each element
        PersistentList<Integer> doubled = numbers.map(x -> x * 2);
        System.out.println("Doubled: " + doubled);
        
        PersistentList<String> asStrings = numbers.map(x -> "num:" + x);
        System.out.println("As strings: " + asStrings);
        
        // Filter: keep only matching elements
        PersistentList<Integer> evens = numbers.filter(x -> x % 2 == 0);
        System.out.println("Evens: " + evens);
        
        PersistentList<Integer> greaterThan2 = numbers.filter(x -> x > 2);
        System.out.println("Greater than 2: " + greaterThan2);
        
        // Chaining operations
        PersistentList<Integer> result = numbers
            .filter(x -> x % 2 == 1)  // Keep odds
            .map(x -> x * x);          // Square them
        System.out.println("Odd numbers squared: " + result);
        
        System.out.println();
    }

    static void example4_PatternMatching() {
        System.out.println("--- Example 4: Pattern Matching Style ---");
        
        PersistentList<Integer> numbers = PersistentList.of(1, 2, 3, 4, 5);
        
        // Sum using pattern matching style
        int sum = sum(numbers);
        System.out.println("Sum of " + numbers + " = " + sum);
        
        // Length using pattern matching
        int length = length(numbers);
        System.out.println("Length of " + numbers + " = " + length);
        
        // Reverse using pattern matching
        PersistentList<Integer> reversed = reverse(numbers);
        System.out.println("Reversed " + numbers + " = " + reversed);
        
        System.out.println();
    }

    // Recursive sum using pattern matching style
    static int sum(PersistentList<Integer> list) {
        if (list.isEmpty()) {
            return 0;
        } else {
            return list.head() + sum(list.tail());
        }
    }

    // Recursive length
    static <T> int length(PersistentList<T> list) {
        if (list.isEmpty()) {
            return 0;
        } else {
            return 1 + length(list.tail());
        }
    }

    // Recursive reverse
    static <T> PersistentList<T> reverse(PersistentList<T> list) {
        return reverseHelper(list, PersistentList.empty());
    }

    static <T> PersistentList<T> reverseHelper(PersistentList<T> list, PersistentList<T> acc) {
        if (list.isEmpty()) {
            return acc;
        } else {
            return reverseHelper(list.tail(), acc.cons(list.head()));
        }
    }

    static void example5_StructuralSharing() {
        System.out.println("--- Example 5: Structural Sharing ---");
        
        // Create a base list
        PersistentList<String> base = PersistentList.of("c", "d", "e");
        System.out.println("Base list: " + base);
        
        // Create multiple lists that share structure
        PersistentList<String> list1 = base.cons("b").cons("a");
        PersistentList<String> list2 = base.cons("x").cons("y");
        PersistentList<String> list3 = base.cons("1").cons("2").cons("3");
        
        System.out.println("List 1: " + list1);
        System.out.println("List 2: " + list2);
        System.out.println("List 3: " + list3);
        
        System.out.println("\nAll three lists share the base structure [c, d, e]");
        System.out.println("This is memory efficient - the shared part is not copied!");
        
        // Verify base is unchanged
        System.out.println("Base still unchanged: " + base);
        
        System.out.println();
    }

    static void example6_Performance() {
        System.out.println("--- Example 6: Performance ---");
        
        // Test cons performance (should be O(1))
        long startTime = System.nanoTime();
        PersistentList<Integer> list = PersistentList.empty();
        for (int i = 0; i < 10000; i++) {
            list = list.cons(i);
        }
        long endTime = System.nanoTime();
        double milliseconds = (endTime - startTime) / 1_000_000.0;
        
        System.out.println("Built list of 10,000 elements in " + 
                          String.format("%.2f", milliseconds) + "ms");
        System.out.println("Final size: " + list.size());
        
        // Test map performance
        startTime = System.nanoTime();
        PersistentList<Integer> mapped = list.map(x -> x * 2);
        endTime = System.nanoTime();
        milliseconds = (endTime - startTime) / 1_000_000.0;
        
        System.out.println("Mapped 10,000 elements in " + 
                          String.format("%.2f", milliseconds) + "ms");
        
        // Test filter performance
        startTime = System.nanoTime();
        PersistentList<Integer> filtered = list.filter(x -> x % 2 == 0);
        endTime = System.nanoTime();
        milliseconds = (endTime - startTime) / 1_000_000.0;
        
        System.out.println("Filtered 10,000 elements in " + 
                          String.format("%.2f", milliseconds) + "ms");
        System.out.println("Filtered size: " + filtered.size());
        
        // Test iteration performance
        startTime = System.nanoTime();
        int sum = 0;
        for (Integer n : list) {
            sum += n;
        }
        endTime = System.nanoTime();
        milliseconds = (endTime - startTime) / 1_000_000.0;
        
        System.out.println("Iterated 10,000 elements in " + 
                          String.format("%.2f", milliseconds) + "ms");
        System.out.println("Sum: " + sum);
        
        System.out.println();
    }
}

