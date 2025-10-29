package com.firefly.runtime.collections;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PersistentVector.
 */
class PersistentVectorTest {

    @Test
    void testEmpty() {
        PersistentVector<String> vec = PersistentVector.empty();
        assertTrue(vec.isEmpty());
        assertEquals(0, vec.size());
    }

    @Test
    void testOf() {
        PersistentVector<Integer> vec = PersistentVector.of(1, 2, 3);
        assertFalse(vec.isEmpty());
        assertEquals(3, vec.size());
        assertEquals(1, vec.get(0));
        assertEquals(2, vec.get(1));
        assertEquals(3, vec.get(2));
    }

    @Test
    void testGet() {
        PersistentVector<String> vec = PersistentVector.of("a", "b", "c");
        assertEquals("a", vec.get(0));
        assertEquals("b", vec.get(1));
        assertEquals("c", vec.get(2));
    }

    @Test
    void testGetOutOfBounds() {
        PersistentVector<String> vec = PersistentVector.of("a", "b");
        assertThrows(IndexOutOfBoundsException.class, () -> vec.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> vec.get(2));
    }

    @Test
    void testSet() {
        PersistentVector<String> vec1 = PersistentVector.of("a", "b", "c");
        PersistentVector<String> vec2 = vec1.set(1, "B");
        
        // Original unchanged
        assertEquals("b", vec1.get(1));
        
        // New vector has update
        assertEquals("B", vec2.get(1));
        assertEquals("a", vec2.get(0));
        assertEquals("c", vec2.get(2));
    }

    @Test
    void testAppend() {
        PersistentVector<Integer> vec1 = PersistentVector.of(1, 2, 3);
        PersistentVector<Integer> vec2 = vec1.append(4);
        
        assertEquals(3, vec1.size());
        assertEquals(4, vec2.size());
        assertEquals(4, vec2.get(3));
    }

    @Test
    void testPrepend() {
        PersistentVector<Integer> vec1 = PersistentVector.of(2, 3, 4);
        PersistentVector<Integer> vec2 = vec1.prepend(1);
        
        assertEquals(3, vec1.size());
        assertEquals(4, vec2.size());
        assertEquals(1, vec2.get(0));
        assertEquals(2, vec2.get(1));
    }

    @Test
    void testMap() {
        PersistentVector<Integer> vec1 = PersistentVector.of(1, 2, 3);
        PersistentVector<Integer> vec2 = vec1.map(x -> x * 2);
        
        assertEquals(PersistentVector.of(2, 4, 6), vec2);
    }

    @Test
    void testFilter() {
        PersistentVector<Integer> vec1 = PersistentVector.of(1, 2, 3, 4, 5);
        PersistentVector<Integer> vec2 = vec1.filter(x -> x % 2 == 0);
        
        assertEquals(PersistentVector.of(2, 4), vec2);
    }

    @Test
    void testIteration() {
        PersistentVector<String> vec = PersistentVector.of("a", "b", "c");
        StringBuilder sb = new StringBuilder();
        for (String s : vec) {
            sb.append(s);
        }
        assertEquals("abc", sb.toString());
    }

    @Test
    void testEquals() {
        PersistentVector<Integer> vec1 = PersistentVector.of(1, 2, 3);
        PersistentVector<Integer> vec2 = PersistentVector.of(1, 2, 3);
        PersistentVector<Integer> vec3 = PersistentVector.of(1, 2, 4);
        
        assertEquals(vec1, vec2);
        assertNotEquals(vec1, vec3);
    }

    @Test
    void testHashCode() {
        PersistentVector<Integer> vec1 = PersistentVector.of(1, 2, 3);
        PersistentVector<Integer> vec2 = PersistentVector.of(1, 2, 3);
        
        assertEquals(vec1.hashCode(), vec2.hashCode());
    }

    @Test
    void testToString() {
        PersistentVector<Integer> vec = PersistentVector.of(1, 2, 3);
        assertEquals("[1, 2, 3]", vec.toString());
    }

    @Test
    void testImmutability() {
        PersistentVector<Integer> vec1 = PersistentVector.of(1, 2, 3);
        PersistentVector<Integer> vec2 = vec1.set(0, 10);
        PersistentVector<Integer> vec3 = vec1.append(4);
        
        // Original unchanged
        assertEquals(PersistentVector.of(1, 2, 3), vec1);
        assertEquals(PersistentVector.of(10, 2, 3), vec2);
        assertEquals(PersistentVector.of(1, 2, 3, 4), vec3);
    }
}

