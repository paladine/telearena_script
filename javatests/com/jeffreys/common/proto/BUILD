java_test(
  name = "ProtosTest",
  srcs = ["ProtosTest.java"],
  size = "small",
  deps = [
    ":java_proto",
    "@maven//:com_google_guava_guava",
    "@maven//:com_google_truth_truth",
    "@maven//:junit_junit",
    "//java/com/jeffreys/junit",
    "//java/com/jeffreys/common/proto",
  ],
)

proto_library(
    name = "proto",
    srcs = ["test.proto"],
)

java_proto_library(
    name = "java_proto",
    deps = [":proto"],
)
