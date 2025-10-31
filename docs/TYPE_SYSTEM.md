# Firefly Native Type System

## Overview

Firefly has a comprehensive native type system that provides clean, idiomatic APIs while efficiently compiling to JVM bytecode. The type system consists of three layers:

1. **Compiler Types (`FireflyType.java`)** - Internal representation with JVM metadata
2. **Standard Library Types (`std/types.fly`)** - Documentation and user-facing API
3. **Native Wrappers (`std/time/*`)** - Firefly-idiomatic APIs over Java types

## Architecture

```
User Code (Firefly types)
         ↓
FireflyType (Compiler)
         ↓
JVM Bytecode
```

## Primitive Types

All primitive types are natively supported with proper JVM mappings:

| Firefly Type | JVM Type | Descriptor | Bits | Usage |
|--------------|----------|------------|------|-------|
| `Int` | `int` | `I` | 32 | Integer numbers |
| `Long` | `long` | `J` | 64 | Large integers |
| `Float` | `double` | `D` | 64 | Floating point |
| `Double` | `double` | `D` | 64 | Alias of Float |
| `Bool` | `boolean` | `Z` | 8 | Boolean values |
| `Char` | `char` | `C` | 16 | Characters |
| `Byte` | `byte` | `B` | 8 | Small integers |
| `Short` | `short` | `S` | 16 | Short integers |
| `String` | `String` | `Ljava/lang/String;` | - | Text |

### Examples

```firefly
let count: Int = 42
let price: Float = 99.99
let name: String = "Firefly"
let active: Bool = true
```

## Standard Types

### Numeric Types

```firefly
use java::math::{BigDecimal, BigInteger}

let precise: BigDecimal = new BigDecimal("99999.99")
let huge: BigInteger = new BigInteger("99999999999999999")
```

### UUID

```firefly
use java::util::UUID

let id: UUID = UUID.randomUUID()
let parsed: UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
```

## Native Date/Time Types

Firefly provides native date/time types in `firefly::std::time` that wrap `java.time.*` with idiomatic APIs:

### Date

Calendar date without time information:

```firefly
use firefly::std::time::Date

let today: Date = Date.now()
let birthday: Date = Date.of(1990, 5, 15)
let nextWeek: Date = today.plusDays(7)

let year: Int = birthday.year()
let month: Int = birthday.month()
let day: Int = birthday.day()
```

**API:**
- `Date.now() -> Date` - Current date
- `Date.of(year, month, day) -> Date` - Create from components
- `Date.parse(text) -> Date` - Parse ISO-8601
- `year() -> Int` - Get year
- `month() -> Int` - Get month (1-12)
- `day() -> Int` - Get day (1-31)
- `plusDays(days) -> Date` - Add days
- `plusMonths(months) -> Date` - Add months
- `plusYears(years) -> Date` - Add years
- `isBefore(other) -> Bool` - Comparison
- `isAfter(other) -> Bool` - Comparison

### DateTime

Date and time without timezone:

```firefly
use firefly::std::time::DateTime

let now: DateTime = DateTime.now()
let meeting: DateTime = DateTime.of(2025, 10, 31, 14, 30, 0)
let later: DateTime = now.plusHours(2)

let hour: Int = meeting.hour()
let minute: Int = meeting.minute()
```

**API:**
- `DateTime.now() -> DateTime` - Current date-time
- `DateTime.of(y, m, d, h, min, s) -> DateTime` - Create from components
- `DateTime.parse(text) -> DateTime` - Parse ISO-8601
- `year/month/day/hour/minute/second() -> Int` - Get components
- `plusHours/plusMinutes/plusDays(n) -> DateTime` - Add time
- `isBefore/isAfter(other) -> Bool` - Comparison

### Instant

Precise timestamp (UTC):

```firefly
use firefly::std::time::Instant

let timestamp: Instant = Instant.now()
let epoch: Instant = Instant.ofEpochSecond(0)
let inOneHour: Instant = timestamp.plusSeconds(3600)
```

**API:**
- `Instant.now() -> Instant` - Current timestamp
- `Instant.ofEpochSecond(sec) -> Instant` - From Unix seconds
- `Instant.ofEpochMilli(ms) -> Instant` - From Unix milliseconds
- `toEpochSecond() -> Long` - To Unix seconds
- `toEpochMilli() -> Long` - To Unix milliseconds
- `plusSeconds/plusMillis(n) -> Instant` - Add time

