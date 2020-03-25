package com.jeffreys.scripts.tascript;

import static com.google.common.truth.Truth.assertThat;
import static com.jeffreys.junit.Exceptions.assertThrows;

import com.google.common.collect.ImmutableList;
import java.util.NoSuchElementException;
import java.util.Scanner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MovementsTest {

  @Test
  public void basicMovements() {
    Movements movements = new Movements(ImmutableList.of("one", "two", "three"));
    for (int i = 0; i < 10; ++i) {
      assertThat(movements.getNextMovement()).isEqualTo("one");
      assertThat(movements.getNextMovement()).isEqualTo("two");
      assertThat(movements.getNextMovement()).isEqualTo("three");
    }
  }

  @Test
  public void emptyMovements_withList() {
    assertThrows(NoSuchElementException.class, () -> new Movements(ImmutableList.of()));
  }

  @Test
  public void basicScannerMovements() {
    Movements movements = new Movements(new Scanner("one\ntwo\nthree"));
    for (int i = 0; i < 10; ++i) {
      assertThat(movements.getNextMovement()).isEqualTo("one");
      assertThat(movements.getNextMovement()).isEqualTo("two");
      assertThat(movements.getNextMovement()).isEqualTo("three");
    }
  }

  @Test
  public void scannerComments() {
    Movements movements =
        new Movements(
            new Scanner(
                "# this is a comment\n"
                    + "one\n"
                    + "\n" // blank line
                    + "// and another comment\n"
                    + "\n" // and another
                    + "two\n"
                    + "three\n"));
    for (int i = 0; i < 10; ++i) {
      assertThat(movements.getNextMovement()).isEqualTo("one");
      assertThat(movements.getNextMovement()).isEqualTo("two");
      assertThat(movements.getNextMovement()).isEqualTo("three");
    }
  }

  @Test
  public void emptyMovements_withScanner() {
    assertThrows(NoSuchElementException.class, () -> new Movements(new Scanner("")));
  }
}
