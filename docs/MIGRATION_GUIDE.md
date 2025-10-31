# Migration Guide: Java Time to Firefly Native Types

## Overview

This guide helps you migrate from `java.time.*` types to Firefly's native time types in `firefly::std::time`. The native types provide a cleaner, more idiomatic API while maintaining full compatibility with the JVM ecosystem.

## Why Migrate?

1. **Idiomatic Firefly APIs** - Methods designed for Firefly's syntax and conventions
2. **Cleaner Code** - Simpler method names and patterns
3. **Better Integration** - Works seamlessly with Firefly's type system
4. **Future-Proof** - Native types will receive first-class support for new features

## Quick Reference

| Java Type | Firefly Type | Module |
|-----------|--------------|--------|
| `java.time.LocalDate` | `Date` | `firefly::std::time` |
| `java.time.LocalDateTime` | `DateTime` | `firefly::std::time` |
| `java.time.Instant` | `Instant` | `firefly::std::time` |
| `java.time.Duration` | `Duration` | `firefly::std::time` |

## Migration Examples

### 1. Date Operations

**Before (java.time):**
```firefly
use java::time::LocalDate

let today: LocalDate = LocalDate::now()
let birthday: LocalDate = LocalDate::of(1990, 5, 15)
let nextWeek: LocalDate = today::plusDays(7)

let year: Int = birthday::getYear()
let month: Int = birthday::getMonthValue()
let day: Int = birthday::getDayOfMonth()
```

**After (Firefly native):**
```firefly
use firefly::std::time::Date

let today: Date = Date.now()
let birthday: Date = Date.of(1990, 5, 15)
let nextWeek: Date = today.plusDays(7)

let year: Int = birthday.year()
let month: Int = birthday.month()
let day: Int = birthday.day()
```

**Key Changes:**
- Static methods use `.` instead of `::`
- Getter methods simplified: `getYear()` → `year()`
- Consistent naming: `getDayOfMonth()` → `day()`, `getMonthValue()` → `month()`

### 2. DateTime Operations

**Before (java.time):**
```firefly
use java::time::LocalDateTime

let now: LocalDateTime = LocalDateTime::now()
let meeting: LocalDateTime = LocalDateTime::of(2025, 10, 31, 14, 30, 0)
let later: LocalDateTime = now::plusHours(2)

let hour: Int = meeting::getHour()
let minute: Int = meeting::getMinute()
let second: Int = meeting::getSecond()
```

**After (Firefly native):**
```firefly
use firefly::std::time::DateTime

let now: DateTime = DateTime.now()
let meeting: DateTime = DateTime.of(2025, 10, 31, 14, 30, 0)
let later: DateTime = now.plusHours(2)

let hour: Int = meeting.hour()
let minute: Int = meeting.minute()
let second: Int = meeting.second()
```

**Key Changes:**
- Same pattern as Date: simplified getters
- Methods maintain the same semantics

### 3. Instant (Timestamps)

**Before (java.time):**
```firefly
use java::time::Instant

let timestamp: Instant = Instant::now()
let epoch: Instant = Instant::ofEpochSecond(0)
let epochMilli: Long = timestamp::toEpochMilli()
let later: Instant = timestamp::plusSeconds(3600)
```

**After (Firefly native):**
```firefly
use firefly::std::time::Instant

let timestamp: Instant = Instant.now()
let epoch: Instant = Instant.ofEpochSecond(0)
let epochMilli: Long = timestamp.toEpochMilli()
let later: Instant = timestamp.plusSeconds(3600)
```

**Key Changes:**
- Static methods use `.` syntax
- Instance methods maintain compatibility with java.time API

### 4. Duration (Time Spans)

**Before (java.time):**
```firefly
use java::time::Duration

let oneHour: Duration = Duration::ofHours(1)
let fiveMin: Duration = Duration::ofMinutes(5)
let total: Duration = oneHour::plus(fiveMin)

let seconds: Long = total::getSeconds()
let isZero: Bool = total::isZero()
```

**After (Firefly native):**
```firefly
use firefly::std::time::Duration

let oneHour: Duration = Duration.ofHours(1)
let fiveMin: Duration = Duration.ofMinutes(5)
let total: Duration = oneHour.plus(fiveMin)

let seconds: Long = total.toSeconds()
let isZero: Bool = total.isZero()
```

**Key Changes:**
- Static constructor methods use `.` syntax
- `getSeconds()` → `toSeconds()` for consistency with other conversions

## Comparison Operations

### Before (java.time):
```firefly
use java::time::LocalDate

let date1: LocalDate = LocalDate::of(2025, 10, 31)
let date2: LocalDate = LocalDate::of(2025, 11, 1)

if (date1::isBefore(date2)) {
    println("date1 is before date2")
}
```

### After (Firefly native):
```firefly
use firefly::std::time::Date

let date1: Date = Date.of(2025, 10, 31)
let date2: Date = Date.of(2025, 11, 1)

if (date1.isBefore(date2)) {
    println("date1 is before date2")
}
```

## Parsing and Formatting

