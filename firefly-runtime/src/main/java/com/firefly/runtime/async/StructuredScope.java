package com.firefly.runtime.async;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * StructuredScope provides structured concurrency primitives.
 * 
 * Ensures that all spawned tasks complete before the scope exits,
 * and provides automatic cancellation of all tasks if any task fails.
 * 
 * Example usage:
 * <pre>
 * try (StructuredScope scope = StructuredScope.open()) {
 *     Future&lt;String&gt; f1 = scope.fork(() -&gt; "result1");
 *     Future&lt;Integer&gt; f2 = scope.fork(() -&gt; 42);
 *     scope.join();  // Wait for all tasks to complete
 *     
 *     String r1 = f1.get();
 *     Integer r2 = f2.get();
 * }
 * </pre>
 */
public class StructuredScope implements AutoCloseable {
    
    private final ExecutorService executor;
    private final List<java.util.concurrent.Future<?>> tasks;
    private final List<CompletableFuture<?>> futures;
    private volatile boolean closed;
    private volatile Throwable firstException;
    private final boolean shutdownOnError;
    
    private StructuredScope(ExecutorService executor, boolean shutdownOnError) {
        this.executor = executor;
        this.tasks = new CopyOnWriteArrayList<>();
        this.futures = new CopyOnWriteArrayList<>();
        this.closed = false;
        this.firstException = null;
        this.shutdownOnError = shutdownOnError;
    }
    
    /**
     * Opens a new structured scope with default thread pool.
     * Tasks will be cancelled if any task fails.
     */
    public static StructuredScope open() {
        int processors = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(processors);
        return new StructuredScope(executor, true);
    }
    
    /**
     * Opens a new structured scope with specified parallelism.
     * 
     * @param parallelism Number of threads in the pool
     */
    public static StructuredScope open(int parallelism) {
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        return new StructuredScope(executor, true);
    }
    
    /**
     * Opens a new structured scope with custom executor.
     * 
     * @param executor The executor service to use
     */
    public static StructuredScope open(ExecutorService executor) {
        return new StructuredScope(executor, true);
    }
    
    /**
     * Opens a new structured scope that does not cancel on error.
     * All tasks will complete even if some fail.
     */
    public static StructuredScope openPermissive() {
        int processors = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(processors);
        return new StructuredScope(executor, false);
    }
    
    /**
     * Forks a new task in this scope.
     * 
     * @param task The task to execute
     * @return A Future representing the task result
     */
    public <T> Future<T> fork(Supplier<T> task) {
        if (closed) {
            throw new IllegalStateException("Scope is closed");
        }
        
        CompletableFuture<T> future = new CompletableFuture<>();
        futures.add(future);
        
        java.util.concurrent.Future<T> executorFuture = executor.submit(() -> {
            try {
                if (firstException != null && shutdownOnError) {
                    // Scope is already failed, don't execute
                    throw new CancellationException("Scope cancelled due to previous failure");
                }
                T result = task.get();
                future.complete(result);
                return result;
            } catch (Throwable e) {
                future.completeExceptionally(e);
                
                // Record first exception
                if (firstException == null && shutdownOnError) {
                    synchronized (this) {
                        if (firstException == null) {
                            firstException = e;
                            // Cancel all other tasks
                            cancelAllTasks();
                        }
                    }
                }
                throw e;
            }
        });
        
        tasks.add(executorFuture);
        
        // Return a Future wrapping the CompletableFuture
        return new Future<>(future);
    }
    
    /**
     * Forks a new void task (Runnable) in this scope.
     * 
     * @param task The task to execute
     */
    public void fork(Runnable task) {
        fork(() -> {
            task.run();
            return null;
        });
    }
    
    /**
     * Joins all tasks in this scope, waiting for them to complete.
     * 
     * @throws RuntimeException if any task failed
     */
    public void join() {
        join(-1);
    }
    
    /**
     * Joins all tasks in this scope with a timeout.
     * 
     * @param timeoutMillis Timeout in milliseconds, -1 for no timeout
     * @throws RuntimeException if any task failed or timeout occurred
     */
    public void join(long timeoutMillis) {
        try {
            if (timeoutMillis > 0) {
                long deadline = System.currentTimeMillis() + timeoutMillis;
                for (java.util.concurrent.Future<?> task : tasks) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        throw new TimeoutException("Scope join timeout");
                    }
                    task.get(remaining, TimeUnit.MILLISECONDS);
                }
            } else {
                for (java.util.concurrent.Future<?> task : tasks) {
                    task.get();
                }
            }
            
            // If we got here and there was an exception, throw it
            if (firstException != null) {
                throw new RuntimeException("Scope failed", firstException);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            cancelAllTasks();
            throw new RuntimeException("Scope join failed", e);
        }
    }
    
    /**
     * Cancels all tasks in this scope.
     */
    private void cancelAllTasks() {
        for (java.util.concurrent.Future<?> task : tasks) {
            task.cancel(true);
        }
    }
    
    /**
     * Returns true if any task in this scope has failed.
     */
    public boolean hasFailed() {
        return firstException != null;
    }
    
    /**
     * Returns the first exception that occurred, or null if no failures.
     */
    public Throwable getFirstException() {
        return firstException;
    }
    
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            
            // Wait for all tasks to complete
            try {
                join();
            } catch (Exception e) {
                // Ignore - already handled
            }
            
            // Shutdown executor
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Static helper to run tasks in a structured scope.
     * Automatically joins and closes the scope.
     * 
     * Example:
     * <pre>
     * StructuredScope.scoped(scope -&gt; {
     *     scope.fork(() -&gt; task1());
     *     scope.fork(() -&gt; task2());
     * });
     * </pre>
     */
    public static void scoped(ScopeConsumer consumer) {
        try (StructuredScope scope = StructuredScope.open()) {
            consumer.accept(scope);
            scope.join();
        } catch (Exception e) {
            throw new RuntimeException("Structured scope failed", e);
        }
    }
    
    /**
     * Static helper to run tasks in a structured scope and return a result.
     */
    public static <R> R scoped(ScopeFunction<R> function) {
        try (StructuredScope scope = StructuredScope.open()) {
            R result = function.apply(scope);
            scope.join();
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Structured scope failed", e);
        }
    }
    
    @FunctionalInterface
    public interface ScopeConsumer {
        void accept(StructuredScope scope) throws Exception;
    }
    
    @FunctionalInterface
    public interface ScopeFunction<R> {
        R apply(StructuredScope scope) throws Exception;
    }
}
