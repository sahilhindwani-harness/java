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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class FibonacciTest {

  @Test
  public void baseCasesShouldMatchDefinition() {
    assertEquals(0, Fibonacci.compute(0));
    assertEquals(1, Fibonacci.compute(1));
  }

  @Test
  public void knownValuesShouldBeCorrect() {
    assertEquals(1, Fibonacci.compute(2));
    assertEquals(2, Fibonacci.compute(3));
    assertEquals(5, Fibonacci.compute(5));
    assertEquals(55, Fibonacci.compute(10));
    assertEquals(6765, Fibonacci.compute(20));
  }

  @Test
  public void repeatedCallsShouldReturnConsistentResults() {
    long first = Fibonacci.compute(30);
    long second = Fibonacci.compute(30);
    assertEquals(first, second);
    assertEquals(832040, first);
  }

  @Test
  public void negativeInputShouldThrow() {
    try {
      Fibonacci.compute(-1);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }
}
