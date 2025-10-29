package com.firefly.compiler.cache;

import java.util.*;
import java.util.concurrent.*;

/**
 * Thread-safe compilation cache for types, symbols, and type resolution results.
 * Uses LRU eviction policy for memory efficiency.
 */
public class CompilationCache<K, V> {
    
    private final int maxSize;
    private final Map<K, V> cache;
    private final Queue<K> accessOrder;
    private final Object lock = new Object();
    
    public CompilationCache(int maxSize) {
        this.maxSize = Math.max(10, maxSize);
        this.cache = new LinkedHashMap<>();
        this.accessOrder = new LinkedList<>();
    }
    
    /**
     * Get value from cache
     */
    public V get(K key) {
        synchronized(lock) {
            V value = cache.get(key);
            if (value != null) {
                // Move to end of access order
                accessOrder.remove(key);
                accessOrder.offer(key);
            }
            return value;
        }
    }
    
    /**
     * Put value in cache
     */
    public void put(K key, V value) {
        synchronized(lock) {
            if (cache.containsKey(key)) {
                accessOrder.remove(key);
            } else if (cache.size() >= maxSize) {
                // Evict least recently used
                K lruKey = accessOrder.poll();
                if (lruKey != null) {
                    cache.remove(lruKey);
                }
            }
            
            cache.put(key, value);
            accessOrder.offer(key);
        }
    }
    
    /**
     * Get or compute value
     */
    public V getOrCompute(K key, java.util.function.Function<K, V> supplier) {
        synchronized(lock) {
            V value = get(key);
            if (value == null) {
                value = supplier.apply(key);
                put(key, value);
            }
            return value;
        }
    }
    
    /**
     * Check if key exists
     */
    public boolean containsKey(K key) {
        synchronized(lock) {
            return cache.containsKey(key);
        }
    }
    
    /**
     * Remove key from cache
     */
    public V remove(K key) {
        synchronized(lock) {
            accessOrder.remove(key);
            return cache.remove(key);
        }
    }
    
    /**
     * Clear entire cache
     */
    public void clear() {
        synchronized(lock) {
            cache.clear();
            accessOrder.clear();
        }
    }
    
    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        synchronized(lock) {
            return new CacheStats(cache.size(), maxSize);
        }
    }
    
    /**
     * Cache statistics
     */
    public static class CacheStats {
        public final int currentSize;
        public final int maxSize;
        public final double utilizationPercentage;
        
        public CacheStats(int currentSize, int maxSize) {
            this.currentSize = currentSize;
            this.maxSize = maxSize;
            this.utilizationPercentage = (double) currentSize / maxSize * 100;
        }
        
        @Override
        public String toString() {
            return String.format("Cache[%d/%d (%.1f%%)]", currentSize, maxSize, utilizationPercentage);
        }
    }
}
