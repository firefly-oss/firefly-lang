package com.firefly.runtime.async;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StructuredScope.
 */
public class StructuredScopeTest {
    
    @Test
    @Timeout(5)
    public void testBasicFork() {
        StructuredScope.scoped(scope -> {
            Future<String> f1 = scope.fork(() -> "hello");
            Future<Integer> f2 = scope.fork(() -> 42);
            
            scope.join();
            
            assertEquals("hello", f1.get());
            assertEquals(42, f2.get());
        });
    }
    
    @Test
    @Timeout(5)
    public void testMultipleTasks() {
        AtomicInteger counter = new AtomicInteger(0);
        
        StructuredScope.scoped(scope -> {
            for (int i = 0; i < 10; i++) {
                scope.fork(() -> {
                    counter.incrementAndGet();
                    return null;
                });
            }
            scope.join();
        });
        
        assertEquals(10, counter.get());
    }
    
    @Test
    @Timeout(5)
    public void testScopeWithResult() {
        String result = StructuredScope.scoped(scope -> {
            Future<String> f1 = scope.fork(() -> "Hello");
            Future<String> f2 = scope.fork(() -> " World");
            
            scope.join();
            
            return f1.get() + f2.get();
        });
        
        assertEquals("Hello World", result);
    }
    
    @Test
    @Timeout(5)
    public void testErrorPropagation() {
        assertThrows(RuntimeException.class, () -> {
            StructuredScope.scoped(scope -> {
                scope.fork(() -> {
                    throw new RuntimeException("Task failed");
                });
                scope.join();
            });
        });
    }
    
    @Test
    @Timeout(5)
    public void testCancellationOnError() {
        AtomicBoolean taskExecuted = new AtomicBoolean(false);
        
        assertThrows(RuntimeException.class, () -> {
            try (StructuredScope scope = StructuredScope.open()) {
                // First task fails immediately
                scope.fork(() -> {
                    throw new RuntimeException("Fail fast");
                });
                
                // Second task should be cancelled
                scope.fork(() -> {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Expected - task was cancelled
                    }
                    taskExecuted.set(true);
                    return null;
                });
                
                scope.join();
            }
        });
        
        // Second task should not have completed
        assertFalse(taskExecuted.get());
    }
    
    @Test
    @Timeout(5)
    public void testPermissiveScope() {
        AtomicInteger successCount = new AtomicInteger(0);
        
        assertThrows(RuntimeException.class, () -> {
            try (StructuredScope scope = StructuredScope.openPermissive()) {
                // Task 1 fails
                scope.fork(() -> {
                    throw new RuntimeException("Task 1 failed");
                });
                
                // Task 2 succeeds
                scope.fork(() -> {
                    successCount.incrementAndGet();
                    return null;
                });
                
                // Task 3 succeeds
                scope.fork(() -> {
                    successCount.incrementAndGet();
                    return null;
                });
                
                scope.join();
            }
        });
        
        // In permissive mode, successful tasks still complete
        assertEquals(2, successCount.get());
    }
    
    @Test
    @Timeout(5)
    public void testCustomParallelism() {
        StructuredScope.scoped(scope -> {
            Future<Integer> f = scope.fork(() -> 123);
            scope.join();
            assertEquals(123, f.get());
        });
    }
    
    @Test
    @Timeout(5)
    public void testVoidTask() {
        AtomicBoolean executed = new AtomicBoolean(false);
        
        StructuredScope.scoped(scope -> {
            scope.fork(() -> executed.set(true));
            scope.join();
        });
        
        assertTrue(executed.get());
    }
    
    @Test
    @Timeout(5)
    public void testFutureIsDone() {
        StructuredScope.scoped(scope -> {
            Future<String> f = scope.fork(() -> "test");
            
            // Initially may not be done
            // Wait for completion
            scope.join();
            
            // After join, should be done
            assertTrue(f.isDone());
            assertTrue(f.isSuccess());
            assertFalse(f.isCancelled());
        });
    }
    
    @Test
    @Timeout(5)
    public void testTimeout() {
        assertThrows(RuntimeException.class, () -> {
            try (StructuredScope scope = StructuredScope.open()) {
                scope.fork(() -> {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
                
                // Join with 500ms timeout
                scope.join(500);
            }
        });
    }
    
    @Test
    @Timeout(5)
    public void testCloseAutomatically() {
        AtomicBoolean taskCompleted = new AtomicBoolean(false);
        
        try (StructuredScope scope = StructuredScope.open()) {
            scope.fork(() -> {
                taskCompleted.set(true);
                return null;
            });
            // Scope closes automatically, waiting for tasks
        }
        
        assertTrue(taskCompleted.get());
    }
    
    @Test
    public void testHasFailed() {
        try {
            try (StructuredScope scope = StructuredScope.open()) {
                assertFalse(scope.hasFailed());
                
                scope.fork(() -> {
                    throw new RuntimeException("Failure");
                });
                
                scope.join();
            }
        } catch (Exception e) {
            // Expected
        }
    }
}
