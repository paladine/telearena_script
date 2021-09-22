package com.jeffreys.scripts.tascript;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;

import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.jeffreys.common.proto.Protos;
import com.jeffreys.common.queue.NonBlockingSupplier;
import com.jeffreys.common.queue.NonBlockingSuppliers;
import com.jeffreys.scripts.common.Triggers;
import com.jeffreys.scripts.tascript.Annotations.LogfilePrintWriter;
import com.jeffreys.scripts.tascript.Annotations.OutputPrintWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.time.Clock;
import java.time.Duration;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {

  private static class GuavaSleeper implements Sleeper {
    @Override
    public void sleep(Duration duration) {
      sleepUninterruptibly(duration.toMillis(), TimeUnit.MILLISECONDS);
    }
  }

  private static class ConfigurationProtoModule extends AbstractModule {
    @Override
    public void configure() {
      bind(Clock.class).toInstance(Clock.systemUTC());
      bind(Scanner.class).toInstance(new Scanner(System.in));
      bind(PrintWriter.class)
          .annotatedWith(OutputPrintWriter.class)
          .toInstance(new PrintWriter(System.out, /* autoFlush= */ true));
      bind(Sleeper.class).to(GuavaSleeper.class);
    }

    @Provides
    Configuration provideConfiguration(Options options) {
      return Protos.parseProtoFromTextFile(options.getConfigFile(), Configuration.class);
    }

    @Provides
    Movements provideMovements(Configuration configuration) {
      try {
        return new Movements(new File(configuration.getMovementFile()));
      } catch (Exception ex) {
        throw new IllegalArgumentException("Unable to create Movements", ex);
      }
    }

    @Provides
    @LogfilePrintWriter
    PrintWriter provideLogfilePrintWriter(Configuration configuration)
        throws FileNotFoundException {
      OutputStream outputStream =
          configuration.getLogFile().isEmpty()
              ? ByteStreams.nullOutputStream()
              : new FileOutputStream(configuration.getLogFile());

      return new PrintWriter(outputStream, /* autoFlush= */ true);
    }

    @Provides
    Triggers provideTriggers(Configuration configuration) {
      return Triggers.of(configuration.getTriggersList());
    }

    @Provides
    @Singleton
    ExecutorService provideExecutor() {
      return Executors.newSingleThreadExecutor();
    }

    @Provides
    @Singleton
    NonBlockingSupplier<String> provideNonBlockingSupplier(
        Scanner scanner, ExecutorService executor) {
      return NonBlockingSuppliers.createNonBlockingSupplier(
          /* capacity= */ 256, executor, scanner::nextLine);
    }
  }

  public static void main(String[] args) {
    ExecutorService executor = null;
    try {
      Injector injector =
          Guice.createInjector(new ConfigurationProtoModule(), Options.getModule(args));
      TAScript taScript = injector.getInstance(TAScript.class);
      executor = injector.getInstance(ExecutorService.class);

      taScript.run();
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(1);
    } finally {
      if (executor != null) {
        MoreExecutors.shutdownAndAwaitTermination(executor, Duration.ofSeconds(3));
      }
    }
  }
}
