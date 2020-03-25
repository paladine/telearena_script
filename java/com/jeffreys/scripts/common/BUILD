package(default_visibility = ["//visibility:public"])

proto_library(
    name = "common_proto",
    srcs = ["common.proto"],
)

java_proto_library(
    name = "java_common_proto",
    deps = [":common_proto"],
)

java_library(
    name = "common",
    srcs = glob(["*.java"]),
    deps = [
      ":java_common_proto",
      "//java/com/jeffreys/common/ansi",
      "//third_party/com/google/auto/value",
      "@maven//:com_google_guava_guava",
    ],
)

