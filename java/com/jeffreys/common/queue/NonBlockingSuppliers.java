package com.jeffreys.common.queue;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public class NonBlockingSuppliers {

  @FunctionalInterface
  private interface Getter<T> {
    T getValue() throws ExecutionException;
  }

  private static final class ValueGetter<T> implements Getter<T> {
    private final T value;

    private ValueGetter(T value) {
      this.value = value;
    }

    @Override
    public T getValue() {
      return value;
    }
  }

  private static final class ThrowingGetter<T> implements Getter<T> {
    private final Throwable throwable;

    private ThrowingGetter(Throwable throwable) {
      this.throwable = throwable;
    }

    @Override
    public T getValue() throws ExecutionException {
      throw new ExecutionException(throwable);
    }
  }

  private static final class NonBlockingSupplierImpl<T> implements NonBlockingSupplier<T> {
    private final BlockingQueue<Getter<T>> queue;

    private NonBlockingSupplierImpl(int capacity, BlockingQueue<Getter<T>> queue) {
      this.queue = queue;
    }

    @Override
    @Nullable
    public T get(Duration timeout) throws InterruptedException, ExecutionException {
      Getter<T> value = queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (value == null) {
        return null;
      }
      return value.getValue();
    }
  }

  private static final class QueuePopulator<T> implements Runnable {
    private final BlockingQueue<Getter<T>> queue;
    private final Supplier<T> blockingSupplier;

    private QueuePopulator(BlockingQueue<Getter<T>> queue, Supplier<T> blockingSupplier) {
      this.queue = queue;
      this.blockingSupplier = blockingSupplier;
    }

    @Override
    public void run() {
      while (true) {
        Getter<T> queueValue;
        try {
          queueValue = new ValueGetter<>(blockingSupplier.get());
        } catch (Throwable t) {
          queueValue = new ThrowingGetter<>(t);
        }

        try {
          queue.put(queueValue);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }
  }

  /** Converts a blocking {@link Supplier} into a non-blocking one. */
  public static <T> NonBlockingSupplier<T> createNonBlockingSupplier(
      int capacity, Executor executor, Supplier<T> blockingSupplier) {
    BlockingQueue<Getter<T>> queue = new ArrayBlockingQueue<>(capacity);
    executor.execute(new QueuePopulator<>(queue, blockingSupplier));
    return new NonBlockingSupplierImpl<>(capacity, queue);
  }
}
