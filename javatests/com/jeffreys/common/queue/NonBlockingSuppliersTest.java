package com.jeffreys.common.queue;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.jeffreys.junit.Exceptions.assertThrows;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NonBlockingSuppliersTest {

  private final ExecutorService executorService = Executors.newFixedThreadPool(2);

  @After
  public void shutdownExecutorService() {
    MoreExecutors.shutdownAndAwaitTermination(executorService, Duration.ofSeconds(2));
  }

  @Test
  public void noCapacity_throwsError() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            NonBlockingSuppliers.createNonBlockingSupplier(
                /* capacity= */ 0, executorService, () -> "test"));
  }

  @Test
  public void simpleGet() throws Exception {
    NonBlockingSupplier<String> supplier =
        NonBlockingSuppliers.createNonBlockingSupplier(
            /* capacity= */ 1, executorService, () -> "test");

    assertThat(supplier.get(Duration.ofSeconds(5))).isEqualTo("test");
    assertThat(supplier.get(Duration.ofSeconds(5))).isEqualTo("test");
  }

  @Test
  public void simpleGet_timesout() throws Exception {
    NonBlockingSupplier<String> supplier =
        NonBlockingSuppliers.createNonBlockingSupplier(
            /* capacity= */ 1,
            executorService,
            new Supplier<String>() {
              @Override
              public String get() {
                Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(2));
                return "test";
              }
            });

    assertThat(supplier.get(Duration.ofSeconds(1))).isEqualTo(null);
    assertThat(supplier.get(Duration.ofSeconds(4))).isEqualTo("test");
  }

  @Test
  public void simpleGet_propagatesException() throws Exception {
    NonBlockingSupplier<String> supplier =
        NonBlockingSuppliers.createNonBlockingSupplier(
            /* capacity= */ 1,
            executorService,
            new Supplier<String>() {
              @Override
              public String get() {
                throw new IllegalArgumentException("haha");
              }
            });

    try {
      supplier.get(Duration.ofSeconds(1));
      assertWithMessage("Should have thrown").fail();
    } catch (ExecutionException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
      assertThat(expected).hasCauseThat().hasMessageThat().contains("haha");
    }
  }
}
