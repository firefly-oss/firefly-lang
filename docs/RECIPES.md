# Quick Recipes

Copy‑paste friendly snippets and commands grounded in verified examples. Use this as a practical cookbook.

---

## Run Hello World
- `fly run examples/hello-world`
- Or: `mvn -q -f examples/hello-world/pom.xml clean package && mvn -q -f examples/hello-world/pom.xml exec:java`

## Compile + Run any example
- `fly run examples/<project-name>`
- Maven alt: `mvn -q -f examples/<project-name>/pom.xml clean package && mvn -q -f examples/<project-name>/pom.xml exec:java`

## Async: define, await, and get
From `examples/async-demo`:
```fly
use com::firefly::runtime::async::Future

class Demo {
  pub async fn compute() -> Int { 40 + 2 }
}

let value: Int = Demo::compute()::get();
```

## Futures: all / any / timeout
From `examples/futures-combinators-demo`:
```fly
use com::firefly::runtime::async::Future
use java::lang::Thread

pub async fn mk(delay: Int, value: Int) -> Int { Thread::sleep(delay); value }

let f1: Future = mk(50, 10);
let f2: Future = mk(100, 20);
Future::all(f1, f2)::get();
println("sum=" + (f1::get() + f2::get()));
println("fastest=" + Future::any(f2, mk(10, 99))::get());
println("timeout-value=" + timeout(50) { mk(30, 7).await });
```

## Pattern matching (structs, tuples)
From `examples/patterns-demo`:
```fly
struct Point { x: Int, y: Int }
let p1: Point = Point { x: 0, y: 0 };
let msg1: String = match p1 {
  Point { x: 0, y: 0 } => "origin",
  Point { x, y } => "(" + x + "," + y + ")",
  _ => "other"
};
```

## Data (sum) types
From `examples/data-patterns-demo`:
```fly
data Result { Ok(String), Err(Int) }
let a: Result = Result::Ok("done");
let msg: String = match a { Ok(s) => s, _ => "unknown" };
```

## Java interop essentials
From `examples/java-interop-advanced`:
```fly
use java::util::{ArrayList, Collections}
use java::lang::Math

let items: ArrayList = new ArrayList();
items::add("banana"); items::add("apple"); items::add("cherry");
Collections::sort(items);
println("size=" + items::size());
println("max=" + Math::max(10, 42));
```

## Sparks (validated smart records)
From `examples/sparks-demo`:
```fly
spark Account {
  id: String,
  balance: Int,
  owner: String,
  validate { self.balance >= 0 }
  computed isActive: Bool { self.balance > 0 }
}
```

## Async pipeline (fan‑out/fan‑in)
From `examples/async-pipeline-demo`:
```fly
use com::firefly::runtime::async::Future

class Pipeline {
  pub async fn fetchUser() -> String { "alice" }
  pub async fn fetchOrders() -> Int { 5 }
  pub async fn fetchBalance() -> Int { 42 }
}

let all: Future = Future::all(Pipeline::fetchUser(), Pipeline::fetchOrders(), Pipeline::fetchBalance());
all::get();
println("first-ready=" + Future::any(Pipeline::fetchOrders(), Pipeline::fetchBalance())::get());
```

## Spring Boot quickstart
See docs/SPRING_BOOT_GUIDE.md; essentials in your POM:
```xml
<plugin>
  <groupId>com.firefly</groupId>
  <artifactId>firefly-maven-plugin</artifactId>
  <version>1.0-Alpha</version>
  <executions><execution><goals><goal>compile</goal></goals></execution></executions>
</plugin>
```

## Run all examples (smoke test)
- `bash scripts/smoke-test-examples.sh`
- Exits non‑zero on first failure; scan output to locate the failing project.
