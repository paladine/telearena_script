package com.jeffreys.common.ansi;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class AnsiColorParser {
  public enum AnsiColor {
    BLACK,
    RED,
    GREEN,
    YELLOW,
    BLUE,
    MAGENTA,
    CYAN,
    WHITE
  }

  public enum AnsiAttribute {
    NONE,
    BOLD,
    FAINT,
    ITALIC,
    UNDERLINE,
    SLOW_BLINK,
    RAPID_BLINK,
    REVERSE_VIDEO,
    CONCEAL,
    CROSSED_OUT,
  }

  private enum AnsiState {
    NORMAL,
    ESCAPE,
    BRACKET,
    VALUE_ACCUM,
    WAIT_FOR_ANSI_END
  }

  private static final char ASCII_ESCAPE = 0x1B;
  private static final ImmutableSet<Character> ANSI_ENDS =
      ImmutableSet.<Character>builder()
          .add('H')
          .add('h')
          .add('f')
          .add('A')
          .add('B')
          .add('C')
          .add('D')
          .add('s')
          .add('u')
          .add('J')
          .add('K')
          .add('m')
          .add('l')
          .add('p')
          .build();

  @AutoValue
  public abstract static class AnsiCharacterAttribute {
    public abstract char getCharacter();

    public abstract AnsiColor getForegroundColor();

    public abstract AnsiColor getBackgroundColor();

    public abstract AnsiAttribute getAttribute();

    public static AnsiCharacterAttribute create(
        char c, AnsiColor foregroundColor, AnsiColor backgroundColor, AnsiAttribute attribute) {
      return new AutoValue_AnsiColorParser_AnsiCharacterAttribute(
          c, foregroundColor, backgroundColor, attribute);
    }

    public static AnsiCharacterAttribute getDefault() {
      return DEFAULT_ANSI_CHARACTER_ATTRIBUTE;
    }
  }

  private static final AnsiCharacterAttribute DEFAULT_ANSI_CHARACTER_ATTRIBUTE =
      AnsiCharacterAttribute.create('\0', AnsiColor.WHITE, AnsiColor.BLACK, AnsiAttribute.NONE);

  @AutoValue
  public abstract static class ParsedAnsiText {
    public abstract String getText();

    public abstract ImmutableList<AnsiCharacterAttribute> getAttributes();

    public static ParsedAnsiText create(String text, List<AnsiCharacterAttribute> attributes) {
      checkArgument(text.length() == attributes.size());
      return new AutoValue_AnsiColorParser_ParsedAnsiText(text, ImmutableList.copyOf(attributes));
    }

    public AnsiCharacterAttribute getFirstAttributeOrDefault() {
      if (getAttributes().isEmpty()) {
        return DEFAULT_ANSI_CHARACTER_ATTRIBUTE;
      }

      return getAttributes().get(0);
    }

    public static ParsedAnsiText create(String text, AnsiCharacterAttribute attribute) {
      ImmutableList.Builder<AnsiCharacterAttribute> builder =
          ImmutableList.builderWithExpectedSize(text.length());
      for (char c : text.toCharArray()) {
        builder.add(
            AnsiCharacterAttribute.create(
                c,
                attribute.getForegroundColor(),
                attribute.getBackgroundColor(),
                attribute.getAttribute()));
      }
      return new AutoValue_AnsiColorParser_ParsedAnsiText(text, builder.build());
    }

    public static ParsedAnsiText create(String text) {
      return create(text, DEFAULT_ANSI_CHARACTER_ATTRIBUTE);
    }
  }

  private final ArrayList<Integer> values = new ArrayList<>();

  private AnsiColor backgroundColor = AnsiColor.BLACK;
  private AnsiColor foregroundColor = AnsiColor.WHITE;
  private AnsiAttribute attribute = AnsiAttribute.NONE;
  private int accumulator = 0;
  private AnsiState state = AnsiState.NORMAL;

  public AnsiColorParser() {}

  public ParsedAnsiText parseAnsi(String text) {
    ImmutableList.Builder<AnsiCharacterAttribute> attributeBuilder =
        ImmutableList.builderWithExpectedSize(text.length());
    StringBuilder stringBuilder = new StringBuilder(text.length());

    for (int i = 0; i < text.length(); ++i) {
      @Nullable AnsiCharacterAttribute ansiCharacterAttribute = parseAnsiCharacter(text.charAt(i));
      if (ansiCharacterAttribute != null) {
        stringBuilder.append(ansiCharacterAttribute.getCharacter());
        attributeBuilder.add(ansiCharacterAttribute);
      }
    }
    return ParsedAnsiText.create(stringBuilder.toString(), attributeBuilder.build());
  }

  @Nullable
  private AnsiCharacterAttribute parseAnsiCharacter(char c) {
    switch (state) {
      case NORMAL:
        if (c == ASCII_ESCAPE) {
          state = AnsiState.ESCAPE;
        } else {
          return AnsiCharacterAttribute.create(c, foregroundColor, backgroundColor, attribute);
        }
        break;
      case ESCAPE:
        if (c == '[') {
          state = AnsiState.BRACKET;
        } else {
          // just consume the prior escape
          state = AnsiState.NORMAL;
          return AnsiCharacterAttribute.create(c, foregroundColor, backgroundColor, attribute);
        }
        break;
      case BRACKET:
        accumulator = 0;
        values.clear();
        if (Character.isDigit(c)) {
          state = AnsiState.VALUE_ACCUM;
          accumulator = c - '0';
        } else {
          // something else? how about waiting until an ending frame
          state = AnsiState.WAIT_FOR_ANSI_END;
        }
        break;
      case VALUE_ACCUM:
        if (c == ';') {
          values.add(accumulator);
          accumulator = 0;
        } else if (Character.isDigit(c)) {
          accumulator = 10 * accumulator + (c - '0');
        } else if (isAnsiSequenceFinished(c)) {
          // push back last digit
          if (accumulator > 0) {
            values.add(accumulator);
          }
          // check if setting graphics mode
          if (c == 'm') {
            for (int val : values) {
              if (val >= 30 && val <= 37) {
                foregroundColor = AnsiColor.values()[val - 30];
              } else if (val >= 40 && val <= 47) {
                backgroundColor = AnsiColor.values()[val - 40];
              } else if (val <= 8) {
                attribute = AnsiAttribute.values()[val];
              }
            }
          }
          values.clear();
          state = AnsiState.NORMAL;
        }
        break;
      case WAIT_FOR_ANSI_END:
        if (c == ASCII_ESCAPE) {
          state = AnsiState.ESCAPE;
        } else if (isAnsiSequenceFinished(c)) {
          state = AnsiState.NORMAL;
        }
        break;
    }
    return null;
  }

  private static boolean isAnsiSequenceFinished(char c) {
    return ANSI_ENDS.contains(c);
  }
}
