package com.jeffreys.scripts.tascript;

import static com.google.common.truth.Truth.assertThat;
import static com.jeffreys.junit.Exceptions.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Scanner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MovementsTest {

  private static final int REPEAT_LOOPS = 8;

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void basicMovements() {
    Movements movements = new Movements(ImmutableList.of("one", "two", "three"));
    for (int i = 0; i < REPEAT_LOOPS; ++i) {
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
  public void basicScannerMovements() throws IOException {
    Movements movements = new Movements(new Scanner("one\ntwo\nthree"));
    for (int i = 0; i < REPEAT_LOOPS; ++i) {
      assertThat(movements.getNextMovement()).isEqualTo("one");
      assertThat(movements.getNextMovement()).isEqualTo("two");
      assertThat(movements.getNextMovement()).isEqualTo("three");
    }
  }

  @Test
  public void scannerComments() throws IOException {
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
    for (int i = 0; i < REPEAT_LOOPS; ++i) {
      assertThat(movements.getNextMovement()).isEqualTo("one");
      assertThat(movements.getNextMovement()).isEqualTo("two");
      assertThat(movements.getNextMovement()).isEqualTo("three");
    }
  }

  @Test
  public void emptyMovements_withScanner() {
    assertThrows(NoSuchElementException.class, () -> new Movements(new Scanner("")));
  }

  @Test
  public void verify_integer_prefixes() throws IOException {
    Movements movements =
        new Movements(new Scanner("0one\n1two\n2three\ntesting\n12fun\n3almost\n-12end"));
    for (int i = 0; i < REPEAT_LOOPS; ++i) {
      assertThat(movements.getNextMovement()).isEqualTo("one");
      assertThat(movements.getNextMovement()).isEqualTo("two");
      assertThat(movements.getNextMovement()).isEqualTo("three");
      assertThat(movements.getNextMovement()).isEqualTo("three");
      assertThat(movements.getNextMovement()).isEqualTo("testing");
      assertThat(movements.getNextMovement()).isEqualTo("fun");
      assertThat(movements.getNextMovement()).isEqualTo("fun");
      assertThat(movements.getNextMovement()).isEqualTo("fun");
      assertThat(movements.getNextMovement()).isEqualTo("fun");
      assertThat(movements.getNextMovement()).isEqualTo("fun");
      assertThat(movements.getNextMovement()).isEqualTo("fun");
      assertThat(movements.getNextMovement()).isEqualTo("fun");
      assertThat(movements.getNextMovement()).isEqualTo("fun");
      assertThat(movements.getNextMovement()).isEqualTo("fun");
      assertThat(movements.getNextMovement()).isEqualTo("fun");
      assertThat(movements.getNextMovement()).isEqualTo("fun");
      assertThat(movements.getNextMovement()).isEqualTo("fun");
      assertThat(movements.getNextMovement()).isEqualTo("almost");
      assertThat(movements.getNextMovement()).isEqualTo("almost");
      assertThat(movements.getNextMovement()).isEqualTo("almost");
      assertThat(movements.getNextMovement()).isEqualTo("-12end");
    }
  }

  @Test
  public void scannerLoad_relativePath_throws() {
    assertThrows(
        IllegalArgumentException.class, () -> new Movements(new Scanner("!include test.txt")));
  }

  @Test
  public void scannerLoad_absolutePath_notFound_throws() {
    assertThrows(
        FileNotFoundException.class, () -> new Movements(new Scanner("!include /test.txt")));
  }

  @Test
  public void scannerLoad_basicFile() throws IOException {
    File base = tempFolder.newFile("base.txt");
    Files.write("test\none".getBytes(), base);

    Movements movements = new Movements(base);
    for (int i = 0; i < REPEAT_LOOPS; ++i) {
      assertThat(movements.getNextMovement()).isEqualTo("test");
      assertThat(movements.getNextMovement()).isEqualTo("one");
    }
  }

  @Test
  public void scannerLoad_includeLoop_relative() throws IOException {
    File base = tempFolder.newFile("base.txt");
    Files.write("!include base.txt".getBytes(), base);

    assertThrows(IllegalArgumentException.class, () -> new Movements(base));
  }

  @Test
  public void scannerLoad_includeLoop_absolute() throws IOException {
    File base = tempFolder.newFile("base.txt");
    Files.write(String.format("!include %s", base.getAbsolutePath()).getBytes(), base);

    assertThrows(IllegalArgumentException.class, () -> new Movements(base));
  }

  @Test
  public void scannerLoad_basicFile_multiIncludes() throws IOException {
    File base = tempFolder.newFile("base.txt");
    File include = tempFolder.newFile("include.txt");
    File third = tempFolder.newFile("third.txt");

    Files.write("base_one\n2base_two\n!include include.txt\n\n".getBytes(), base);
    Files.write(
        "!include third.txt\ninclude_one\ninclude_two\n!include third.txt\n".getBytes(), include);
    Files.write("third\ncustom_command\n".getBytes(), third);

    Movements movements = new Movements(base);
    for (int i = 0; i < REPEAT_LOOPS; ++i) {
      assertThat(movements.getNextMovement()).isEqualTo("base_one");
      assertThat(movements.getNextMovement()).isEqualTo("base_two");
      assertThat(movements.getNextMovement()).isEqualTo("base_two");

      assertThat(movements.getNextMovement()).isEqualTo("third");
      assertThat(movements.getNextMovement()).isEqualTo("custom_command");

      assertThat(movements.getNextMovement()).isEqualTo("include_one");
      assertThat(movements.getNextMovement()).isEqualTo("include_two");

      assertThat(movements.getNextMovement()).isEqualTo("third");
      assertThat(movements.getNextMovement()).isEqualTo("custom_command");
    }
  }
}
