package com.jeffreys.scripts.tascript;

import static com.google.common.base.Preconditions.checkArgument;

public class Stats {
  private long totalDamage = 0;
  private int maxDamage = 0;
  private int minDamage = Integer.MAX_VALUE;
  private long count = 0;

  public Stats() {}

  public long getTotalDamage() {
    return totalDamage;
  }

  public long getMaximumDamage() {
    return maxDamage;
  }

  public long getMinimumDamage() {
    return minDamage;
  }

  public long getCount() {
    return count;
  }

  public void addDamage(int damage) {
    checkArgument(damage > 0);

    ++count;
    totalDamage += damage;

    if (damage > maxDamage) {
      maxDamage = damage;
    }

    if (damage < minDamage) {
      minDamage = damage;
    }
  }
}
