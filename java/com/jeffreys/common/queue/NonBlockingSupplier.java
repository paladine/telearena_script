package com.jeffreys.common.queue;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

@FunctionalInterface
public interface NonBlockingSupplier<T> {
  @Nullable
  T get(Duration timeout) throws InterruptedException, ExecutionException;
}
