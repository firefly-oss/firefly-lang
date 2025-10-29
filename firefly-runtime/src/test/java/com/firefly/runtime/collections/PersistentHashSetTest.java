package com.firefly.runtime.collections;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PersistentHashSet.
 */
class PersistentHashSetTest {

    @Test
    void testEmpty() {
        PersistentHashSet<String> set = PersistentHashSet.empty();
        assertTrue(set.isEmpty());
        assertEquals(0, set.size());
    }

    @Test
    void testOf() {
        PersistentHashSet<Integer> set = PersistentHashSet.of(1, 2, 3);
        assertEquals(3, set.size());
        assertTrue(set.contains(1));
        assertTrue(set.contains(2));
        assertTrue(set.contains(3));
    }

    @Test
    void testAdd() {
        PersistentHashSet<String> set1 = PersistentHashSet.<String>empty()
            .add("a")
            .add("b");
        
        assertEquals(2, set1.size());
        assertTrue(set1.contains("a"));
        assertTrue(set1.contains("b"));
    }

    @Test
    void testAddDuplicate() {
        PersistentHashSet<String> set1 = PersistentHashSet.<String>empty()
            .add("a");
        PersistentHashSet<String> set2 = set1.add("a");
        
        // Should return same set
        assertSame(set1, set2);
    }

    @Test
    void testContains() {
        PersistentHashSet<Integer> set = PersistentHashSet.of(1, 2, 3);
        
        assertTrue(set.contains(1));
        assertTrue(set.contains(2));
        assertTrue(set.contains(3));
        assertFalse(set.contains(4));
    }

    @Test
    void testRemove() {
        PersistentHashSet<String> set1 = PersistentHashSet.of("a", "b", "c");
        PersistentHashSet<String> set2 = set1.remove("b");
        
        // Original unchanged
        assertEquals(3, set1.size());
        assertTrue(set1.contains("b"));
        
        // New set has removal
        assertEquals(2, set2.size());
        assertFalse(set2.contains("b"));
        assertTrue(set2.contains("a"));
        assertTrue(set2.contains("c"));
    }

    @Test
    void testRemoveNonExistent() {
        PersistentHashSet<String> set1 = PersistentHashSet.of("a");
        PersistentHashSet<String> set2 = set1.remove("b");
        
        // Should return same set
        assertSame(set1, set2);
    }

    @Test
    void testAddAll() {
        PersistentHashSet<Integer> set1 = PersistentHashSet.of(1, 2);
        PersistentHashSet<Integer> set2 = set1.addAll(Arrays.asList(3, 4, 5));
        
        assertEquals(2, set1.size());
        assertEquals(5, set2.size());
        assertTrue(set2.contains(3));
        assertTrue(set2.contains(4));
        assertTrue(set2.contains(5));
    }

    @Test
    void testFilter() {
        PersistentHashSet<Integer> set1 = PersistentHashSet.of(1, 2, 3, 4, 5);
        PersistentHashSet<Integer> set2 = set1.filter(x -> x % 2 == 0);
        
        assertEquals(2, set2.size());
        assertTrue(set2.contains(2));
        assertTrue(set2.contains(4));
    }

    @Test
    void testMap() {
        PersistentHashSet<Integer> set1 = PersistentHashSet.of(1, 2, 3);
        PersistentHashSet<String> set2 = set1.map(x -> "num:" + x);
        
        assertEquals(3, set2.size());
        assertTrue(set2.contains("num:1"));
        assertTrue(set2.contains("num:2"));
        assertTrue(set2.contains("num:3"));
    }

    @Test
    void testUnion() {
        PersistentHashSet<String> set1 = PersistentHashSet.of("a", "b");
        PersistentHashSet<String> set2 = PersistentHashSet.of("b", "c");
        PersistentHashSet<String> union = set1.union(set2);
        
        assertEquals(3, union.size());
        assertTrue(union.contains("a"));
        assertTrue(union.contains("b"));
        assertTrue(union.contains("c"));
    }

    @Test
    void testIntersection() {
        PersistentHashSet<String> set1 = PersistentHashSet.of("a", "b", "c");
        PersistentHashSet<String> set2 = PersistentHashSet.of("b", "c", "d");
        PersistentHashSet<String> intersection = set1.intersection(set2);
        
        assertEquals(2, intersection.size());
        assertTrue(intersection.contains("b"));
        assertTrue(intersection.contains("c"));
    }

    @Test
    void testDifference() {
        PersistentHashSet<String> set1 = PersistentHashSet.of("a", "b", "c");
        PersistentHashSet<String> set2 = PersistentHashSet.of("b", "c", "d");
        PersistentHashSet<String> difference = set1.difference(set2);
        
        assertEquals(1, difference.size());
        assertTrue(difference.contains("a"));
    }

    @Test
    void testIteration() {
        PersistentHashSet<Integer> set = PersistentHashSet.of(1, 2, 3);
        int sum = 0;
        for (Integer n : set) {
            sum += n;
        }
        assertEquals(6, sum);
    }

    @Test
    void testImmutability() {
        PersistentHashSet<String> set1 = PersistentHashSet.of("a", "b");
        PersistentHashSet<String> set2 = set1.add("c");
        PersistentHashSet<String> set3 = set1.remove("a");
        
        // Original unchanged
        assertEquals(2, set1.size());
        assertTrue(set1.contains("a"));
        assertTrue(set1.contains("b"));
        
        assertEquals(3, set2.size());
        assertEquals(1, set3.size());
    }
}

