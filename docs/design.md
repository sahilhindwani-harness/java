# Design: Fibonacci Utility

## Goal

Add a small, self-contained utility class that computes Fibonacci numbers,
using memoization so repeated/large lookups don't re-do exponential work.

## Location

`util/src/main/java/io/kubernetes/client/util/Fibonacci.java`, in the
existing `util` module alongside other stateless helpers (e.g.
`PatchUtils`, `SSLUtils`). No new module or dependency is needed.

## API

```java
public final class Fibonacci {
  public static long compute(int n);
}
```

- `compute(n)` returns the `n`-th Fibonacci number (0-indexed: `F(0)=0`,
  `F(1)=1`).
- Throws `IllegalArgumentException` for `n < 0`.
- `long` is used as the return type since Fibonacci values grow quickly
  (values beyond `n=92` overflow `long`, which is an accepted, documented
  limitation rather than something this utility needs to solve).

## Memoization approach

Results are cached in a `Map<Integer, Long>` (`ConcurrentHashMap`) keyed by
`n`. `compute(n)` looks up the cache first; on a miss it computes
iteratively from the closest previously-cached value (or from 0) and
stores every intermediate result along the way, so subsequent calls for
any smaller or equal `n` are O(1).

An iterative fill (rather than recursive memoization) avoids stack depth
concerns for large `n` and keeps the cache-population logic simple and
thread-safe via `ConcurrentHashMap`.

## Testing

A JUnit 4 test class `FibonacciTest` (matching the module's existing test
conventions) covers:
- Base cases (`F(0)`, `F(1)`).
- A handful of known values.
- Repeated calls returning consistent cached results.
- `n < 0` throwing `IllegalArgumentException`.
