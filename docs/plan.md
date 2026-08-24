# Plan: Fibonacci Utility

See `docs/design.md` for the approach. Implementation breaks into 3 tasks:

## Task 1 — Implement `Fibonacci` class

Add `util/src/main/java/io/kubernetes/client/util/Fibonacci.java` with a
`compute(int n)` static method backed by a `ConcurrentHashMap` memo cache,
per the design doc. Validate `n >= 0`.

## Task 2 — Unit tests

Add `util/src/test/java/io/kubernetes/client/util/FibonacciTest.java`
covering base cases, known values, cache-consistency across repeated
calls, and the negative-input error case.

## Task 3 — Verify and open PR

Build/test the `util` module, then open a pull request with the design
doc, plan doc, class, and test.
