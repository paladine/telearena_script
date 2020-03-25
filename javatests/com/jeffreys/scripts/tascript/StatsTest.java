package com.jeffreys.scripts.tascript;

import static com.google.common.truth.Truth.assertThat;
import static com.jeffreys.junit.Exceptions.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StatsTest {
  private final Stats stats = new Stats();

  @Test
  public void cant_add_negative() {
    assertThrows(IllegalArgumentException.class, () -> stats.addDamage(-1));
  }

  @Test
  public void cant_add_zero() {
    assertThrows(IllegalArgumentException.class, () -> stats.addDamage(0));
  }

  @Test
  public void basicAdd_increments() {
    for (int i = 1; i <= 10; ++i) {
      stats.addDamage(i);
    }

    assertThat(stats.getCount()).isEqualTo(10);
    assertThat(stats.getMinimumDamage()).isEqualTo(1);
    assertThat(stats.getMaximumDamage()).isEqualTo(10);
    assertThat(stats.getTotalDamage()).isEqualTo(55);
  }

  @Test
  public void basicAdd_decrements() {
    for (int i = 10; i >= 1; --i) {
      stats.addDamage(i);
    }

    assertThat(stats.getCount()).isEqualTo(10);
    assertThat(stats.getMinimumDamage()).isEqualTo(1);
    assertThat(stats.getMaximumDamage()).isEqualTo(10);
    assertThat(stats.getTotalDamage()).isEqualTo(55);
  }
}
