package com.firefly.runtime.async;

import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A Future represents a value that may not be available yet.
 * 
 * <p>Futures are used for asynchronous programming, allowing you to work with
 * values that will be computed in the background. This is similar to JavaScript
 * Promises, Rust Futures, or Java CompletableFuture.</p>
 * 
 * <h2>Key Characteristics</h2>
 * <ul>
 *   <li><b>Asynchronous:</b> Computation happens in the background</li>
 *   <li><b>Composable:</b> Chain operations with map, flatMap, etc.</li>
 *   <li><b>Thread-Safe:</b> Safe to use from multiple threads</li>
 *   <li><b>Non-Blocking:</b> Callbacks execute when value is ready</li>
 *   <li><b>Error Handling:</b> Propagates errors through the chain</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create a future from a computation
 * Future<Integer> future = Future.async(() -> {
 *     Thread.sleep(1000);
 *     return 42;
 * });
 * 
 * // Transform the result
 * Future<String> result = future
 *     .map(x -> x * 2)
 *     .map(x -> "Result: " + x);
 * 
 * // Handle completion
 * result.onComplete(value -> System.out.println(value));
 * 
 * // Or block and wait
 * String value = result.get();  // "Result: 84"
 * }</pre>
 * 
 * @param <T> The type of the value
 */
public final class Future<T> {
    
