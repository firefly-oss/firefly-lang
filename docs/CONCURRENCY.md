# Firefly Concurrency Features

Firefly provides a unique set of concurrency primitives that make async programming more ergonomic and expressive. These features are first-class language constructs, not library functions.

## Table of Contents

1. [Concurrent Expression](#concurrent-expression)
2. [Race Expression](#race-expression)
3. [Timeout Expression](#timeout-expression)
4. [Coalesce Operator](#coalesce-operator)
5. [Combining Features](#combining-features)

---

## Concurrent Expression

The `concurrent` expression allows you to execute multiple async operations in parallel and wait for all of them to complete.

### Syntax

```firefly
concurrent {
    let binding1 = asyncExpr1.await,
    let binding2 = asyncExpr2.await,
    let bindingN = asyncExprN.await
}
```

### Example

```firefly
async fn fetchUserData(userId: Int) -> UserData {
    concurrent {
        let profile = fetchProfile(userId).await,
        let posts = fetchPosts(userId).await,
        let friends = fetchFriends(userId).await
    }
}
```

### Key Features

- **Structured Concurrency**: All operations must complete before the block returns
- **Bound Lifetime**: If the parent function is cancelled, all concurrent operations are cancelled
- **Type Safety**: Each binding is properly typed based on the async expression
- **Async Context Only**: Must be used within an `async` function

### Semantics

1. All expressions are started simultaneously
2. The block waits for all operations to complete
3. If any operation fails, the entire block fails (fail-fast)
4. Results are available as bindings within the block scope

---

## Race Expression

The `race` expression executes multiple operations concurrently and returns the result of the first one to complete, cancelling the others.

### Syntax

```firefly
race {
    expression1;
    expression2;
    expressionN
}
```

### Example

```firefly
async fn fetchFromFastest(key: String) -> Data? {
    race {
        fetchFromCache(key).await;
        fetchFromPrimaryDB(key).await;
        fetchFromReplicaDB(key).await
    }
}
```

### Key Features

- **First-Wins**: Returns the first completed operation's result
- **Automatic Cancellation**: Other operations are cancelled once one completes
- **Fallback Chains**: Can be used to implement retry logic with multiple backends
- **Type Inference**: Return type is inferred from the block's expressions

### Use Cases

- Multi-source data fetching (cache vs database)
- Load balancing across replicas
- Timeout implementation with fallback
- Network resilience patterns

---

## Timeout Expression

The `timeout` expression wraps an async operation with a time limit, returning `None` if the operation doesn't complete in time.

### Syntax

```firefly
timeout(durationInMs) {
    asyncOperation.await
}
```

### Example

```firefly
async fn fetchWithTimeout(url: String) -> Data? {
    timeout(5000) {
        fetchData(url).await
    }
}
```

### Key Features

- **Time-Bounded Operations**: Automatically cancels operations that take too long
- **Optional Result**: Returns `T?` (Optional type) - `Some(result)` on success, `None` on timeout
- **Millisecond Precision**: Duration is specified in milliseconds
- **Composable**: Can be nested with other async expressions

### Type Transformation

```firefly
async fn foo() -> T { ... }

// Wrapping with timeout changes type to T?
let result: T? = timeout(1000) {
    foo().await
};
```

---

## Coalesce Operator

The null-coalescing operator `??` provides a concise way to handle optional values with default fallbacks.

### Syntax

```firefly
optionalValue ?? fallbackValue
```

### Example

```firefly
fn getDisplayName(user: User?) -> String {
    user?.name ?? "Anonymous"
}

async fn fetchWithFallback(url: String) -> Data {
    let result = timeout(3000) {
        fetchFromNetwork(url).await
    };
    
    result ?? getCachedData(url)
}
```

### Key Features

- **Null Safety**: Safely handle optional values without explicit unwrapping
- **Chain Multiple Fallbacks**: Can chain multiple `??` operators
- **Short-Circuit Evaluation**: Right side only evaluated if left is `None`
- **Type Inference**: Return type is the non-optional type

### Combining with Safe Access

```firefly
// Safe navigation combined with coalesce
let email = user?.profile?.contact?.email ?? "no-email@example.com"
```

---

## Combining Features

Firefly's concurrency features are designed to work together seamlessly, enabling complex async patterns with minimal boilerplate.

### Example 1: Timeout + Race

Try multiple sources with a global timeout:

```firefly
async fn smartFetch(key: String) -> Data? {
    timeout(10000) {
        race {
            fetchFromCDN(key).await;
            fetchFromDatabase(key).await;
            fetchFromBackup(key).await
        }
    }
}
```

### Example 2: Concurrent + Race + Coalesce

Parallel fetches with fallback strategy:

```firefly
async fn robustFetch(userId: Int) -> UserData {
    let data = timeout(5000) {
        race {
            concurrent {
                let cache = fetchFromCache(userId).await,
                let db = fetchFromDB(userId).await
            };
            fetchFromBackup(userId).await
        }
    };
    
    data ?? getDefaultUserData()
}
```

### Example 3: Adaptive Timeout Strategy

Different timeout thresholds for different data sources:

```firefly
async fn adaptiveFetch(url: String) -> Data? {
    race {
        // Try fast CDN first with short timeout
        timeout(500) {
            fetchFromCDN(url).await
        };
        
        // Fall back to origin with longer timeout
        timeout(3000) {
            fetchFromOrigin(url).await
        };
        
        // Final fallback - no timeout
        fetchFromCache(url).await
    }
}
```

---

## Comparison with Other Languages

### Firefly vs JavaScript

**JavaScript (Promise.all + Promise.race)**:
```javascript
// Concurrent
const results = await Promise.all([
    fetch1(),
    fetch2(),
    fetch3()
]);

// Race
const result = await Promise.race([
    fetch1(),
    fetch2(),
    fetch3()
]);
```

**Firefly**:
```firefly
// Concurrent
concurrent {
    let r1 = fetch1().await,
    let r2 = fetch2().await,
    let r3 = fetch3().await
}

// Race
race {
    fetch1().await;
    fetch2().await;
    fetch3().await
}
```

### Firefly vs Rust

**Rust (tokio)**:
```rust
// Concurrent
let (r1, r2, r3) = tokio::join!(
    fetch1(),
    fetch2(),
    fetch3()
);

// Race
tokio::select! {
    r1 = fetch1() => r1,
    r2 = fetch2() => r2,
    r3 = fetch3() => r3,
}
```

**Firefly**:
```firefly
// More readable and consistent with language syntax
concurrent {
    let r1 = fetch1().await,
    let r2 = fetch2().await,
    let r3 = fetch3().await
}

race {
    fetch1().await;
    fetch2().await;
    fetch3().await
}
```

---

## Best Practices

### 1. Always Set Reasonable Timeouts

```firefly
// Good
async fn fetchData() -> Data? {
    timeout(5000) {
        networkCall().await
    }
}

// Bad - no timeout, might hang forever
async fn fetchData() -> Data {
    networkCall().await
}
```

### 2. Use Coalesce for Graceful Degradation

```firefly
// Good - always returns something
async fn getConfig() -> Config {
    timeout(1000) {
        fetchRemoteConfig().await
    } ?? getLocalConfig()
}
```

### 3. Combine Race with Timeouts for Resilience

```firefly
// Try multiple sources, each with its own timeout
race {
    timeout(500) { fastSource().await };
    timeout(2000) { reliableSource().await };
    timeout(5000) { backupSource().await }
}
```

### 4. Use Concurrent for Independent Operations

```firefly
// Good - operations are independent
concurrent {
    let user = fetchUser().await,
    let settings = fetchSettings().await,
    let notifications = fetchNotifications().await
}

// Bad - second depends on first, use sequential await
let user = fetchUser().await;
let profile = fetchProfile(user.id).await;
```

---

## Error Handling

All concurrency features integrate with Firefly's `Result<T>` type for robust error handling:

```firefly
async fn safeOperation() -> Result<Data> {
    let result = timeout(3000) {
        riskyOperation().await
    };
    
    match result {
        Some(data) => Ok(data),
        None => Err("Operation timed out")
    }
}
```

---

## Performance Considerations

1. **Concurrent**: Launches all operations immediately - use for truly parallel work
2. **Race**: May waste resources on cancelled operations - use judiciously
3. **Timeout**: Adds scheduling overhead - set realistic timeouts
4. **Coalesce**: Zero overhead - purely syntactic sugar

---

## Summary

Firefly's concurrency model provides:

✅ **Ergonomic**: Natural syntax that reads like synchronous code  
✅ **Safe**: Structured concurrency with bounded lifetimes  
✅ **Composable**: Features work together seamlessly  
✅ **Expressive**: Covers common async patterns without external libraries  
✅ **Type-Safe**: Full type inference and checking

These primitives enable building robust, concurrent systems with minimal boilerplate while maintaining code readability and safety.
