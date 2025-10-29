package com.firefly.runtime.async;

import org.junit.jupiter.api.Test;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Future.
 */
class FutureTest {

    @Test
    void testSuccessful() throws Exception {
        Future<Integer> future = Future.successful(42);
        assertTrue(future.isCompleted());
        assertTrue(future.isSuccess());
        assertFalse(future.isFailure());
        assertEquals(42, future.get());
    }

    @Test
    void testFailed() {
        Exception error = new RuntimeException("test error");
        Future<Integer> future = Future.failed(error);
        
        assertTrue(future.isCompleted());
        assertFalse(future.isSuccess());
        assertTrue(future.isFailure());
        
        assertThrows(ExecutionException.class, () -> future.get());
    }

    @Test
    void testAsync() throws Exception {
        Future<Integer> future = Future.async(() -> {
            Thread.sleep(100);
            return 42;
        });
        
        assertFalse(future.isCompleted());
        assertEquals(42, future.get());
        assertTrue(future.isCompleted());
    }

    @Test
    void testMap() throws Exception {
        Future<Integer> future1 = Future.successful(10);
        Future<Integer> future2 = future1.map(x -> x * 2);
        Future<String> future3 = future2.map(x -> "Result: " + x);
        
        assertEquals("Result: 20", future3.get());
    }

    @Test
    void testFlatMap() throws Exception {
        Future<Integer> future1 = Future.successful(10);
        Future<Integer> future2 = future1.flatMap(x -> 
            Future.async(() -> x * 2)
        );
        
        assertEquals(20, future2.get());
    }

    @Test
    void testOnSuccess() throws Exception {
        AtomicInteger result = new AtomicInteger(0);
        
        Future<Integer> future = Future.async(() -> 42);
        future.onSuccess(result::set);
        
        future.get(); // Wait for completion
        Thread.sleep(100); // Give callback time to execute
        
        assertEquals(42, result.get());
    }

    @Test
    void testOnFailure() throws Exception {
        AtomicInteger errorCount = new AtomicInteger(0);
        
        Future<Integer> future = Future.async(() -> {
            throw new RuntimeException("test error");
        });
        
        future.onFailure(error -> errorCount.incrementAndGet());
        
        try {
            future.get();
        } catch (ExecutionException e) {
            // Expected
        }
        
        Thread.sleep(100); // Give callback time to execute
        assertEquals(1, errorCount.get());
    }

    @Test
    void testOnComplete() throws Exception {
        AtomicInteger result = new AtomicInteger(0);
        
        Future<Integer> future = Future.successful(42);
        future.onComplete(result::set);
        
        Thread.sleep(100); // Give callback time to execute
        assertEquals(42, result.get());
    }

    @Test
    void testRecover() throws Exception {
        Future<Integer> future1 = Future.failed(new RuntimeException("error"));
        Future<Integer> future2 = future1.recover(0);
        
        assertEquals(0, future2.get());
    }

    @Test
    void testRecoverWith() throws Exception {
        Future<Integer> future1 = Future.failed(new RuntimeException("error"));
        Future<Integer> future2 = future1.recoverWith(error -> 42);
        
        assertEquals(42, future2.get());
    }

    @Test
    void testZip() throws Exception {
        Future<Integer> future1 = Future.successful(10);
        Future<Integer> future2 = Future.successful(20);
        Future<Integer> combined = future1.zip(future2, (a, b) -> a + b);
        
        assertEquals(30, combined.get());
    }

    @Test
    void testAll() throws Exception {
        Future<Integer> f1 = Future.async(() -> {
            Thread.sleep(50);
            return 1;
        });
        Future<Integer> f2 = Future.async(() -> {
            Thread.sleep(100);
            return 2;
        });
        Future<Integer> f3 = Future.async(() -> {
            Thread.sleep(75);
            return 3;
        });
        
        Future<Void> all = Future.all(f1, f2, f3);
        all.get(1, TimeUnit.SECONDS);
        
        assertTrue(f1.isCompleted());
        assertTrue(f2.isCompleted());
        assertTrue(f3.isCompleted());
    }

    @Test
    void testAny() throws Exception {
        Future<Integer> f1 = Future.async(() -> {
            Thread.sleep(200);
            return 1;
        });
        Future<Integer> f2 = Future.async(() -> {
            Thread.sleep(50);
            return 2;
        });
        Future<Integer> f3 = Future.async(() -> {
            Thread.sleep(150);
            return 3;
        });
        
        Future<Object> any = Future.any(f1, f2, f3);
        Object result = any.get(1, TimeUnit.SECONDS);
        
        // Should be 2 since f2 completes first
        assertEquals(2, result);
    }

    @Test
    void testChaining() throws Exception {
        Future<String> result = Future.async(() -> 10)
            .map(x -> x * 2)
            .map(x -> x + 5)
            .flatMap(x -> Future.async(() -> "Result: " + x));
        
        assertEquals("Result: 25", result.get());
    }

    @Test
    void testErrorPropagation() {
        Future<Integer> future = Future.async(() -> {
            throw new RuntimeException("error");
        });
        
        Future<Integer> mapped = future.map(x -> x * 2);
        
        assertThrows(ExecutionException.class, () -> mapped.get());
    }
}

