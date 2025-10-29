package com.firefly.runtime.async;

import java.util.concurrent.CompletableFuture;

/**
 * A Promise is a writable future that can be completed with a value or an error.
 * 
 * <p>Promises are used for creating async operations where you need to control
 * when the result becomes available. Once completed, the Promise cannot be
 * modified again.</p>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create a promise
 * Promise<Integer> promise = new Promise<>();
 * 
 * // Get the read-only future
 * Future<Integer> future = promise.future();
 * 
 * // Complete the promise (in another thread/callback)
 * promise.complete(42);
 * 
 * // Or fail it
 * promise.fail(new RuntimeException("error"));
 * }</pre>
 * 
 * @param <T> The type of the value
 */
public final class Promise<T> {
    
    private final CompletableFuture<T> underlying;
    private volatile boolean completed = false;
    
    /**
     * Creates a new unfulfilled promise.
     */
    public Promise() {
        this.underlying = new CompletableFuture<>();
    }
    
    /**
     * Gets a read-only Future view of this promise.
     * 
     * @return The future
     */
    public Future<T> future() {
        return new Future<>(underlying);
    }
    
    /**
     * Completes this promise with a value.
     * 
     * @param value The value
     * @return true if successful, false if already completed
     */
    public boolean complete(T value) {
        if (completed) {
            return false;
        }
        completed = true;
        return underlying.complete(value);
    }
    
    /**
     * Completes this promise with an error.
     * 
     * @param error The error
     * @return true if successful, false if already completed
     */
    public boolean fail(Throwable error) {
        if (completed) {
            return false;
        }
        completed = true;
        return underlying.completeExceptionally(error);
    }
    
    /**
     * Checks if this promise has been completed.
     * 
     * @return true if completed
     */
    public boolean isCompleted() {
        return completed;
    }
    
    /**
     * Creates an already-completed promise.
     * 
     * @param <T> The type of the value
     * @param value The value
     * @return A completed promise
     */
    public static <T> Promise<T> successful(T value) {
        Promise<T> promise = new Promise<>();
        promise.complete(value);
        return promise;
    }
    
    /**
     * Creates an already-failed promise.
     * 
     * @param <T> The type of the value
     * @param error The error
     * @return A failed promise
     */
    public static <T> Promise<T> failed(Throwable error) {
        Promise<T> promise = new Promise<>();
        promise.fail(error);
        return promise;
    }
}
