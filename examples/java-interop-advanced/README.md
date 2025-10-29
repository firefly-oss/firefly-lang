# Java Interop Advanced (Flylang)

Demonstrates calling Java static methods, using Java collections, and Java time API from Flylang.

## Build & Run

```bash
mvn -q -DskipTests package
mvn -q exec:java
```

Expected output (example):
```
size=3
max=42
2025-10-28 16:30:00
```
