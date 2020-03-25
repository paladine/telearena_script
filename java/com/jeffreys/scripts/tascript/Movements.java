package com.jeffreys.scripts.tascript;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** Cycles through a list of movements endlessly. */
class Movements {
  private final ImmutableList<String> movements;
  private final Iterator<String> iterator;

  public Movements(List<String> movements) {
    if (movements.isEmpty()) {
      throw new NoSuchElementException("Empty movements");
    }
    this.movements = ImmutableList.copyOf(movements);
    this.iterator = Iterators.cycle(movements);
  }

  public Movements(Scanner scanner) {
    this(loadFromScanner(scanner));
  }

  public String getNextMovement() {
    return iterator.next();
  }

  private static ImmutableList<String> loadFromScanner(Scanner scanner) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    while (scanner.hasNextLine()) {
      String lineValue = scanner.nextLine();
      // skip empty lines and allow for comments
      if (lineValue.isEmpty() || lineValue.startsWith("#") || lineValue.startsWith("//")) {
        continue;
      }
      builder.add(lineValue);
    }
    return builder.build();
  }
}
