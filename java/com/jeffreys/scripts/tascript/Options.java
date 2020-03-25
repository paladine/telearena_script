package com.jeffreys.scripts.tascript;

import com.google.auto.value.AutoValue;
import com.google.inject.AbstractModule;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

@AutoValue
abstract class Options {
  private static class Flags {
    @Option(name = "--config", usage = "Configuration textproto", required = true)
    public String configFile;
  }

  private static Flags parse(String[] args) {
    try {
      Flags flags = new Flags();
      CmdLineParser parser = new CmdLineParser(flags);
      parser.parseArgument(args);
      return flags;
    } catch (CmdLineException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static AbstractModule getModule(String[] args) {
    Flags flags = parse(args);
    Options options = new AutoValue_Options(flags.configFile);

    return new AbstractModule() {
      @Override
      public void configure() {
        bind(Options.class).toInstance(options);
      }
    };
  }

  abstract String getConfigFile();
}
