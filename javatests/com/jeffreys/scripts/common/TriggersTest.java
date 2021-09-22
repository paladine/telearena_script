package com.jeffreys.scripts.common;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.collect.ImmutableList;
import com.jeffreys.common.ansi.AnsiColorParser.AnsiAttribute;
import com.jeffreys.common.ansi.AnsiColorParser.AnsiCharacterAttribute;
import com.jeffreys.common.ansi.AnsiColorParser.AnsiColor;
import com.jeffreys.common.ansi.AnsiColorParser.ParsedAnsiText;
import java.util.function.BiConsumer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class TriggersTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private BiConsumer<Integer, String> command;

  @Test
  public void verify_emptyTrigger_doesNothing() {
    Triggers triggers = Triggers.of(ImmutableList.of());

    assertThat(triggers.processLine(ParsedAnsiText.create(""), command)).isFalse();

    verifyNoInteractions(command);
  }

  @Test
  public void emptyTrigger_doesntBomb() {
    Triggers.of(ImmutableList.of(Trigger.getDefaultInstance()));
  }

  @Test
  public void simple_no_match() {
    Triggers triggers =
        Triggers.of(
            ImmutableList.of(
                Trigger.newBuilder()
                    .setId(1)
                    .setTriggerRegex("haha")
                    .setCommand("you sent haha")
                    .build()));

    assertThat(triggers.processLine(ParsedAnsiText.create("not match"), command)).isFalse();

    verifyNoInteractions(command);
  }

  @Test
  public void simple_wrong_color_match() {
    Triggers triggers =
        Triggers.of(
            ImmutableList.of(
                Trigger.newBuilder()
                    .setId(1)
                    .setTriggerRegex(".*haha.*")
                    .setCommand("you sent haha")
                    .setExpectedColor(Color.RED)
                    .build()));

    assertThat(triggers.processLine(ParsedAnsiText.create("this is a testhahayep"), command))
        .isFalse();

    verifyNoInteractions(command);
  }

  @Test
  public void simple_color_match() {
    Triggers triggers =
        Triggers.of(
            ImmutableList.of(
                Trigger.newBuilder()
                    .setId(1)
                    .setTriggerRegex(".*haha.*")
                    .setCommand("you sent haha")
                    .setExpectedColor(Color.RED)
                    .build()));

    assertThat(
            triggers.processLine(
                ParsedAnsiText.create(
                    "this is a testhahayep",
                    AnsiCharacterAttribute.create(
                        '\0', AnsiColor.RED, AnsiColor.BLACK, AnsiAttribute.NONE)),
                command))
        .isTrue();

    verify(command).accept(1, "you sent haha");
  }

  @Test
  public void simple_color_match_regex_expansion() {
    Triggers triggers =
        Triggers.of(
            ImmutableList.of(
                Trigger.newBuilder()
                    .setId(1)
                    .setTriggerRegex(".*Your Experience is (\\d+)\\..*")
                    .setCommand("Experience is $1 dude.\r\n")
                    .setExpectedColor(Color.RED)
                    .build()));

    assertThat(
            triggers.processLine(
                ParsedAnsiText.create(
                    "Whatever, Your Experience is 12828. Dude.",
                    AnsiCharacterAttribute.create(
                        '\0', AnsiColor.RED, AnsiColor.BLACK, AnsiAttribute.NONE)),
                command))
        .isTrue();

    verify(command).accept(1, "Experience is 12828 dude.\r\n");
  }
}
