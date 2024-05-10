package org.desktop2.transport.simpletorrettracker;

import java.util.function.Consumer;
import java.util.function.Function;

public class SneakyThrowsFactory {
  public static <T, R> Function<T, R> sneakyThrows(ThrowableFunction<T, R> function) {
    return t -> {
      try {
        return function.apply(t);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    };
  }

  public static <T> Consumer<T> sneakyThrows(ThrowableConsumer<T> consumer) {
    return (T arg) -> {
      try {
        consumer.apply(arg);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    };
  }

  interface ThrowableFunction<T, R> {
    R apply(T t) throws Exception;
  }

  interface ThrowableConsumer<T> {
    void apply(T t) throws Exception;
  }
}