### Before (java.time):
```firefly
use java::time::LocalDate
use java::time::format::DateTimeFormatter

let text: String = "2025-10-31"
let date: LocalDate = LocalDate::parse(text)
let formatted: String = date::format(DateTimeFormatter::ISO_DATE)
```

### After (Firefly native):
```firefly
use firefly::std::time::Date

let text: String = "2025-10-31"
let date: Date = Date.parse(text)
let formatted: String = date.format()  // ISO-8601 by default
```

**Key Changes:**
- Built-in formatting with sensible defaults
- Simplified API for common use cases

## Complete Example Migration

### Before (java.time):
```firefly
module example::scheduling

use java::time::{LocalDate, LocalDateTime, Duration}

class Scheduler {
    pub fn scheduleEvent(name: String, daysFromNow: Int) -> Void {
        let today: LocalDate = LocalDate::now()
        let eventDate: LocalDate = today::plusDays(daysFromNow)
        
        let eventTime: LocalDateTime = LocalDateTime::of(
            eventDate::getYear(),
            eventDate::getMonthValue(),
            eventDate::getDayOfMonth(),
            14, 30, 0
        )
        
        let now: LocalDateTime = LocalDateTime::now()
        let until: Duration = Duration::between(now, eventTime)
        
        println("Event: " + name)
        println("Date: " + eventDate::toString())
        println("Time until: " + until::toHours() + " hours")
    }
}
```

### After (Firefly native):
```firefly
module example::scheduling

use firefly::std::time::{Date, DateTime, Duration}

class Scheduler {
    pub fn scheduleEvent(name: String, daysFromNow: Int) -> Void {
        let today: Date = Date.now()
        let eventDate: Date = today.plusDays(daysFromNow)
        
        let eventTime: DateTime = DateTime.of(
            eventDate.year(),
            eventDate.month(),
            eventDate.day(),
            14, 30, 0
        )
        
        let now: DateTime = DateTime.now()
        let until: Duration = Duration.between(now, eventTime)
        
        println("Event: " + name)
        println("Date: " + eventDate.format())
        println("Time until: " + until.toHours() + " hours")
    }
}
```

## Method Name Mapping

### Date/DateTime Getters

| Java Method | Firefly Method |
|-------------|----------------|
| `getYear()` | `year()` |
| `getMonthValue()` | `month()` |
| `getDayOfMonth()` | `day()` |
| `getHour()` | `hour()` |
| `getMinute()` | `minute()` |
| `getSecond()` | `second()` |
| `getNano()` | `nano()` |

### Duration Conversions

| Java Method | Firefly Method |
|-------------|----------------|
| `getSeconds()` | `toSeconds()` |
| `getNano()` | `toNanos()` |
| `toMillis()` | `toMillis()` |
| `toHours()` | `toHours()` |
| `toMinutes()` | `toMinutes()` |

### Static Constructors

| Java Syntax | Firefly Syntax |
|-------------|----------------|
| `LocalDate::now()` | `Date.now()` |
| `LocalDate::of(...)` | `Date.of(...)` |
| `Duration::ofHours(1)` | `Duration.ofHours(1)` |
| `Instant::ofEpochSecond(0)` | `Instant.ofEpochSecond(0)` |

## Interoperability

Firefly native types wrap `java.time.*` types, so they're fully interoperable:

```firefly
use firefly::std::time::Date
use java::time::LocalDate

// Native Firefly type
let fireflyDate: Date = Date.now()

// Get underlying Java type when needed
let javaDate: LocalDate = fireflyDate.toJavaLocalDate()

// Use with Java libraries
someJavaLibrary::processDate(javaDate)
```

## Migration Checklist

- [ ] Update imports from `java::time::*` to `firefly::std::time::*`
- [ ] Change type names: `LocalDate` → `Date`, `LocalDateTime` → `DateTime`
- [ ] Update static method calls: `::` → `.`
- [ ] Simplify getter methods: `getYear()` → `year()`
- [ ] Update duration conversions: `getSeconds()` → `toSeconds()`
- [ ] Replace custom formatting with `.format()` where applicable
- [ ] Test thoroughly - behavior should be identical

## Gradual Migration

You can migrate gradually by using both types in the same codebase:

```firefly
use java::time::LocalDate
use firefly::std::time::Date

class MixedExample {
    pub fn process() -> Void {
        // Old code using java.time
        let javaDate: LocalDate = LocalDate::now()
        
        // New code using Firefly native
        let fireflyDate: Date = Date.now()
        
        // Both work fine together
        println("Java: " + javaDate::toString())
        println("Firefly: " + fireflyDate.format())
    }
}
```

## Benefits Summary

✅ **Cleaner syntax** - Less verbose method calls  
✅ **Consistent patterns** - All native types follow same conventions  
✅ **Better IDE support** - Native types integrate with Firefly tooling  
✅ **Future-proof** - Will receive language-level optimizations  
✅ **Fully compatible** - Can convert to/from java.time when needed  

## Need Help?

- Check the [Type System Documentation](TYPE_SYSTEM.md)
- See [Examples](../examples/types-showcase/)
- Join the community discussions

---

**Note:** The java.time types will continue to work in Firefly. This migration is recommended but not required.
