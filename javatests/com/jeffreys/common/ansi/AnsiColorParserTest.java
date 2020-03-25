package com.jeffreys.common.ansi;

import static com.google.common.truth.Truth.assertThat;
import static com.jeffreys.junit.Exceptions.assertThrows;

import com.google.common.collect.ImmutableList;
import com.jeffreys.common.ansi.AnsiColorParser.AnsiColor;
import com.jeffreys.common.ansi.AnsiColorParser.ParsedAnsiText;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AnsiColorParserTest {

  private final AnsiColorParser ansiColorParser = new AnsiColorParser();

  private static String createBoldColor(AnsiColor foreground, AnsiColor background) {
    return String.format("\u001B[1;%d;%dm", 40 + background.ordinal(), 30 + foreground.ordinal());
  }

  @Test
  public void simpleParse_noAnsi() {
    String str = "This is really cool.\r\nYep";

    AnsiColorParser.ParsedAnsiText parsedAnsi = ansiColorParser.parseAnsi(str);

    assertThat(parsedAnsi.getText()).isEqualTo(str);
    assertThat(parsedAnsi.getAttributes()).hasSize(str.length());
    assertThat(parsedAnsi.getAttributes().get(0))
        .isEqualTo(parsedAnsi.getFirstAttributeOrDefault());
  }

  @Test
  public void simpleParse_empty() {
    AnsiColorParser.ParsedAnsiText parsedAnsi = ansiColorParser.parseAnsi("");
    assertThat(parsedAnsi.getText()).isEmpty();
    assertThat(parsedAnsi.getAttributes()).isEmpty();
    assertThat(parsedAnsi.getFirstAttributeOrDefault()).isNotNull();
  }

  @Test
  public void simpleParse_withColors() {
    for (AnsiColor color : AnsiColor.values()) {
      String str = createBoldColor(color, color) + "Yo";

      AnsiColorParser.ParsedAnsiText parsedAnsi = ansiColorParser.parseAnsi(str);

      assertThat(parsedAnsi.getText()).isEqualTo("Yo");
      assertThat(parsedAnsi.getAttributes()).hasSize(2);
      assertThat(parsedAnsi.getAttributes().get(0))
          .isEqualTo(
              AnsiColorParser.AnsiCharacterAttribute.create(
                  'Y', color, color, AnsiColorParser.AnsiAttribute.BOLD));
      assertThat(parsedAnsi.getAttributes().get(1))
          .isEqualTo(
              AnsiColorParser.AnsiCharacterAttribute.create(
                  'o', color, color, AnsiColorParser.AnsiAttribute.BOLD));
    }
  }

  @Test
  public void simpleParse_allAnsi_noCharacters() {
    String str = "\u001B[30;31;32;40;4;41;45m";

    AnsiColorParser.ParsedAnsiText parsedAnsi = ansiColorParser.parseAnsi(str);

    assertThat(parsedAnsi.getText()).isEmpty();
    assertThat(parsedAnsi.getAttributes()).isEmpty();
    assertThat(parsedAnsi.getFirstAttributeOrDefault()).isNotNull();
  }

  @Test
  public void verify_nonSetGraphicsMode() {
    String str = "\u001B[K\u001B[2J\u001B[4;5H\u001B[=55h\u001B[=56l";

    AnsiColorParser.ParsedAnsiText parsedAnsi = ansiColorParser.parseAnsi(str);

    assertThat(parsedAnsi.getText()).isEmpty();
    assertThat(parsedAnsi.getAttributes()).isEmpty();
  }

  @Test
  public void parsedAnsiText_create_throwsWithUnequalArguments() {
    assertThrows(
        IllegalArgumentException.class, () -> ParsedAnsiText.create("Test", ImmutableList.of()));
  }
}
