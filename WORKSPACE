load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository", "new_git_repository")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

new_git_repository(
    name = "org_kohsuke_arg4j",
    remote = "https://github.com/kohsuke/args4j.git",
    tag = "args4j-site-2.33",
    build_file_content = """
java_library(
  name = "args4j",
  visibility = ["//visibility:public"],
  srcs = glob(["args4j/src/org/kohsuke/args4j/**/*.java"]),
  resources = glob(["args4j/src/org/kohsuke/args4j/*.properties"]),
  deps = [],
)""",
)

# rules_java defines rules for generating Java code from Protocol Buffers.
http_archive(
    name = "rules_java",
    sha256 = "ccf00372878d141f7d5568cedc4c42ad4811ba367ea3e26bc7c43445bbc52895",
    strip_prefix = "rules_java-d7bf804c8731edd232cb061cb2a9fe003a85d8ee",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_java/archive/d7bf804c8731edd232cb061cb2a9fe003a85d8ee.tar.gz",
        "https://github.com/bazelbuild/rules_java/archive/d7bf804c8731edd232cb061cb2a9fe003a85d8ee.tar.gz",
    ],
)

# rules_proto defines abstract rules for building Protocol Buffers.
http_archive(
    name = "rules_proto",
    sha256 = "57001a3b33ec690a175cdf0698243431ef27233017b9bed23f96d44b9c98242f",
    strip_prefix = "rules_proto-9cd4f8f1ede19d81c6d48910429fe96776e567b1",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_proto/archive/9cd4f8f1ede19d81c6d48910429fe96776e567b1.tar.gz",
        "https://github.com/bazelbuild/rules_proto/archive/9cd4f8f1ede19d81c6d48910429fe96776e567b1.tar.gz",
    ],
)

load("@rules_java//java:repositories.bzl", "rules_java_dependencies", "rules_java_toolchains")
rules_java_dependencies()
rules_java_toolchains()

load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies", "rules_proto_toolchains")
rules_proto_dependencies()
rules_proto_toolchains()

git_repository(
    name = "rules_jvm_external",
    commit = "d442b54",
    remote = "https://github.com/bazelbuild/rules_jvm_external",
)

load("@rules_jvm_external//:defs.bzl", "maven_install")

maven_install(
    artifacts = [
        "com.google.inject:guice:4.2.2",
        "com.google.inject.extensions:guice-testlib:4.2.2",

        "com.google.flogger:flogger:0.4",
        "com.google.flogger:flogger-system-backend:0.4",

        "junit:junit:4.13",

        "org.mockito:mockito-core:3.3.3",

        "com.google.auto.value:auto-value:1.7",
        "com.google.auto.value:auto-value-annotations:1.7",

        "com.google.guava:guava:28.2-jre",
        "com.google.guava:guava:28.2-android",

        "com.google.truth:truth:1.0.1",
        "com.google.truth.extensions:truth-java8-extension:1.0.1",

        "javax.inject:javax.inject:1",

        "com.google.code.findbugs:jsr305:3.0.2",

        "com.google.protobuf:protobuf-java:3.11.3",
        "com.google.protobuf:protobuf-java-util:3.11.3",
    ],
    repositories = [
        "https://jcenter.bintray.com",
        "https://maven.google.com",
        "https://repo1.maven.org/maven2",
    ],
)

