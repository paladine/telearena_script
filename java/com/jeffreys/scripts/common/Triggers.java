package com.jeffreys.scripts.common;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.jeffreys.common.ansi.AnsiColorParser.AnsiColor;
import com.jeffreys.common.ansi.AnsiColorParser.ParsedAnsiText;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Triggers {

  @AutoValue
  abstract static class CompiledTrigger {
    abstract int getId();

    abstract Color getExpectedColor();

    abstract Pattern getPattern();

    abstract String getCommand();

    static CompiledTrigger create(Trigger trigger) {
      return new AutoValue_Triggers_CompiledTrigger(
          trigger.getId(),
          trigger.getExpectedColor(),
          Pattern.compile(trigger.getTriggerRegex()),
          trigger.getCommand());
    }
  }

  private final ImmutableList<CompiledTrigger> compiledTriggers;

  private Triggers(Iterable<Trigger> triggers) {
    compiledTriggers =
        Streams.stream(triggers).map(CompiledTrigger::create).collect(toImmutableList());
  }

  public static Triggers of(Iterable<Trigger> triggers) {
    return new Triggers(triggers);
  }

  /**
   * Processes {@code line} for any triggers, calling {@code} command if a trigger is found, with
   * the ID of the trigger and command text.
   *
   * @return {@code true} if the line had a triggered processed
   */
  public boolean processLine(ParsedAnsiText line, BiConsumer<Integer, String> command) {
    if (line.getText().isEmpty()) {
      return false;
    }

    boolean foundMatch = false;
    for (CompiledTrigger trigger : compiledTriggers) {
      if (!trigger.getExpectedColor().equals(Color.ANY)
          && !trigger
              .getExpectedColor()
              .equals(toProtoColor(line.getFirstAttributeOrDefault().getForegroundColor()))) {
        continue;
      }

      Matcher matcher = trigger.getPattern().matcher(line.getText());
      if (matcher.matches()) {
        foundMatch = true;
        command.accept(trigger.getId(), matcher.replaceAll(trigger.getCommand()));
      }
    }

    return foundMatch;
  }

  private static Color toProtoColor(AnsiColor color) {
    return Color.valueOf(color.name());
  }
}
