# Flylang Complete Tutorial

This tutorial is a practical, end-to-end guide to Flylang. All code snippets are designed to be copy-pasted into a `.fly` file and compiled with the `fly` CLI.

## Install

```bash
bash scripts/install.sh --from-source --prefix "$HOME/.local"
export PATH="$HOME/.local/bin:$PATH"
fly version
```

## 1) Hello World (verified)

```fly path=null start=null
module examples::hello

class Main {
    pub fn fly(args: [String]) -> Void {
        println("Hello, Flylang!");
    }
}
```

Run:
```bash
fly run examples/hello.fly
```

## 2) Java Interop (verified)

```fly path=null start=null
module examples::interop

use java::util::ArrayList
use java::io::File

class Main {
    pub fn fly(args: [String]) -> Void {
        let list = new ArrayList();
        list.add("A");
        list.add("B");
        println(list.size());

        let f = new File("README.md");
        println(f.getName());
    }
}
```

## 3) Structs vs Sparks (verified)

Structs are immutable data objects with JavaBean getters.

```fly path=null start=null
module examples::structs

struct User { id: String, name: String }

class Main {
    pub fn fly(args: [String]) -> Void {
        let u = User { id: "U1", name: "Alice" };
        println(u.getName());
    }
}
```

Sparks are immutable smart records with validation and computed properties.

```fly path=null start=null
module examples::spark

spark Account {
    id: String,
    balance: Int,
    owner: String,

    // Validation executes during construction; throwing aborts construction
    validate { self.balance >= 0 }

    computed isActive: Bool { self.balance > 0 }

    fn deposit(amount: Int) -> Account {
        Account { id: self.id, balance: self.balance + amount, owner: self.owner }
    }
}

class Main {
    pub fn fly(args: [String]) -> Void {
        let a = Account { id: "ACC001", balance: 100, owner: "Alice" };
        println(a.isActive);
    }
}
```

## 4) Functions and Methods (verified)

```fly path=null start=null
module examples::functions

class Math {
    pub fn add(a: Int, b: Int) -> Int { a + b }
}

class Main {
    pub fn fly(args: [String]) -> Void {
        println(new Math().add(2, 3));
    }
}
```

## 5) Async/Await and Futures (verified)

```fly path=null start=null
module examples::async

use com::firefly::runtime::async::Future

class Demo {
    pub async fn compute() -> Int { 40 + 2 }
    pub async fn mainAsync() -> Int { self.compute().await }
}

class Main {
    pub fn fly(args: [String]) -> Void {
        let d = new Demo();
        println(d.mainAsync().get());
    }
}
```

Notes:
- `await` only inside `async` functions. From non-async, use `Future#get()`.
- Functions return the last expression; no explicit `return` needed.

## 6) Java Static Methods and Fields (verified)

```fly path=null start=null
module examples::static

use java::lang::Math

class Main {
    pub fn fly(args: [String]) -> Void {
        println(Math::max(10, 20));
    }
}
```

## 7) Spring Boot (verified)

See `docs/SPRING_BOOT_GUIDE.md` and `examples/spring-boot-demo` for a full REST API.

## 8) Concurrency Primitives (syntax)

Flylang syntax supports concurrency expressions:

```fly path=null start=null
module examples::concurrency

async fn fetch1() -> Int { 1 }
async fn fetch2() -> Int { 2 }

fn demo() -> Void {
    // Run tasks concurrently and bind results
    // concurrent { let a = fetch1().await, let b = fetch2().await }

    // Race between tasks, first result wins
    // race { fetch1().await; fetch2().await }

    // Timeout block
    // timeout(1000) { fetch1().await }
}
```

## CLI

```bash
fly compile path/to/file.fly
fly run path/to/file.fly
fly version
```

## Next Steps
- Explore the examples in `examples/`
- Read the full Spring Boot guide: `docs/SPRING_BOOT_GUIDE.md`
- Try building with Maven and the Flylang Maven plugin

Happy coding in Flylang! 🔥
