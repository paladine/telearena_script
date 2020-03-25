load("//rules:gen_java_tests.bzl", "gen_java_tests")

java_library(
  name = "tests",
  srcs = glob(["*.java"]),
  deps = [
    "@maven//:com_google_truth_truth",
    "@maven//:com_google_truth_extensions_truth_java8_extension",
    "@maven//:junit_junit",
    "//java/com/jeffreys/common/ansi",
    "//java/com/jeffreys/common/queue",
    "//java/com/jeffreys/junit",
    "//java/com/jeffreys/scripts/common:java_common_proto",
    "//java/com/jeffreys/scripts/common",
    "//java/com/jeffreys/scripts/tascript:lib",
    "@maven//:com_google_guava_guava",
    "@maven//:com_google_inject_guice",
    "@maven//:com_google_inject_extensions_guice_testlib",
    "//java/com/jeffreys/scripts/tascript:java_tascript_proto",
    "@maven//:javax_inject_javax_inject",
    "@maven//:org_mockito_mockito_core",
  ],
)

gen_java_tests(
  srcs = glob(["*.java"]),
  runtime_deps = [":tests"],
  size = "small",
)
