package com.jeffreys.common.proto;

import static com.google.common.truth.Truth.assertThat;
import static com.jeffreys.junit.Exceptions.assertThrows;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProtosTest {

  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void parseEmptyText_defaultInstance() {
    assertThat(Protos.parseProtoFromText("", TestProto.class))
        .isEqualTo(TestProto.getDefaultInstance());
  }

  @Test
  public void parseText() {
    assertThrows(
        IllegalArgumentException.class, () -> Protos.parseProtoFromText("blah", TestProto.class));
  }

  @Test
  public void parsesText() {
    assertThat(Protos.parseProtoFromText("value: \"haha\"\r\nnumber: 68", TestProto.class))
        .isEqualTo(TestProto.newBuilder().setValue("haha").setNumber(68).build());
  }

  @Test
  public void parsesFromFile_string() throws IOException {
    File tempFile = folder.newFile("tempFile.proto");

    Files.asCharSink(tempFile, StandardCharsets.UTF_8).write("value: \"haha\"\r\nnumber: 68");

    assertThat(Protos.parseProtoFromTextFile(tempFile.getPath(), TestProto.class))
        .isEqualTo(TestProto.newBuilder().setValue("haha").setNumber(68).build());
  }

  @Test
  public void parsesFromFile_file() throws IOException {
    File tempFile = folder.newFile("tempFile.proto");

    Files.asCharSink(tempFile, StandardCharsets.UTF_8).write("value: \"haha\"\r\nnumber: 68");

    assertThat(Protos.parseProtoFromTextFile(tempFile, TestProto.class))
        .isEqualTo(TestProto.newBuilder().setValue("haha").setNumber(68).build());
  }
}