### Duration

Time-based amount:

```firefly
use firefly::std::time::Duration

let oneHour: Duration = Duration.ofHours(1)
let fiveMinutes: Duration = Duration.ofMinutes(5)
let total: Duration = oneHour.plus(fiveMinutes)
```

**API:**
- `Duration.zero() -> Duration` - Zero duration
- `Duration.ofHours/ofMinutes/ofSeconds(n) -> Duration` - Create
- `toSeconds() -> Long` - Convert to seconds
- `toMillis() -> Long` - Convert to milliseconds
- `plus/minus(other) -> Duration` - Arithmetic
- `isZero() -> Bool` - Check if zero
- `isNegative() -> Bool` - Check if negative

## Collection Types

```firefly
use java::util::{ArrayList, HashMap, HashSet}

let list: ArrayList = new ArrayList()
list.add(1)
list.add(2)

let map: HashMap = new HashMap()
map.put("key", "value")

let set: HashSet = new HashSet()
set.add("item")
```

## Type Resolution Priority

When resolving types, Firefly uses this priority order:

1. **Firefly Native Types** (`FireflyType` registry)
   - `Int`, `Float`, `String`, `UUID`, `Date`, etc.

2. **Explicit Imports**
   - `use java::util::UUID`

3. **Wildcard Imports**
   - `use java::util::*`

4. **Current Module**
   - Types defined in the same module

5. **Java Lang Package**
   - `String`, `Integer`, `System`, etc.

## Implementation Details

### Compiler Integration

The `FireflyType` class (`firefly-compiler/src/main/java/com/firefly/compiler/types/FireflyType.java`) contains:

- **Type Metadata**: Name, descriptor, internal name
- **JVM Opcodes**: Load, store, return opcodes for each type
- **64-bit Flag**: Special handling for `Long`, `Float`, `Double`
- **Boxing/Unboxing**: Automatic conversion methods
- **Type Conversion**: Safe conversion between compatible types

### BytecodeGenerator Integration

The compiler uses helper methods:

```java
private FireflyType getFireflyTypeFromName(String typeName)
private int getStoreOpcodeForType(VarType varType)
private int getLoadOpcodeForType(VarType varType)
private int getReturnOpcodeForType(VarType varType)
```

### TypeResolver Integration

The `TypeResolver` checks `FireflyType` registry before Java types:

```java
public Optional<String> resolveClassName(String simpleName) {
    // 1. Check Firefly native types first
    FireflyType fireflyType = FireflyType.fromFireflyName(simpleName);
    if (fireflyType != null) {
        return Optional.of(fireflyType.getJvmInternalName());
    }
    
    // 2. Then check imports, wildcards, etc...
}
```

## Testing

### REPL Tests

```firefly
# Int type
let x: Int = 42
x  # Output: 42

# Float type
let price: Float = 99.99
price  # Output: 99.99

# String type
let name: String = "Firefly"
name  # Output: Firefly

# Type inference
let auto = 3.14
auto  # Output: 3.14
```

### Verified Working

✅ All primitive types (Int, Float, String, Bool)
✅ Type annotations work correctly
✅ Type inference works correctly
✅ Arithmetic operations with typed variables
✅ String concatenation
✅ FireflyType integration in compiler
✅ TypeResolver recognizes Firefly types
✅ Correct bytecode generation for all types

## Future Enhancements

1. **More Native Wrappers**: Collections (List, Map, Set) with Firefly APIs
2. **Result Type**: Proper `Result<T, E>` integration
3. **Option Type**: Native `Option<T>` support
4. **Range Types**: `Range`, `RangeInclusive`
5. **Async Types**: `Future`, `Promise`, `Task`

## Why Native Types?

1. **Clean APIs**: Users write `Date.now()` not `LocalDate.now()`
2. **Consistency**: All date types use same naming conventions
3. **Flexibility**: Can change internal implementation without breaking user code
4. **Type Safety**: Better integration with Firefly's type system
5. **Documentation**: Unified docs in `firefly::std` namespace
6. **Future-Proof**: Room for optimizations or custom implementations

## Summary

Firefly's type system provides:
- **50+ built-in types** covering all common use cases
- **Native date/time types** with idiomatic APIs
- **Efficient JVM compilation** with correct opcodes
- **Type safety** with proper checking and inference
- **Clean separation** from Java internals

Users write idiomatic Firefly code while the compiler generates optimal JVM bytecode.
