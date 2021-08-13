/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.util.function;

import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;

/**
 * Utilities relating to functions.
 */
public abstract class Functions {
  private Functions() {}
 

  /** Returns a list generated by applying a function to each index between
   * 0 and {@code size} - 1. */
  public static <E> List<E> generate(final int size,
      final Function1<Integer, E> fn) {
    if (size < 0) {
      throw new IllegalArgumentException();
    }
    return new GeneratingList<>(size, fn);
  }
  
  /**
   * Returns a function of arity 2 that does nothing.
   *
   * @param <R> Return type
   * @param <T0> Type of parameter 0
   * @param <T1> Type of parameter 1
   * @return Function that does nothing.
   */
  public static <R, T0, T1> Function2<R, T0, T1> ignore2() {
    //noinspection unchecked
    return Ignore.INSTANCE;
  }
 

  /** Ignore.
   *
   * @param <R> result type
   * @param <T0> first argument type
   * @param <T1> second argument type */
  private static final class Ignore<R, T0, T1>
      implements Function0<R>, Function1<T0, R>, Function2<T0, T1, R> {
    public R apply() {
      return null;
    }

    public R apply(T0 p0) {
      return null;
    }

    public R apply(T0 p0, T1 p1) {
      return null;
    }

    static final Ignore INSTANCE = new Ignore();
  }

  /** List that generates each element using a function.
   *
   * @param <E> element type */
  private static class GeneratingList<E> extends AbstractList<E>
      implements RandomAccess {
    private final int size;
    private final Function1<Integer, E> fn;

    GeneratingList(int size, Function1<Integer, E> fn) {
      this.size = size;
      this.fn = fn;
    }

    public int size() {
      return size;
    }

    public E get(int index) {
      return fn.apply(index);
    }
  }
}

// End Functions.java
