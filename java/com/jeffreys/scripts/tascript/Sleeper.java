package com.jeffreys.scripts.tascript;

import java.time.Duration;

@FunctionalInterface
public interface Sleeper {
  void sleep(Duration duration);
}
