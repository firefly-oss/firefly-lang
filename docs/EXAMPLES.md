# Examples Index

A comprehensive, curated tour of real, runnable Flylang examples. Each project under `examples/` is a standalone Maven module that compiles `.fly` sources and demonstrates a focused set of features.

---

## Table of Contents
- Hello World
- Async Demo
- Concurrency Demo
- Futures Combinators
- Patterns Demo
- Data Patterns Demo
- Java Interop Advanced
- Sparks Demo
- Async Pipeline Demo
- Smoke Tests and Tips

---

## Hello World
- Path: `examples/hello-world`
- Run: `fly run examples/hello-world`
- Alt: `mvn -q -f examples/hello-world/pom.xml clean package && mvn -q -f examples/hello-world/pom.xml exec:java`
- Shows: module layout, entrypoint `fly()`, printing

Key snippet:
```fly
module examples::hello_world

class Main {
  pub fn fly(args: [String]) -> Void {
    println("Hello, Flylang!");
  }
}
```

Expected output:
```
Hello, Flylang!
```

## Async Demo
- Path: `examples/async-demo`
- Run: `fly run examples/async-demo`
- Shows: `async fn`, `.await`, `Future::get()` outside async

Snippet:
```fly
class Demo {
  pub async fn compute() -> Int { 40 + 2 }
  pub async fn mainAsync() -> Int { self::compute().await }
}

class Main {
  pub fn fly(args: [String]) -> Void {
    let d: Demo = new Demo();
    println("" + d::mainAsync()::get());
  }
}
```

## Concurrency Demo
- Path: `examples/concurrency-demo`
- Run: `fly run examples/concurrency-demo`
- Shows: racing tasks, timeouts, binding concurrent futures

Highlights:
```fly
let fastest: Int = Future::any(f2, f3)::get();
let value: Int = timeout(50) { self::mk(30, 7).await };
```

Tips:
- Prefer `Future::any` for first finisher; combine with `timeout` to keep latencies bounded.

## Futures Combinators
- Path: `examples/futures-combinators-demo`
- Run: `fly run examples/futures-combinators-demo`
- Shows: `Future::all`, `Future::any`, `timeout`

Snippet:
```fly
let f1: Future = self::mk(50, 10);
let f2: Future = self::mk(100, 20);
Future::all(f1, f2)::get();
println("sum=" + (f1::get() + f2::get()));
println("fastest=" + Future::any(f2, self::mk(10, 99))::get());
```

## Patterns Demo
- Path: `examples/patterns-demo`
- Run: `fly run examples/patterns-demo`
- Shows: struct patterns, tuple patterns, wildcards `_`

Snippet:
```fly
struct Point { x: Int, y: Int }
let msg: String = match (Point { x: 0, y: 0 }) {
  Point { x: 0, y: 0 } => "origin",
  Point { x, y } => "(" + x + "," + y + ")",
  _ => "other"
};
```

## Data Patterns Demo
- Path: `examples/data-patterns-demo`
- Run: `fly run examples/data-patterns-demo`
- Shows: ADTs with constructors, pattern matching on variants

Snippet:
```fly
data Result { Ok(String), Err(Int) }
let m: String = match Result::Err(404) { Err(c) => "error=" + c, _ => "ok" };
```

## Java Interop Advanced
- Path: `examples/java-interop-advanced`
- Run: `fly run examples/java-interop-advanced`
- Shows: Java collections, time API, static calls, formatting

Snippet:
```fly
use java::util::{ArrayList, Collections}
use java::lang::Math
use java::time::LocalDateTime
use java::time::format::DateTimeFormatter

let items: ArrayList = new ArrayList();
items::add("banana"); items::add("apple"); items::add("cherry");
Collections::sort(items);
println("max=" + Math::max(10, 42));
println(LocalDateTime::now()::format(DateTimeFormatter::ofPattern("yyyy-MM-dd HH:mm:ss")));
```

## Sparks Demo
- Path: `examples/sparks-demo`
- Run: `fly run examples/sparks-demo`
- Shows: sparks with `validate` and `computed` properties

Snippet:
```fly
spark Account {
  id: String,
  balance: Int,
  owner: String,
  validate { self.balance >= 0 }
  computed isActive: Bool { self.balance > 0 }
}
```

## Async Pipeline Demo
- Path: `examples/async-pipeline-demo`
- Run: `fly run examples/async-pipeline-demo`
- Shows: fan‑out/fan‑in, combining multiple async ops, formatting outputs

Snippet:
```fly
let all: Future = Future::all(p::fetchUser(), p::fetchOrders(), p::fetchBalance());
all::get();
println("first-ready=" + Future::any(p::fetchOrders(), p::fetchBalance())::get());
```

## Running Tests

### Interactive Test Runner
Run the unified test script interactively:
```bash
./scripts/test.sh
```

Menu options:
- Run all tests (Maven + Examples)
- Run Maven unit tests only
- Run all examples
- Run quick examples (subset)
- Select specific examples
- Run individual example

### Command-Line Options
```bash
./scripts/test.sh --all        # Run everything
./scripts/test.sh --unit       # Maven tests only
./scripts/test.sh --examples   # All examples
./scripts/test.sh --quick      # Fast smoke test
./scripts/test.sh --ci         # CI mode (verbose)
```

### Manual Execution
Run individual examples with Maven:
```bash
mvn -q -f examples/<name>/pom.xml clean package
mvn -q -f examples/<name>/pom.xml exec:java
```

Or use the Flylang CLI:
```bash
fly run examples/<name>
```

**Note:** Most examples are self-contained. If an example requires external services, check its README.
