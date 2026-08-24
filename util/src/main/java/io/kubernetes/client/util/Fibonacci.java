/*
Copyright 2026 The Kubernetes Authors.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package io.kubernetes.client.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Utility for computing Fibonacci numbers with memoization. */
public final class Fibonacci {

  private static final ConcurrentMap<Integer, Long> CACHE = new ConcurrentHashMap<>();

  static {
    CACHE.put(0, 0L);
    CACHE.put(1, 1L);
  }

  private Fibonacci() {}

  /**
   * Returns the n-th Fibonacci number (0-indexed: compute(0) == 0, compute(1) == 1).
   *
   * @throws IllegalArgumentException if n is negative
   */
  public static long compute(int n) {
    if (n < 0) {
      throw new IllegalArgumentException("n must be >= 0, got: " + n);
    }
    Long cached = CACHE.get(n);
    if (cached != null) {
      return cached;
    }
    int start = n;
    while (!CACHE.containsKey(start)) {
      start--;
    }
    long prev = CACHE.get(start);
    long prevPrev = CACHE.get(start - 1);
    for (int i = start + 1; i <= n; i++) {
      long value = prev + prevPrev;
      CACHE.put(i, value);
      prevPrev = prev;
      prev = value;
    }
    return CACHE.get(n);
  }
}
