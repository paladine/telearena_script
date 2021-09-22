package com.jeffreys.common.proto;

import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Contains helper methods for protobufs. */
public class Protos {

  private static <T extends Message> Message.Builder getBuilder(Class<T> clazz)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    Method m = clazz.getMethod("newBuilder");
    return (Message.Builder) m.invoke(null);
  }

  /** Returns a proto object parsed from {@code file} as text. */
  @SuppressWarnings("unchecked")
  public static <T extends Message> T parseProtoFromText(String proto, Class<T> clazz) {
    try {
      Message.Builder builder = getBuilder(clazz);
      TextFormat.merge(proto, builder);
      return (T) builder.build();
    } catch (Exception ex) {
      throw new IllegalArgumentException(ex);
    }
  }

  /** Returns a proto object parsed from {@code file} as text. */
  @SuppressWarnings("unchecked")
  public static <T extends Message> T parseProtoFromTextFile(String file, Class<T> clazz) {
    try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file))) {
      Message.Builder builder = getBuilder(clazz);
      TextFormat.merge(reader, builder);
      return (T) builder.build();
    } catch (Exception ex) {
      throw new IllegalArgumentException(ex);
    }
  }

  /** Returns a proto object parsed from {@code file} as text. */
  public static <T extends Message> T parseProtoFromTextFile(File file, Class<T> clazz) {
    return parseProtoFromTextFile(file.getPath(), clazz);
  }
}
