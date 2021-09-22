package com.jeffreys.scripts.tascript;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.flogger.FluentLogger;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import javax.annotation.Nullable;

/** Cycles through a list of movements endlessly. */
class Movements {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Iterator<String> iterator;

  public Movements(File file) throws IOException {
    this(new Scanner(new FileInputStream(file)), file);
  }

  @VisibleForTesting
  public Movements(Scanner scanner) throws IOException {
    this(loadFromScanner(scanner, null));
  }

  @VisibleForTesting
  public Movements(Scanner scanner, File file) throws IOException {
    this(loadFromScanner(scanner, file));
  }

  @VisibleForTesting
  Movements(List<String> movements) {
    if (movements.isEmpty()) {
      throw new NoSuchElementException("Empty movements");
    }
    this.iterator = Iterators.cycle(ImmutableList.copyOf(movements));
  }

  public String getNextMovement() {
    return iterator.next();
  }

  @AutoValue
  abstract static class ScannerAndFile {
    abstract Scanner getScanner();

    @Nullable
    abstract File getFile();

    private static ScannerAndFile create(Scanner scanner, File file) {
      return new AutoValue_Movements_ScannerAndFile(scanner, file);
    }
  }

  private static class LineFetcher {
    private final Deque<ScannerAndFile> scanners = new ArrayDeque<>();
    @Nullable private final File baseFile;

    public LineFetcher(Scanner initial, @Nullable File baseFile) {
      this.scanners.push(ScannerAndFile.create(initial, baseFile));
      this.baseFile = baseFile;
    }

    private boolean fileCurrentlyBeingScanned(File file) {
      return scanners.stream().anyMatch(scannerAndFile -> file.equals(scannerAndFile.getFile()));
    }

    private ScannerAndFile include(File file) throws IOException {
      logger.atInfo().log("#including movement file: %s", file);

      // resolve a relative path
      if (!file.isAbsolute()) {
        if (baseFile == null) {
          throw new IllegalArgumentException("Can't !include relative files without a base file");
        }
        file = new File(baseFile.getParent(), file.getName());
      }

      if (fileCurrentlyBeingScanned(file)) {
        throw new IllegalArgumentException(
            String.format(
                "!included movement file already included, you have a loop: %s",
                file.getAbsolutePath()));
      }

      return ScannerAndFile.create(new Scanner(file), file);
    }

    @Nullable
    public String nextLine() throws IOException {
      ScannerAndFile scannerAndFile = scanners.peekFirst();
      while (scannerAndFile != null) {
        if (!scannerAndFile.getScanner().hasNextLine()) {
          scanners.removeFirst();
          scannerAndFile = scanners.peekFirst();
          continue;
        }

        String line = scannerAndFile.getScanner().nextLine();

        if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) { // skip comments
          continue;
        } else if (line.startsWith("!include ")) { // process includes
          scannerAndFile = include(new File(line.substring(9)));
          scanners.addFirst(scannerAndFile);
          continue;
        }

        // otherwise return the line as-is
        return line;
      }
      return null;
    }
  }

  private static ImmutableList<String> loadFromScanner(Scanner scanner, @Nullable File file)
      throws IOException {
    logger.atInfo().log("Loading movement file: %s", file);

    ImmutableList.Builder<String> builder = ImmutableList.builder();
    LineFetcher fetcher = new LineFetcher(scanner, file);
    String lineValue;
    while ((lineValue = fetcher.nextLine()) != null) {
      // look for starting digits to use as a count
      int count = 0;
      int charIndex;
      for (charIndex = 0; charIndex < lineValue.length(); ++charIndex) {
        char c = lineValue.charAt(charIndex);
        if (!Character.isDigit(c)) {
          break;
        }
        count = count * 10 + Character.getNumericValue(c);
      }

      count = Math.max(1, count);
      String command = lineValue.substring(charIndex);

      for (int i = 0; i < count; ++i) {
        builder.add(command);
      }
    }
    return builder.build();
  }
}
