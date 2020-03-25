package(default_visibility = ["//javatests/com/jeffreys/scripts/tascript:__subpackages__"])

proto_library(
    name = "tascript_proto",
    srcs = ["tascript.proto"],
    deps = ["//java/com/jeffreys/scripts/common:common_proto"],
)

java_proto_library(
    name = "java_tascript_proto",
    deps = [":tascript_proto"],
)

BINARY_SRC = ["Main.java", "Options.java"]

java_library(
    name = "lib",
    srcs = glob(["*.java"], exclude = BINARY_SRC),
    deps = [
      ":java_tascript_proto",
      "//java/com/jeffreys/common/ansi",
      "//java/com/jeffreys/common/proto",
      "//java/com/jeffreys/common/queue",
      "//java/com/jeffreys/scripts/common",
      "//third_party/com/google/auto/value",
      "@maven//:com_google_code_findbugs_jsr305",
      "@maven//:com_google_guava_guava",
      "@maven//:javax_inject_javax_inject",
      "@maven//:com_google_inject_guice",
      "@maven//:com_google_flogger_flogger",
      "@maven//:com_google_flogger_flogger_system_backend",
      "@maven//:com_google_protobuf_protobuf_java",
    ],
)

java_binary(
    name = "TAScript",
    srcs = BINARY_SRC,
    main_class = "com.jeffreys.scripts.tascript.Main",
    deps = [
      ":java_tascript_proto",
      ":lib",
      #"@com_google_flogger//jar",
      "//java/com/jeffreys/common/proto",
      "//java/com/jeffreys/common/queue",
      "//java/com/jeffreys/scripts/common",
      "//third_party/com/google/auto/value",
      "@maven//:com_google_guava_guava",
      "@maven//:com_google_inject_guice",
      "@org_kohsuke_arg4j//:args4j",
    ],
)
