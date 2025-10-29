package com.firefly.runtime.collections;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PersistentHashMap.
 */
class PersistentHashMapTest {

    @Test
    void testEmpty() {
        PersistentHashMap<String, Integer> map = PersistentHashMap.empty();
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
    }

    @Test
    void testOf() {
        PersistentHashMap<String, Integer> map = PersistentHashMap.of(
            PersistentHashMap.entry("one", 1),
            PersistentHashMap.entry("two", 2)
        );
        assertEquals(2, map.size());
        assertEquals(1, map.get("one"));
        assertEquals(2, map.get("two"));
    }

    @Test
    void testPut() {
        PersistentHashMap<String, Integer> map1 = PersistentHashMap.<String, Integer>empty()
            .put("one", 1)
            .put("two", 2);
        
        assertEquals(2, map1.size());
        assertEquals(1, map1.get("one"));
        assertEquals(2, map1.get("two"));
    }

    @Test
    void testPutOverwrite() {
        PersistentHashMap<String, Integer> map1 = PersistentHashMap.<String, Integer>empty()
            .put("one", 1);
        PersistentHashMap<String, Integer> map2 = map1.put("one", 10);
        
        // Original unchanged
        assertEquals(1, map1.get("one"));
        
        // New map has update
        assertEquals(10, map2.get("one"));
    }

    @Test
    void testGet() {
        PersistentHashMap<String, Integer> map = PersistentHashMap.<String, Integer>empty()
            .put("one", 1)
            .put("two", 2);
        
        assertEquals(1, map.get("one"));
        assertEquals(2, map.get("two"));
        assertNull(map.get("three"));
    }

    @Test
    void testGetOrDefault() {
        PersistentHashMap<String, Integer> map = PersistentHashMap.<String, Integer>empty()
            .put("one", 1);
        
        assertEquals(1, map.getOrDefault("one", 0));
        assertEquals(0, map.getOrDefault("two", 0));
    }

    @Test
    void testContainsKey() {
        PersistentHashMap<String, Integer> map = PersistentHashMap.<String, Integer>empty()
            .put("one", 1);
        
        assertTrue(map.containsKey("one"));
        assertFalse(map.containsKey("two"));
    }

    @Test
    void testContainsValue() {
        PersistentHashMap<String, Integer> map = PersistentHashMap.<String, Integer>empty()
            .put("one", 1);
        
        assertTrue(map.containsValue(1));
        assertFalse(map.containsValue(2));
    }

    @Test
    void testRemove() {
        PersistentHashMap<String, Integer> map1 = PersistentHashMap.<String, Integer>empty()
            .put("one", 1)
            .put("two", 2);
        PersistentHashMap<String, Integer> map2 = map1.remove("one");
        
        // Original unchanged
        assertEquals(2, map1.size());
        assertTrue(map1.containsKey("one"));
        
        // New map has removal
        assertEquals(1, map2.size());
        assertFalse(map2.containsKey("one"));
        assertTrue(map2.containsKey("two"));
    }

    @Test
    void testRemoveNonExistent() {
        PersistentHashMap<String, Integer> map1 = PersistentHashMap.<String, Integer>empty()
            .put("one", 1);
        PersistentHashMap<String, Integer> map2 = map1.remove("two");
        
        // Should return same map
        assertSame(map1, map2);
    }

    @Test
    void testPutAll() {
        PersistentHashMap<String, Integer> map1 = PersistentHashMap.<String, Integer>empty()
            .put("one", 1);
        
        Map<String, Integer> other = new HashMap<>();
        other.put("two", 2);
        other.put("three", 3);
        
        PersistentHashMap<String, Integer> map2 = map1.putAll(other);
        
        assertEquals(1, map1.size());
        assertEquals(3, map2.size());
        assertEquals(2, map2.get("two"));
        assertEquals(3, map2.get("three"));
    }

    @Test
    void testMapValues() {
        PersistentHashMap<String, Integer> map1 = PersistentHashMap.<String, Integer>empty()
            .put("one", 1)
            .put("two", 2);
        
        PersistentHashMap<String, String> map2 = map1.mapValues((k, v) -> k + ":" + v);
        
        assertEquals("one:1", map2.get("one"));
        assertEquals("two:2", map2.get("two"));
    }

    @Test
    void testKeySet() {
        PersistentHashMap<String, Integer> map = PersistentHashMap.<String, Integer>empty()
            .put("one", 1)
            .put("two", 2);
        
        assertTrue(map.keySet().contains("one"));
        assertTrue(map.keySet().contains("two"));
        assertEquals(2, map.keySet().size());
    }

    @Test
    void testValues() {
        PersistentHashMap<String, Integer> map = PersistentHashMap.<String, Integer>empty()
            .put("one", 1)
            .put("two", 2);
        
        assertTrue(map.values().contains(1));
        assertTrue(map.values().contains(2));
        assertEquals(2, map.values().size());
    }

    @Test
    void testIteration() {
        PersistentHashMap<String, Integer> map = PersistentHashMap.<String, Integer>empty()
            .put("one", 1)
            .put("two", 2);
        
        int sum = 0;
        for (Map.Entry<String, Integer> entry : map) {
            sum += entry.getValue();
        }
        assertEquals(3, sum);
    }

    @Test
    void testImmutability() {
        PersistentHashMap<String, Integer> map1 = PersistentHashMap.<String, Integer>empty()
            .put("one", 1);
        PersistentHashMap<String, Integer> map2 = map1.put("two", 2);
        PersistentHashMap<String, Integer> map3 = map1.remove("one");
        
        // Original unchanged
        assertEquals(1, map1.size());
        assertTrue(map1.containsKey("one"));
        
        assertEquals(2, map2.size());
        assertEquals(0, map3.size());
    }
}