    private static final ExecutorService DEFAULT_EXECUTOR = 
        Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            new ThreadFactory() {
                private final java.util.concurrent.atomic.AtomicInteger idx = new java.util.concurrent.atomic.AtomicInteger(1);
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "FireflyWorker-" + idx.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            }
        );
    
    // Dedicated scheduler for timeouts to avoid starvation from compute pool
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "FireflyTimeoutScheduler");
        t.setDaemon(true);
        return t;
    });
    
    private final CompletableFuture<T> underlying;
    
    /**
     * Package-private constructor (used by Promise).
     */
    Future(CompletableFuture<T> underlying) {
        this.underlying = underlying;
    }
    
    /**
     * Creates a future that is already completed with the given value.
     * 
     * @param <T> The type of the value
     * @param value The value
     * @return A completed future
     */
    public static <T> Future<T> successful(T value) {
        return new Future<>(CompletableFuture.completedFuture(value));
    }
    
    /**
     * Creates a future that is already failed with the given exception.
     * 
     * @param <T> The type of the value
     * @param error The exception
     * @return A failed future
     */
    public static <T> Future<T> failed(Throwable error) {
        CompletableFuture<T> cf = new CompletableFuture<>();
        cf.completeExceptionally(error);
        return new Future<>(cf);
    }
    
    /**
     * Creates a future from an asynchronous computation.
     * 
     * <p>The computation will be executed in a background thread from the
     * default executor.</p>
     * 
     * @param <T> The type of the value
     * @param computation The computation to execute
     * @return A future that will complete with the result
     */
    public static <T> Future<T> async(Callable<T> computation) {
        return new Future<>(CompletableFuture.supplyAsync(() -> {
            try {
                return computation.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, DEFAULT_EXECUTOR));
    }
    
    /**
     * Creates a future from an asynchronous computation using a custom executor.
     * 
     * @param <T> The type of the value
     * @param computation The computation to execute
     * @param executor The executor to use
     * @return A future that will complete with the result
     */
    public static <T> Future<T> async(Callable<T> computation, Executor executor) {
        return new Future<>(CompletableFuture.supplyAsync(() -> {
            try {
                return computation.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor));
    }
    
    /**
     * Creates a future from a Runnable using the default executor.
     * The resulting future completes with null (Void).
     */
    public static Future<Void> async(Runnable task) {
        return new Future<>(CompletableFuture.runAsync(task, DEFAULT_EXECUTOR).thenApply(v -> null));
    }
    
    /**
     * Creates a future from a Runnable using a custom executor.
     * The resulting future completes with null (Void).
     */
    public static Future<Void> async(Runnable task, Executor executor) {
        return new Future<>(CompletableFuture.runAsync(task, executor).thenApply(v -> null));
    }
    
    /**
     * Checks if this future has completed (successfully or with an error).
     * 
     * @return true if completed
     */
    public boolean isCompleted() {
        return underlying.isDone();
    }
    
    /**
     * Alias for isCompleted() - checks if this future has completed.
     * 
     * @return true if completed
     */
    public boolean isDone() {
        return underlying.isDone();
    }
    
    /**
     * Checks if this future was cancelled.
     * 
     * @return true if cancelled
     */
    public boolean isCancelled() {
        return underlying.isCancelled();
    }
    
    /**
     * Attempts to cancel execution of this future.
     * 
     * @param mayInterruptIfRunning true if the thread executing this task
     *        should be interrupted; otherwise, in-progress tasks are allowed
     *        to complete
     * @return false if the task could not be cancelled, typically because
     *         it has already completed normally; true otherwise
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        return underlying.cancel(mayInterruptIfRunning);
    }
    
    /**
     * Checks if this future completed successfully.
     * 
     * @return true if completed successfully
     */
    public boolean isSuccess() {
        return underlying.isDone() && !underlying.isCompletedExceptionally();
    }
    
    /**
     * Checks if this future completed with an error.
     * 
     * @return true if completed with an error
     */
    public boolean isFailure() {
        return underlying.isCompletedExceptionally();
    }
    
    /**
     * Blocks and waits for the future to complete, then returns the value.
     * 
     * @return The value
     * @throws ExecutionException if the computation threw an exception
     * @throws InterruptedException if the current thread was interrupted
     */
    public T get() throws ExecutionException, InterruptedException {
        return underlying.get();
    }
    
    /**
     * Blocks and waits for the future to complete with a timeout.
     * 
     * @param timeout The maximum time to wait
     * @param unit The time unit
     * @return The value
     * @throws ExecutionException if the computation threw an exception
     * @throws InterruptedException if the current thread was interrupted
     * @throws TimeoutException if the timeout elapsed
     */
    public T get(long timeout, TimeUnit unit) 
            throws ExecutionException, InterruptedException, TimeoutException {
        return underlying.get(timeout, unit);
    }
    
    /**
     * Transforms the value of this future using the given function.
     * 
     * <p>If this future fails, the error is propagated to the result future.</p>
     * 
     * @param <R> The type of the result
     * @param mapper The transformation function
     * @return A new future with the transformed value
     */
    public <R> Future<R> map(Function<? super T, ? extends R> mapper) {
        return new Future<>(underlying.thenApply(mapper));
    }
    
    /**
     * Transforms the value of this future using a function that returns a future.
     * 
     * <p>This is useful for chaining asynchronous operations. The result is
     * flattened so you don't get Future&lt;Future&lt;R&gt;&gt;.</p>
     * 
     * @param <R> The type of the result
     * @param mapper The transformation function
     * @return A new future with the transformed value
     */
    public <R> Future<R> flatMap(Function<? super T, Future<R>> mapper) {
        return new Future<>(underlying.thenCompose(value -> mapper.apply(value).underlying));
    }
    
    /**
     * Registers a callback to be executed when this future completes successfully.
     * 
     * @param callback The callback to execute
     * @return This future (for chaining)
     */
    public Future<T> onSuccess(Consumer<? super T> callback) {
        underlying.thenAccept(callback);
        return this;
    }
    
    /**
     * Registers a callback to be executed when this future fails.
     * 
     * @param callback The callback to execute
     * @return This future (for chaining)
     */
    public Future<T> onFailure(Consumer<Throwable> callback) {
        underlying.exceptionally(error -> {
            callback.accept(error);
            return null;
        });
        return this;
    }
    
    /**
     * Registers a callback to be executed when this future completes (success or failure).
     * 
     * @param callback The callback to execute
     * @return This future (for chaining)
     */
    public Future<T> onComplete(Consumer<? super T> callback) {
        underlying.whenComplete((value, error) -> {
            if (error == null) {
                callback.accept(value);
            }
        });
        return this;
    }
    
    /**
     * Returns a future that completes with the given value if this future fails.
     * 
     * @param defaultValue The default value
     * @return A new future that cannot fail
     */
    public Future<T> recover(T defaultValue) {
        return new Future<>(underlying.exceptionally(error -> defaultValue));
    }
    
    /**
     * Returns a future that completes with the result of the recovery function if this future fails.
     * 
     * @param recovery The recovery function
     * @return A new future
     */
    public Future<T> recoverWith(Function<Throwable, T> recovery) {
        return new Future<>(underlying.exceptionally(recovery));
    }
    
    /**
     * Combines this future with another future using the given function.
     * 
     * @param <U> The type of the other future's value
     * @param <R> The type of the result
     * @param other The other future
     * @param combiner The combining function
     * @return A new future with the combined result
     */
    public <U, R> Future<R> zip(Future<U> other, java.util.function.BiFunction<? super T, ? super U, ? extends R> combiner) {
        return new Future<>(underlying.thenCombine(other.underlying, combiner));
    }
    
    /**
     * Creates a future that completes when all given futures complete.
     * 
     * @param futures The futures to wait for
     * @return A future that completes when all futures complete
     */
    @SafeVarargs
    public static Future<Void> all(Future<?>... futures) {
        CompletableFuture<?>[] cfs = new CompletableFuture[futures.length];
        for (int i = 0; i < futures.length; i++) {
            cfs[i] = futures[i].underlying;
        }
        return new Future<>(CompletableFuture.allOf(cfs));
    }
    
    /**
     * Creates a future that completes when any of the given futures completes.
     * 
     * @param <T> The type of the value
     * @param futures The futures to race
     * @return A future that completes with the first result
     */
    @SafeVarargs
    public static <T> Future<Object> any(Future<T>... futures) {
        CompletableFuture<?>[] cfs = new CompletableFuture[futures.length];
        for (int i = 0; i < futures.length; i++) {
            cfs[i] = futures[i].underlying;
        }
        return new Future<>(CompletableFuture.anyOf(cfs));
    }
    
    /**
     * Creates a future that completes after the specified timeout.
     * If the computation completes before the timeout, its value is returned.
     * If the timeout elapses first, a TimeoutException is thrown.
     * 
     * @param <T> The type of the value
     * @param timeoutMillis The timeout in milliseconds
     * @param computation The computation to execute
     * @return A future that will timeout if it takes too long
     */
    public static <T> Future<T> timeout(long timeoutMillis, Callable<T> computation) {
        CompletableFuture<T> future = new CompletableFuture<>();
        
        // Start the computation
        CompletableFuture.supplyAsync(() -> {
            try {
                return computation.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, DEFAULT_EXECUTOR).whenComplete((result, error) -> {
            if (error != null) {
                future.completeExceptionally(error);
            } else {
                future.complete(result);
            }
        });
        
        // Schedule timeout on dedicated scheduler to guarantee firing
        SCHEDULER.schedule(() -> {
            future.completeExceptionally(
                new TimeoutException("Operation timed out after " + timeoutMillis + "ms")
            );
        }, timeoutMillis, TimeUnit.MILLISECONDS);
        
        return new Future<>(future);
    }
    
    /**
     * Applies a timeout to this future.
     * 
     * @param timeoutMillis The timeout in milliseconds
     * @return A future that will timeout if it takes too long
     */
    public Future<T> withTimeout(long timeoutMillis) {
        return new Future<>(underlying.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS));
    }
}

