package com.jeffreys.scripts.tascript;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.jeffreys.junit.Exceptions.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.jeffreys.common.ansi.AnsiColorParser.ParsedAnsiText;
import com.jeffreys.common.queue.NonBlockingSupplier;
import com.jeffreys.scripts.common.Color;
import com.jeffreys.scripts.common.Trigger;
import com.jeffreys.scripts.common.Triggers;
import com.jeffreys.scripts.tascript.Annotations.LogfilePrintWriter;
import com.jeffreys.scripts.tascript.Annotations.OutputPrintWriter;
import com.jeffreys.scripts.tascript.TAScript.AttackException;
import com.jeffreys.scripts.tascript.TAScript.AttackType;
import com.jeffreys.scripts.tascript.TAScript.DeadException;
import com.jeffreys.scripts.tascript.TAScript.GroupMemberStatus;
import com.jeffreys.scripts.tascript.TAScript.LogoffException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.Scanner;
import javax.inject.Inject;
import javax.inject.Provider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

@RunWith(JUnit4.class)
public class TAScriptTest {
  private static final String USERNAME = "Super Conductor";

  private static final String RED = "\u001B[1;31m";
  private static final String GREEN = "\u001B[1;32m";
  private static final String YELLOW = "\u001B[1;33m";
  private static final String BLUE = "\u001B[1;34m";
  private static final String MAGENTA = "\u001B[1;35m";
  private static final String CYAN = "\u001B[1;36m";
  private static final String WHITE = "\u001B[1;37m";

  private final ByteArrayOutputStream output = new ByteArrayOutputStream();
  private final ByteArrayOutputStream logOutput = new ByteArrayOutputStream();

  @Bind(lazy = true)
  private NonBlockingSupplier<String> lineSupplier;

  @Bind private final Movements movements = new Movements(ImmutableList.of("\r\n"));

  @Bind(lazy = true)
  private Configuration configuration =
      Configuration.newBuilder().setUsername(USERNAME).setNumberOfPhysicalAttacks(1).build();

  @Bind private final Clock clock = Clock.systemUTC();

  @Bind @OutputPrintWriter
  private final PrintWriter printWriterOutput = new PrintWriter(output, /* autoFlush= */ true);

  @Bind @LogfilePrintWriter
  private final PrintWriter logfileWriterOutput = new PrintWriter(logOutput, /* autoFlush= */ true);

  @Bind @Mock private final Sleeper sleeper = mock(Sleeper.class);

  @Bind(lazy = true)
  private Triggers triggers = Triggers.of(ImmutableList.of());

  @Inject Provider<TAScript> tascript;

  @Before
  public void injectMembers() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void parseGroupMemberStatus_allReady_nameMissing() {
    String text =
        "Your group currently consists of:\r\n"
            + "  t                              (L) [HE:100% ST:Ready]\r\n"
            + "\r\n";

    assertThat(getScript(text).getGroupStatus())
        .isEqualTo(
            GroupMemberStatus.create(
                ImmutableSet.of("t"),
                /* youAreReady= */ true,
                /* allAreReady= */ true,
                /* lowestHealth= */ 1.0,
                /* lowestHealthMember= */ "t"));
  }

  @Test
  public void parseGroupMemberStatus_allResting() {
    String text =
        "Your group currently consists of:\r\n"
            + "  t                              (L) [HE:100% ST:Resting]\r\n"
            + "\r\n";

    assertThat(getScript(text).getGroupStatus())
        .isEqualTo(
            GroupMemberStatus.create(
                ImmutableSet.of("t"),
                /* youAreReady= */ false,
                /* allAreReady= */ false,
                /* lowestHealth= */ 1.0,
                /* lowestHealthMember= */ "t"));
  }

  @Test
  public void parseGroupMemberStatus_oneResting_im_ready() {
    String text =
        "Your group currently consists of:\r\n"
            + "  t                              (L) [HE: 48% ST:Resting]\r\n"
            + "  Super Conductor                (L) [HE: 50% ST:Ready]\r\n"
            + "\r\n";

    assertThat(getScript(text).getGroupStatus())
        .isEqualTo(
            GroupMemberStatus.create(
                ImmutableSet.of("t", "Super Conductor"),
                /* youAreReady= */ true,
                /* allAreReady= */ false,
                /* lowestHealth= */ .48,
                /* lowestHealthMember= */ "t"));
  }

  @Test
  public void parseGroupMemberStatus_oneResting_im_resting() {
    String text =
        "Your group currently consists of:\r\n"
            + "  t                              (L) [HE: 50% ST:Ready]\r\n"
            + "  Super Conductor                (L) [HE: 48% ST:Resting]\r\n"
            + "\r\n";

    assertThat(getScript(text).getGroupStatus())
        .isEqualTo(
            GroupMemberStatus.create(
                ImmutableSet.of("t", "Super Conductor"),
                /* youAreReady= */ false,
                /* allAreReady= */ false,
                /* lowestHealth= */ 0.48,
                /* lowestHealthMember= */ "Super Conductor"));
  }

  @Test
  public void getPlayersInRoom_empty() {
    ParsedAnsiText line = ParsedAnsiText.create("");

    ImmutableSet<String> players = TAScript.getPlayersInRoom(line);
    assertThat(players).isEmpty();
  }

  @Test
  public void getPlayersInRoom_multiplePlayers() {
    String text = "Dwarven Lord, Star Lord, Fisty, and Paladine are here.";
    ParsedAnsiText line = ParsedAnsiText.create(text);

    ImmutableSet<String> players = TAScript.getPlayersInRoom(line);

    assertThat(players).containsExactly("Dwarven Lord", "Star Lord", "Fisty", "Paladine");
  }

  @Test
  public void getPlayersInRoom_two_multipleFirst() {
    String text = "Dwarven Lord and Todd are here.";
    ParsedAnsiText line = ParsedAnsiText.create(text);

    ImmutableSet<String> players = TAScript.getPlayersInRoom(line);

    assertThat(players).containsExactly("Dwarven Lord", "Todd");
  }

  @Test
  public void getPlayersInRoom_two_multipleSecond() {
    String text = "Todd and Dwarven Lord are here.";
    ParsedAnsiText line = ParsedAnsiText.create(text);

    ImmutableSet<String> players = TAScript.getPlayersInRoom(line);

    assertThat(players).containsExactly("Dwarven Lord", "Todd");
  }

  @Test
  public void getPlayersInRoom_two_singleWords() {
    String text = "Todd and Rush are here.";
    ParsedAnsiText line = ParsedAnsiText.create(text);

    ImmutableSet<String> players = TAScript.getPlayersInRoom(line);

    assertThat(players).containsExactly("Rush", "Todd");
  }

  @Test
  public void getPlayersInRoom_singlePlayer_oneWord() {
    String text = "Todd is here.";
    ParsedAnsiText line = ParsedAnsiText.create(text);

    ImmutableSet<String> players = TAScript.getPlayersInRoom(line);

    assertThat(players).containsExactly("Todd");
  }

  @Test
  public void getPlayersInRoom_singlePlayer_twoWords() {
    String text = "Todd Jones is here.";
    ParsedAnsiText line = ParsedAnsiText.create(text);

    ImmutableSet<String> players = TAScript.getPlayersInRoom(line);

    assertThat(players).containsExactly("Todd Jones");
  }

  @Test
  public void getPlayersInRoom_noPlayers() {
    String text = "There is nobody here.";
    ParsedAnsiText line = ParsedAnsiText.create(text);

    ImmutableSet<String> players = TAScript.getPlayersInRoom(line);

    assertThat(players).isEmpty();
  }

  @Test
  public void getMonstersInRoom_single_doubleWord() {
    String text = "There is a lizard woman here.";
    ParsedAnsiText line = ParsedAnsiText.create(text);

    ImmutableMap<String, Integer> monsters = TAScript.getMonstersInRoom(line);

    assertThat(monsters).containsExactly("woman", 1);
  }

  @Test
  public void getMonstersInRoom_single_singleWord() {
    String text = "There is a rat here.";
    ParsedAnsiText line = ParsedAnsiText.create(text);

    ImmutableMap<String, Integer> monsters = TAScript.getMonstersInRoom(line);

    assertThat(monsters).containsExactly("rat", 1);
  }

  @Test
  public void getMonstersInRoom_double_singleWord() {
    String text = "There are two rats here.";
    ParsedAnsiText line = ParsedAnsiText.create(text);

    ImmutableMap<String, Integer> monsters = TAScript.getMonstersInRoom(line);

    assertThat(monsters).containsExactly("rat", 2);
  }

  @Test
  public void getMonstersInRoom_twoGroups_singles() {
    String text = "There is a female orc, and a lizard woman here.";
    ParsedAnsiText line = ParsedAnsiText.create(text);

    ImmutableMap<String, Integer> monsters = TAScript.getMonstersInRoom(line);

    assertThat(monsters).containsExactly("orc", 1, "woman", 1);
  }

  @Test
  public void getMonstersInRoom_threeGroups_singles() {
    String text = "There is a cave bear, a female orc, and a lizard woman here.";
    ParsedAnsiText line = ParsedAnsiText.create(text);

    ImmutableMap<String, Integer> monsters = TAScript.getMonstersInRoom(line);

    assertThat(monsters).containsExactly("bear", 1, "orc", 1, "woman", 1);
  }

  @Test
  public void getMonstersInRoom_bunchOfGroups_mixed() {
    String text =
        "There are two cave bears, a female kobold, a female orc, a huge rat, a lizard"
            + " man, a lizard woman, and an orc here.";
    ParsedAnsiText line = ParsedAnsiText.create(text);

    ImmutableMap<String, Integer> monsters = TAScript.getMonstersInRoom(line);

    assertThat(monsters)
        .containsExactly("bear", 2, "kobold", 1, "orc", 2, "rat", 1, "man", 1, "woman", 1);
  }

  @Test
  public void getMonstersInRoom_twoGroups_plural() {
    String text = "There are two flame giantesses, and two giant scorpions here.";
    ParsedAnsiText line = ParsedAnsiText.create(text);

    ImmutableMap<String, Integer> monsters = TAScript.getMonstersInRoom(line);

    assertThat(monsters).containsExactly("giantess", 2, "scorpion", 2);
  }

  @Test
  public void getMonstersInRoom_twoGroups_mixed() {
    String text = "There is a flame giantess, and two female elves here.";
    ParsedAnsiText line = ParsedAnsiText.create(text);

    ImmutableMap<String, Integer> monsters = TAScript.getMonstersInRoom(line);

    assertThat(monsters).containsExactly("giantes", 1, "el", 2);
  }

  @Test
  public void getTargets_empty() {
    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "There is nobody here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";
    TAScript.GetTargetResult result = getScript(text).getTargets();

    assertThat(result.getCount()).isEqualTo(0);
  }

  @Test
  public void getTargets_singleMonster() {
    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There is a huge rat here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";
    TAScript.GetTargetResult result = getScript(text).getTargets();

    assertThat(result.getCount()).isEqualTo(1);
    assertThat(result.getTarget()).isEqualTo("rat");
    assertThat(result.isPlayer()).isFalse();
  }

  @Test
  public void getTargets_multipleMonsters() {
    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There is a flame giantess, and two giant scorpions here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";
    TAScript.GetTargetResult result = getScript(text).getTargets();

    assertThat(result.getCount()).isEqualTo(3);
    assertThat(result.getTarget()).isEqualTo("giantes");
    assertThat(result.isPlayer()).isFalse();
  }

  @Test
  public void getTargets_multipleMonsters_withPlayer() {
    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There is a flame giantess, and two giant scorpions here.\r\n"
            + MAGENTA
            + "Paladine is here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";
    TAScript.GetTargetResult result = getScript(text).getTargets();

    assertThat(result.getCount()).isEqualTo(3);
    assertThat(result.getTarget()).isEqualTo("giantes");
    assertThat(result.isPlayer()).isFalse();
  }

  @Test
  public void getTargets_monstersSplitAcrossMultipleLines() {
    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There are four elite dwarven guards, two dwarven leutenants, and a dwarven\r\n"
            + "warlord here.\r\n"
            + MAGENTA
            + "Paladine is here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";
    TAScript.GetTargetResult result = getScript(text).getTargets();

    assertThat(result.getCount()).isEqualTo(7);
    assertThat(result.getTarget()).isEqualTo("guard");
    assertThat(result.isPlayer()).isFalse();
  }

  @Test
  public void getTargets_monstersSplitAcrossManyMultipleLines() {
    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There are four elite dwarven guards, two dwarven leutenants, a dwarven\r\n"
            + "warlord, six super long dope massive flame giantesses, two apollyon\r\n"
            + "dragons, and an orc here.\r\n"
            + MAGENTA
            + "Paladine is here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";
    TAScript.GetTargetResult result = getScript(text).getTargets();

    assertThat(result.getCount()).isEqualTo(16);
    assertThat(result.getTarget()).isEqualTo("guard");
    assertThat(result.isPlayer()).isFalse();
  }

  @Test
  public void getTargets_whichTriggeredA_NullPointerException() {
    String text =
        YELLOW
            + "You're on a path in the Elven Valley\r\n"
            + RED
            + "There is an elven champion, two elven warriors, a female elf, and an elven\r\n"
            + "scout here.\r\n"
            + MAGENTA
            + "Paladine is here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";
    TAScript.GetTargetResult result = getScript(text).getTargets();

    assertThat(result.getCount()).isEqualTo(5);
    assertThat(result.getTarget()).isEqualTo("champion");
    assertThat(result.isPlayer()).isFalse();
  }

  @Test
  public void getTargets_multipleMonsters_withPlayer_onAttackList() {
    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There is a flame giantess, and two giant scorpions here.\r\n"
            + MAGENTA
            + "Paladine is here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";
    TAScript tascript = getScript(text);
    tascript.addPlayerToAttack("Paladine");

    TAScript.GetTargetResult result = tascript.getTargets();

    assertThat(result.getCount()).isEqualTo(4);
    assertThat(result.getTarget()).isEqualTo("Paladine");
    assertThat(result.isPlayer()).isTrue();
  }

  @Test
  public void getTargets_multipleMonsters_withTwoPlayers_oneOnAttackList() {
    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There is a flame giantess, and two giant scorpions here.\r\n"
            + MAGENTA
            + "Dwarven Lord, Star Lord, Fisty, and Paladine are here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";
    TAScript tascript = getScript(text);
    tascript.addPlayerToAttack("Paladine");

    TAScript.GetTargetResult result = tascript.getTargets();

    assertThat(result.getCount()).isEqualTo(4);
    assertThat(result.getTarget()).isEqualTo("Paladine");
    assertThat(result.isPlayer()).isTrue();
  }

  @Test
  public void getTargets_noMonsters_withSinglePlayerToAttack() {
    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "Paladine is here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";
    TAScript tascript = getScript(text);
    tascript.addPlayerToAttack("Paladine");

    TAScript.GetTargetResult result = tascript.getTargets();

    assertThat(result.getCount()).isEqualTo(1);
    assertThat(result.getTarget()).isEqualTo("Paladine");
    assertThat(result.isPlayer()).isTrue();
  }

  @Test
  public void getTargets_noMonsters_withPlayersToAttack() {
    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "Paladine and Rush are here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";
    TAScript tascript = getScript(text);
    tascript.addPlayerToAttack("Paladine");
    tascript.addPlayerToAttack("Rush");

    TAScript.GetTargetResult result = tascript.getTargets();

    assertThat(result.getCount()).isEqualTo(2);
    assertThat(result.getTarget()).isEqualTo("Paladine");
    assertThat(result.isPlayer()).isTrue();
  }

  @Test
  public void getTargets_noMonsters_withSinglePlayerToAttack_multiword() {
    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "Super Conductor is here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";
    TAScript tascript = getScript(text);
    tascript.addPlayerToAttack("Super Conductor");

    TAScript.GetTargetResult result = tascript.getTargets();

    assertThat(result.getCount()).isEqualTo(1);
    assertThat(result.getTarget()).isEqualTo("Super");
    assertThat(result.isPlayer()).isTrue();
  }

  @Test
  public void getTargets_noMonsters_withPlayersToAttack_multiword() {
    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "Super Conductor and Evil Lyn are here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";
    TAScript tascript = getScript(text);
    tascript.addPlayerToAttack("Super Conductor");
    tascript.addPlayerToAttack("Evil Lyn");

    TAScript.GetTargetResult result = tascript.getTargets();

    assertThat(result.getCount()).isEqualTo(2);
    assertThat(result.getTarget()).isEqualTo("Super");
    assertThat(result.isPlayer()).isTrue();
  }

  @Test
  public void getPlayerStatus_noMana() {
    String text =
        "Mana:         0 / 0\r\n" + "Vitality:     19838 / 20000\r\n" + "Status:       Healthy\r\n";
    TAScript tascript = getScript(text);

    TAScript.PlayerStatus result = getScript(text).getPlayerStatus();

    assertThat(result.getMana()).isEqualTo(0);
    assertThat(result.getMaxMana()).isEqualTo(0);
    assertThat(result.getVitality()).isEqualTo(19838);
    assertThat(result.getMaxVitality()).isEqualTo(20000);
    assertThat(result.isHungry()).isFalse();
    assertThat(result.isThirsty()).isFalse();
    assertThat(result.getGroupMemberStatus()).isEmpty();
  }

  @Test
  public void getPlayerStatus_withMana() {
    String text =
        "Mana:         20 / 40\r\n"
            + "Vitality:     19838 / 20000\r\n"
            + "Status:       Healthy\r\n";
    TAScript tascript = getScript(text);

    TAScript.PlayerStatus result = getScript(text).getPlayerStatus();

    assertThat(result.getMana()).isEqualTo(20);
    assertThat(result.getMaxMana()).isEqualTo(40);
    assertThat(result.getVitality()).isEqualTo(19838);
    assertThat(result.getMaxVitality()).isEqualTo(20000);
    assertThat(result.isHungry()).isFalse();
    assertThat(result.isThirsty()).isFalse();
    assertThat(result.getGroupMemberStatus()).isEmpty();
  }

  @Test
  public void getPlayerStatus_outOfOrder() {
    String text =
        "Vitality:     19838 / 20000\r\n"
            + "Status:       Healthy\r\n\r\n"
            + "Mana:         20 / 40\r\n"
            + "Vitality:     19838 / 20000\r\n"
            + "Status:       Healthy\r\n";
    TAScript tascript = getScript(text);

    TAScript.PlayerStatus result = getScript(text).getPlayerStatus();

    assertThat(result.getMana()).isEqualTo(20);
    assertThat(result.getMaxMana()).isEqualTo(40);
    assertThat(result.getVitality()).isEqualTo(19838);
    assertThat(result.getMaxVitality()).isEqualTo(20000);
    assertThat(result.isHungry()).isFalse();
    assertThat(result.isThirsty()).isFalse();
    assertThat(result.getGroupMemberStatus()).isEmpty();
  }

  @Test
  public void getPlayerStatus_hungry() {
    String text =
        "Mana:         20 / 40\r\n" + "Vitality:     30 / 31\r\n" + "Status:       Hungry\r\n";
    TAScript tascript = getScript(text);

    TAScript.PlayerStatus result = getScript(text).getPlayerStatus();

    assertThat(result.getMana()).isEqualTo(20);
    assertThat(result.getMaxMana()).isEqualTo(40);
    assertThat(result.getVitality()).isEqualTo(30);
    assertThat(result.getMaxVitality()).isEqualTo(31);
    assertThat(result.isHungry()).isTrue();
    assertThat(result.isThirsty()).isFalse();
    assertThat(result.getGroupMemberStatus()).isEmpty();
  }

  @Test
  public void getPlayerStatus_thirsty() {
    String text =
        "Mana:         20 / 40\r\n" + "Vitality:     30 / 31\r\n" + "Status:       Thirsty\r\n";
    TAScript tascript = getScript(text);

    TAScript.PlayerStatus result = getScript(text).getPlayerStatus();

    assertThat(result.getMana()).isEqualTo(20);
    assertThat(result.getMaxMana()).isEqualTo(40);
    assertThat(result.getVitality()).isEqualTo(30);
    assertThat(result.getMaxVitality()).isEqualTo(31);
    assertThat(result.isHungry()).isFalse();
    assertThat(result.isThirsty()).isTrue();
    assertThat(result.getGroupMemberStatus()).isEmpty();
  }

  @Test
  public void getPlayerStatus_groupHealSpell() {
    configuration = configuration.toBuilder().setGroupHealSpell("kusamotumaru").build();

    String text =
        "Mana:         20 / 40\r\n"
            + "Vitality:     30 / 31\r\n"
            + "Status:       Thirsty\r\n\r\n"
            + "Your group currently consists of:\r\n"
            + "  t                              (L) [HE: 99% ST:Ready]\r\n"
            + "  Super Conductor                (L) [HE: 10% ST:Ready]\r\n"
            + "  Dude                           (L) [HE: 44% ST:Ready]\r\n"
            + "\r\n";
    TAScript tascript = getScript(text);

    TAScript.PlayerStatus result = getScript(text).getPlayerStatus();

    assertThat(result.getMana()).isEqualTo(20);
    assertThat(result.getMaxMana()).isEqualTo(40);
    assertThat(result.getVitality()).isEqualTo(30);
    assertThat(result.getMaxVitality()).isEqualTo(31);
    assertThat(result.isHungry()).isFalse();
    assertThat(result.isThirsty()).isTrue();
    assertThat(result.getGroupMemberStatus())
        .hasValue(
            GroupMemberStatus.create(
                ImmutableSet.of("t", "Super Conductor", "Dude"),
                /* youAreReady= */ true,
                /* allAreReady= */ true,
                /* lowestHealth= */ .1,
                /* lowestHealthMember= */ "Super Conductor"));
  }

  @Test
  public void getPlayerStatus_groupHealSpell_onlyOneMember() {
    configuration = configuration.toBuilder().setGroupHealSpell("kusamotumaru").build();

    String text =
        "Mana:         20 / 40\r\n"
            + "Vitality:     30 / 31\r\n"
            + "Status:       Thirsty\r\n\r\n"
            + "Your group currently consists of:\r\n"
            + "  t                              (L) [HE: 99% ST:Ready]\r\n"
            + "\r\n";
    TAScript tascript = getScript(text);

    TAScript.PlayerStatus result = getScript(text).getPlayerStatus();

    assertThat(result.getMana()).isEqualTo(20);
    assertThat(result.getMaxMana()).isEqualTo(40);
    assertThat(result.getVitality()).isEqualTo(30);
    assertThat(result.getMaxVitality()).isEqualTo(31);
    assertThat(result.isHungry()).isFalse();
    assertThat(result.isThirsty()).isTrue();
    assertThat(result.getGroupMemberStatus())
        .hasValue(
            GroupMemberStatus.create(
                ImmutableSet.of("t"),
                /* youAreReady= */ true,
                /* allAreReady= */ true,
                /* lowestHealth= */ .99,
                /* lowestHealthMember= */ "t"));
  }

  @Test
  public void logFile_writesCorrectly() {
    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "Super Conductor is here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";
    TAScript tascript = getScript(text);
    tascript.addPlayerToAttack("Super Conductor");

    TAScript.GetTargetResult result = tascript.getTargets();

    assertThat(logOutput.toString()).isEqualTo(text.replace("\r", ""));
  }

  @Test
  public void checkHealth_notRequired() {
    configuration =
        configuration.toBuilder()
            .setHealPercentage(0.9)
            .setBigHealPercentage(0.7)
            .setCriticalPercentage(0.5)
            .setLogOffCommand("=x\r\n")
            .setHealSpellIsAttack(false)
            .setBigHealSpellIsAttack(false)
            .setHealSpell("motu")
            .setBigHealSpell("kusamotu")
            .build();

    String text =
        "Mana:         0 / 0\r\n" + "Vitality:     200 / 200\r\n" + "Status:       Healthy\r\n";
    TAScript tascript = getScript(text);

    getScript(text).checkHealth();

    assertThat(output.toString()).isEqualTo("he\r\n");
    verify(sleeper, never()).sleep(any());
  }

  @Test
  public void checkHealth_smallHeal() {
    configuration =
        configuration.toBuilder()
            .setHealPercentage(0.9)
            .setBigHealPercentage(0.7)
            .setCriticalPercentage(0.5)
            .setLogOffCommand("=x\r\n")
            .setHealSpellIsAttack(false)
            .setBigHealSpellIsAttack(false)
            .setHealSpell("motu")
            .setBigHealSpell("kusamotu")
            .build();

    String text =
        "Mana:         0 / 0\r\n"
            + "Vitality:     90 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     91 / 100\r\n"
            + "Status:       Healthy\r\n";
    TAScript tascript = getScript(text);

    getScript(text).checkHealth();

    assertThat(output.toString()).isEqualTo("he\r\nc motu Super\r\nhe\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void checkHealth_smallMultipleTimes() {
    configuration =
        configuration.toBuilder()
            .setHealPercentage(0.9)
            .setBigHealPercentage(0.7)
            .setCriticalPercentage(0.5)
            .setLogOffCommand("=x\r\n")
            .setHealSpellIsAttack(false)
            .setBigHealSpellIsAttack(false)
            .setHealSpell("motu")
            .setBigHealSpell("kusamotu")
            .build();

    String text =
        "Mana:         0 / 0\r\n"
            + "Vitality:     71 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     76 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     88 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     92 / 100\r\n"
            + "Status:       Healthy\r\n";
    TAScript tascript = getScript(text);

    getScript(text).checkHealth();

    assertThat(output.toString())
        .isEqualTo("he\r\nc motu Super\r\nhe\r\nc motu Super\r\nhe\r\nc motu Super\r\nhe\r\n");
    verify(sleeper, times(3)).sleep(any());
  }

  @Test
  public void checkHealth_bigHealToSmallHeal() {
    configuration =
        configuration.toBuilder()
            .setHealPercentage(0.9)
            .setBigHealPercentage(0.7)
            .setCriticalPercentage(0.5)
            .setLogOffCommand("=x\r\n")
            .setHealSpellIsAttack(false)
            .setBigHealSpellIsAttack(false)
            .setHealSpell("motu")
            .setBigHealSpell("kusamotu")
            .build();

    String text =
        "Mana:         0 / 0\r\n"
            + "Vitality:     69 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     80 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     92 / 100\r\n"
            + "Status:       Healthy\r\n";
    TAScript tascript = getScript(text);

    getScript(text).checkHealth();

    assertThat(output.toString())
        .isEqualTo("he\r\nc kusamotu Super\r\nhe\r\nc motu Super\r\nhe\r\n");
    verify(sleeper, times(2)).sleep(any());
  }

  @Test
  public void checkHealth_bigHealToSmallHeal_attackHeals() {
    configuration =
        configuration.toBuilder()
            .setHealPercentage(0.9)
            .setBigHealPercentage(0.7)
            .setCriticalPercentage(0.5)
            .setLogOffCommand("=x\r\n")
            .setHealSpellIsAttack(true)
            .setBigHealSpellIsAttack(true)
            .setHealSpell("motu")
            .setBigHealSpell("kusamotu")
            .build();

    String text =
        "Mana:         0 / 0\r\n"
            + "Vitality:     69 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     80 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     92 / 100\r\n"
            + "Status:       Healthy\r\n";
    TAScript tascript = getScript(text);

    getScript(text).checkHealth();

    assertThat(output.toString()).isEqualTo("he\r\n");
    verify(sleeper, never()).sleep(any());
  }

  @Test
  public void checkHealth_bigHealToSmallHeal_onlyBigHealSpellIsAttack() {
    configuration =
        configuration.toBuilder()
            .setHealPercentage(0.9)
            .setBigHealPercentage(0.7)
            .setCriticalPercentage(0.5)
            .setLogOffCommand("=x\r\n")
            .setHealSpellIsAttack(false)
            .setBigHealSpellIsAttack(true)
            .setHealSpell("motu")
            .setBigHealSpell("kusamotu")
            .build();

    String text =
        "Mana:         0 / 0\r\n"
            + "Vitality:     59 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     80 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     92 / 100\r\n"
            + "Status:       Healthy\r\n";
    TAScript tascript = getScript(text);

    getScript(text).checkHealth();

    assertThat(output.toString()).isEqualTo("he\r\nc motu Super\r\nhe\r\nc motu Super\r\nhe\r\n");
    verify(sleeper, times(2)).sleep(any());
  }

  @Test
  public void checkHealth_criticalAbort() {
    configuration =
        configuration.toBuilder()
            .setHealPercentage(0.9)
            .setBigHealPercentage(0.7)
            .setCriticalPercentage(0.5)
            .setLogOffCommand("=x\r\n")
            .setHealSpellIsAttack(false)
            .setBigHealSpellIsAttack(false)
            .setHealSpell("motu")
            .setBigHealSpell("kusamotu")
            .build();

    String text =
        "Mana:         0 / 0\r\n" + "Vitality:     49 / 100\r\n" + "Status:       Healthy\r\n";
    TAScript tascript = getScript(text);

    assertThrows(LogoffException.class, () -> getScript(text).checkHealth());

    assertThat(output.toString()).isEqualTo("he\r\n=x\r\n");
    verify(sleeper, never()).sleep(any());
  }

  @Test
  public void waitUntilReady_nothingSpecial() {
    configuration =
        configuration.toBuilder()
            .setHasYari(false)
            .setWaitForAllMembers(false)
            .setMinimumMoveMana(0)
            .build();

    String text =
        BLUE
            + "You suddenly feel very vulnerable!\r\n"
            + WHITE
            + "Your group currently consists of:\r\n"
            + "  t                              (L) [HE:100% ST:Resting]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     49 / 100\r\n"
            + "Status:       Healthy\r\n";

    getScript(text).waitUntilReady();

    assertThat(output.toString()).isEqualTo("gr\r\nhe\r\n");
    verify(sleeper, never()).sleep(any());
  }

  @Test
  public void waitUntilReady_notReadyYet() {
    configuration =
        configuration.toBuilder()
            .setHasYari(false)
            .setWaitForAllMembers(false)
            .setMinimumMoveMana(0)
            .build();

    String text =
        "Your group currently consists of:\r\n"
            + "  t                              (L) [HE:100% ST:Resting]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Resting]\r\n"
            + "\r\n"
            + "Your group currently consists of:\r\n"
            + "  t                              (L) [HE:100% ST:Resting]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     49 / 100\r\n"
            + "Status:       Healthy\r\n";

    getScript(text).waitUntilReady();

    assertThat(output.toString()).isEqualTo("gr\r\ngr\r\nhe\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void waitUntilReady_waitForAll_notReadyYet() {
    configuration =
        configuration.toBuilder()
            .setHasYari(false)
            .setWaitForAllMembers(true)
            .setMinimumMoveMana(0)
            .build();

    String text =
        "Your group currently consists of:\r\n"
            + "  t                              (L) [HE:100% ST:Resting]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Resting]\r\n"
            + "\r\n"
            + "Your group currently consists of:\r\n"
            + "  t                              (L) [HE:100% ST:Resting]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n"
            + "Your group currently consists of:\r\n"
            + "  t                              (L) [HE:100% ST:Ready]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     49 / 100\r\n"
            + "Status:       Healthy\r\n";

    getScript(text).waitUntilReady();

    assertThat(output.toString()).isEqualTo("gr\r\ngr\r\ngr\r\nhe\r\n");
    verify(sleeper, times(2)).sleep(any());
  }

  @Test
  public void waitUntilReady_castsYariWhenNecessary() {
    configuration =
        configuration.toBuilder()
            .setHasYari(true)
            .setWaitForAllMembers(false)
            .setMinimumMoveMana(0)
            .build();

    String text =
        BLUE
            + "You suddenly feel very vulnerable!\r\n"
            + WHITE
            + "Your group currently consists of:\r\n"
            + "  t                              (L) [HE:100% ST:Resting]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n"
            + "Your group currently consists of:\r\n"
            + "  t                              (L) [HE:100% ST:Resting]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     49 / 100\r\n"
            + "Status:       Healthy\r\n";

    getScript(text).waitUntilReady();

    assertThat(output.toString()).isEqualTo("gr\r\nc yari Super\r\ngr\r\nhe\r\n");
    verify(sleeper, never()).sleep(any());
  }

  @Test
  public void waitUntilReady_sustenanceCommandWhenNecessary() {
    configuration =
        configuration.toBuilder()
            .setSustenanceCommand("c kotarimaru")
            .setWaitForAllMembers(false)
            .setMinimumMoveMana(0)
            .build();

    String text =
        "Your group currently consists of:\r\n"
            + "  t                              (L) [HE:100% ST:Resting]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     49 / 100\r\n"
            + "Status:       Hungry\r\n"
            + "\r\n"
            + "Your group currently consists of:\r\n"
            + "  t                              (L) [HE:100% ST:Resting]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     49 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "\r\n";

    getScript(text).waitUntilReady();

    assertThat(output.toString()).isEqualTo("gr\r\nhe\r\nc kotarimaru\r\ngr\r\nhe\r\n");
    verify(sleeper, never()).sleep(any());
  }

  @Test
  public void waitUntilReady_waitsForMana() {
    configuration =
        configuration.toBuilder().setWaitForAllMembers(false).setMinimumMoveMana(20).build();

    String text =
        "Your group currently consists of:\r\n"
            + "  t                              (L) [HE:100% ST:Resting]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n"
            + "Mana:         16 / 300\r\n"
            + "Vitality:     49 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "\r\n"
            + "Your group currently consists of:\r\n"
            + "  t                              (L) [HE:100% ST:Resting]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n"
            + "Mana:         19 / 300\r\n"
            + "Vitality:     49 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "\r\n"
            + "Your group currently consists of:\r\n"
            + "  t                              (L) [HE:100% ST:Resting]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n"
            + "Mana:         20 / 300\r\n"
            + "Vitality:     49 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "\r\n";

    getScript(text).waitUntilReady();

    assertThat(output.toString()).isEqualTo("gr\r\nhe\r\ngr\r\nhe\r\ngr\r\nhe\r\n");
    verify(sleeper, times(2)).sleep(any());
  }

  @Test
  public void attackTargets_nothingToAttack() {
    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "There is nobody here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";

    getScript(text).attackTargets();

    assertThat(output.toString()).isEqualTo("\r\n");
    verify(sleeper, never()).sleep(any());
  }

  @Test
  public void attackTargets_nothingToAttack_playerInRoom() {
    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "Rush is here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";

    getScript(text).attackTargets();

    assertThat(output.toString()).isEqualTo("\r\n");
    verify(sleeper, never()).sleep(any());
  }

  @Test
  public void attackTargets_attacksPlayer() {
    configuration = configuration.toBuilder().setNumberOfPhysicalAttacks(2).build();

    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "Rush is here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n"
            + "\r\n"
            + "Mana:         19 / 300\r\n"
            + "Vitality:     49 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "\r\n"
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "There is nobody here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";

    TAScript script = getScript(text);
    script.addPlayerToAttack("Rush");
    script.attackTargets();

    assertThat(output.toString()).isEqualTo("\r\nremove Rush\r\na Rush\r\na Rush\r\nhe\r\n\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void attackTargets_attacksMonsterAndCastsSpell() {
    configuration =
        configuration.toBuilder()
            .setNumberOfPhysicalAttacks(2)
            .setAttackSpell("tami")
            .setGroupAttackSpell("dobudakidaku")
            .setMinimumAttackMana(10)
            .build();

    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There is a huge rat here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n"
            + "\r\n"
            + "Mana:         19 / 300\r\n"
            + "Vitality:     49 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "\r\n"
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "There is nobody here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";

    TAScript script = getScript(text);
    script.attackTargets();

    assertThat(output.toString()).isEqualTo("\r\na rat\r\na rat\r\nhe\r\nc tami rat\r\n\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void attackTargets_attacksMonsterAndCastsSpell_notEnoughMana() {
    configuration =
        configuration.toBuilder()
            .setNumberOfPhysicalAttacks(2)
            .setAttackSpell("tami")
            .setGroupAttackSpell("dobudakidaku")
            .setMinimumAttackMana(20)
            .build();

    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There is a huge rat here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n"
            + "\r\n"
            + "Mana:         19 / 300\r\n"
            + "Vitality:     49 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "\r\n"
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "There is nobody here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";

    TAScript script = getScript(text);
    script.attackTargets();

    assertThat(output.toString()).isEqualTo("\r\na rat\r\na rat\r\nhe\r\n\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void attackTargets_attacksMonsterAndCastsGroupSpell() {
    configuration =
        configuration.toBuilder()
            .setNumberOfPhysicalAttacks(2)
            .setAttackSpell("tami")
            .setGroupAttackSpell("dobudakidaku")
            .setMinimumAttackMana(10)
            .build();

    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There is a flame giantess, and two giant scorpions here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n"
            + "\r\n"
            + "Mana:         19 / 300\r\n"
            + "Vitality:     49 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "\r\n"
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "There is nobody here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";

    TAScript script = getScript(text);
    script.attackTargets();

    assertThat(output.toString())
        .isEqualTo("\r\na giantes\r\na giantes\r\nhe\r\nc dobudakidaku\r\n\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void attackTargets_additionalAttackCommand() {
    configuration =
        configuration.toBuilder()
            .setNumberOfPhysicalAttacks(2)
            .setAttackSpell("tami")
            .setGroupAttackSpell("dobudakidaku")
            .setMinimumAttackMana(10)
            .setAdditionalAttackCommand("single $1")
            .setAdditionalGroupAttackCommand("area $1")
            .build();

    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There is a flame giantess here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n"
            + "\r\n"
            + "Mana:         19 / 300\r\n"
            + "Vitality:     49 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "\r\n"
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "There is nobody here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";

    TAScript script = getScript(text);
    script.attackTargets();

    assertThat(output.toString())
        .isEqualTo("\r\na giantes\r\na giantes\r\nhe\r\nc tami giantes\r\nsingle giantes\r\n\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void attackTargets_groupAdditionalAttackCommand() {
    configuration =
        configuration.toBuilder()
            .setNumberOfPhysicalAttacks(2)
            .setAttackSpell("tami")
            .setGroupAttackSpell("dobudakidaku")
            .setMinimumAttackMana(10)
            .setAdditionalAttackCommand("single $1")
            .setAdditionalGroupAttackCommand("area $1")
            .build();

    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There is a flame giantess, and two giant scorpions here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n"
            + "\r\n"
            + "Mana:         19 / 300\r\n"
            + "Vitality:     49 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "\r\n"
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "There is nobody here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";

    TAScript script = getScript(text);
    script.attackTargets();

    assertThat(output.toString())
        .isEqualTo("\r\na giantes\r\na giantes\r\nhe\r\nc dobudakidaku\r\narea giantes\r\n\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void attackTargets_attacksMonsterAndCastsGroupSpell_notEnoughMana() {
    configuration =
        configuration.toBuilder()
            .setNumberOfPhysicalAttacks(2)
            .setAttackSpell("tami")
            .setGroupAttackSpell("dobudakidaku")
            .setMinimumAttackMana(20)
            .build();

    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There is a flame giantess, and two giant scorpions here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n"
            + "\r\n"
            + "Mana:         19 / 300\r\n"
            + "Vitality:     49 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "\r\n"
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "There is nobody here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";

    TAScript script = getScript(text);
    script.attackTargets();

    assertThat(output.toString()).isEqualTo("\r\na giantes\r\na giantes\r\nhe\r\n\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void attackTargets_healsFirst() {
    configuration =
        configuration.toBuilder()
            .setNumberOfPhysicalAttacks(2)
            .setHealDuringBattle(true)
            .setHealSpell("motu")
            .setBigHealSpell("kusamotu")
            .setHealPercentage(0.8)
            .setBigHealPercentage(0.6)
            .setAttackSpell("tami")
            .setGroupAttackSpell("dobudakidaku")
            .setMinimumAttackMana(20)
            .build();

    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There is a flame giantess here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n"
            + "\r\n"
            + "Mana:         49 / 300\r\n"
            + "Vitality:     80 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "\r\n"
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "There is nobody here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";

    TAScript script = getScript(text);
    script.attackTargets();

    assertThat(output.toString())
        .isEqualTo("\r\na giantes\r\na giantes\r\nhe\r\nc motu Super\r\nc tami giantes\r\n\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void attackTargets_healsBigFirst() {
    configuration =
        configuration.toBuilder()
            .setNumberOfPhysicalAttacks(2)
            .setHealDuringBattle(true)
            .setHealSpell("motu")
            .setBigHealSpell("kusamotu")
            .setHealPercentage(0.8)
            .setBigHealPercentage(0.6)
            .setAttackSpell("tami")
            .setGroupAttackSpell("dobudakidaku")
            .setMinimumAttackMana(20)
            .build();

    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There is a flame giantess here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n"
            + "\r\n"
            + "Mana:         49 / 300\r\n"
            + "Vitality:     60 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "\r\n"
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "There is nobody here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";

    TAScript script = getScript(text);
    script.attackTargets();

    assertThat(output.toString())
        .isEqualTo(
            "\r\na giantes\r\na giantes\r\nhe\r\nc kusamotu Super\r\nc tami giantes\r\n\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void attackTargets_groupHealsFirst() {
    configuration =
        configuration.toBuilder()
            .setNumberOfPhysicalAttacks(2)
            .setHealDuringBattle(true)
            .setHealSpell("motu")
            .setBigHealSpell("kusamotu")
            .setGroupHealSpell("kusamotumaru")
            .setHealPercentage(0.8)
            .setBigHealPercentage(0.6)
            .setGroupHealPercentage(0.8)
            .setAttackSpell("tami")
            .setGroupAttackSpell("dobudakidaku")
            .setMinimumAttackMana(20)
            .build();

    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There is a flame giantess here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n"
            + "\r\n"
            + "Mana:         49 / 300\r\n"
            + "Vitality:     60 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "\r\n"
            + "Your group currently consists of:\r\n"
            + "  t                              (L) [HE: 80% ST:Ready]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n"
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "There is nobody here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";

    TAScript script = getScript(text);
    script.attackTargets();

    assertThat(output.toString())
        .isEqualTo(
            "\r\na giantes\r\na giantes\r\nhe\r\ngr\r\nc kusamotumaru\r\nc tami giantes\r\n\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void attackTargets_groupHealsFirst_butNotEnoughInGroup() {
    configuration =
        configuration.toBuilder()
            .setNumberOfPhysicalAttacks(2)
            .setHealDuringBattle(true)
            .setHealSpell("motu")
            .setBigHealSpell("kusamotu")
            .setGroupHealSpell("kusamotumaru")
            .setHealPercentage(0.8)
            .setBigHealPercentage(0.6)
            .setGroupHealPercentage(0.8)
            .setAttackSpell("tami")
            .setGroupAttackSpell("dobudakidaku")
            .setMinimumAttackMana(20)
            .build();

    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There is a flame giantess here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n"
            + "\r\n"
            + "Mana:         49 / 300\r\n"
            + "Vitality:     60 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "\r\n"
            + "Your group currently consists of:\r\n"
            + "  Super Conductor                (L) [HE: 60% ST:Ready]\r\n"
            + "\r\n"
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "There is nobody here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";

    TAScript script = getScript(text);
    script.attackTargets();

    assertThat(output.toString())
        .isEqualTo(
            "\r\na giantes\r\na giantes\r\nhe\r\ngr\r\nc kusamotu Super\r\nc tami giantes\r\n\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void attackTargets_healsAlways_noSpellWithoutMana() {
    configuration =
        configuration.toBuilder()
            .setNumberOfPhysicalAttacks(2)
            .setHealDuringBattle(true)
            .setHealSpell("motu")
            .setBigHealSpell("kusamotu")
            .setHealPercentage(0.8)
            .setBigHealPercentage(0.6)
            .setAttackSpell("tami")
            .setGroupAttackSpell("dobudakidaku")
            .setMinimumAttackMana(20)
            .build();

    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There is a flame giantess here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n"
            + "\r\n"
            + "Mana:         19 / 300\r\n"
            + "Vitality:     80 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "\r\n"
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "There is nobody here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";

    TAScript script = getScript(text);
    script.attackTargets();

    assertThat(output.toString())
        .isEqualTo("\r\na giantes\r\na giantes\r\nhe\r\nc motu Super\r\n\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void attackTargets_healsDisabledDuringBattle() {
    configuration =
        configuration.toBuilder()
            .setNumberOfPhysicalAttacks(2)
            .setHealDuringBattle(false)
            .setHealSpell("motu")
            .setBigHealSpell("kusamotu")
            .setHealPercentage(0.8)
            .setBigHealPercentage(0.6)
            .setAttackSpell("tami")
            .setGroupAttackSpell("dobudakidaku")
            .setMinimumAttackMana(20)
            .build();

    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There is a flame giantess here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n"
            + "\r\n"
            + "Mana:         49 / 300\r\n"
            + "Vitality:     80 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "\r\n"
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "There is nobody here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";

    TAScript script = getScript(text);
    script.attackTargets();

    assertThat(output.toString())
        .isEqualTo("\r\na giantes\r\na giantes\r\nhe\r\nc tami giantes\r\n\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void attackTargets_healsDisabledDuringBattle_butHealSpellIsAttack() {
    configuration =
        configuration.toBuilder()
            .setNumberOfPhysicalAttacks(2)
            .setHealDuringBattle(false)
            .setHealSpell("yilazi")
            .setHealSpellIsAttack(true)
            .setBigHealSpell("kusamotu")
            .setHealPercentage(0.8)
            .setBigHealPercentage(0.6)
            .setAttackSpell("tami")
            .setGroupAttackSpell("dobudakidaku")
            .setMinimumAttackMana(20)
            .build();

    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There is a flame giantess here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n"
            + "\r\n"
            + "Mana:         49 / 300\r\n"
            + "Vitality:     80 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "\r\n"
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "There is nobody here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";

    TAScript script = getScript(text);
    script.attackTargets();

    assertThat(output.toString())
        .isEqualTo(
            "\r\na giantes\r\na giantes\r\nhe\r\nc yilazi giantes\r\nc tami giantes\r\n\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void attackTargets_healsDisabledDuringBattle_butBigHealSpellIsAttack() {
    configuration =
        configuration.toBuilder()
            .setNumberOfPhysicalAttacks(2)
            .setHealDuringBattle(false)
            .setHealSpell("motu")
            .setBigHealSpell("yilazi")
            .setBigHealSpellIsAttack(true)
            .setHealPercentage(0.8)
            .setBigHealPercentage(0.6)
            .setAttackSpell("tami")
            .setGroupAttackSpell("dobudakidaku")
            .setMinimumAttackMana(20)
            .build();

    String text =
        YELLOW
            + "You're in the north plaza.\r\n"
            + RED
            + "There is a flame giantess here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n"
            + "\r\n"
            + "Mana:         49 / 300\r\n"
            + "Vitality:     60 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "\r\n"
            + "You're in the north plaza.\r\n"
            + MAGENTA
            + "There is nobody here.\r\n"
            + CYAN
            + "There is nothing on the floor.\r\n";

    TAScript script = getScript(text);
    script.attackTargets();

    assertThat(output.toString())
        .isEqualTo(
            "\r\na giantes\r\na giantes\r\nhe\r\nc yilazi giantes\r\nc tami giantes\r\n\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void attacked_byMonster() {
    String text =
        RED + "The dwarven guard attacked you with his battleax for 28 damage!\r\nTest\r\n";

    assertThat(getScript(text).getNextLine()).isNotNull();
    assertThat(output.toString()).isEmpty();
  }

  @Test
  public void attacked_byPlayer() {
    String text = RED + "Super Conductor attacked you with a battleax for 28 damage!\r\nTest\r\n";

    TAScript script = getScript(text);
    assertThrows(
        AttackException.class,
        () -> {
          ParsedAnsiText tmp = script.getNextLine();
        });
    assertThat(output.toString()).isEmpty();
    assertThat(script.getPlayersToAttack()).containsExactly("Super Conductor");
  }

  @Test
  public void attacked_byDischarge() {
    String text =
        RED
            + "Super Conductor discharged a weak beam of bright energy at you for 3 damage!\r\n"
            + "Test\r\n";

    TAScript script = getScript(text);
    assertThrows(
        AttackException.class,
        () -> {
          ParsedAnsiText tmp = script.getNextLine();
        });
    assertThat(output.toString()).isEmpty();
    assertThat(script.getPlayersToAttack()).containsExactly("Super Conductor");
  }

  @Test
  public void attacked_playerOnlyArea() {
    String text =
        RED
            + "Super Conductor just discharged a storm of blue energy bolts at hostile people in"
            + " the area!\r\n"
            + "Test\r\n";

    TAScript script = getScript(text);
    assertThrows(
        AttackException.class,
        () -> {
          ParsedAnsiText tmp = script.getNextLine();
        });
    assertThat(output.toString()).isEmpty();
    assertThat(script.getPlayersToAttack()).containsExactly("Super Conductor");
  }

  @Test
  public void attacked_playerOnlyArea_inGroup() {
    String text =
        RED
            + "Super Conductor just discharged a storm of blue energy bolts at hostile people in"
            + " the area!\r\n"
            + "Test\r\n";

    TAScript script = getScript(text);
    script.addGroupMember("Super Conductor");

    assertThat(script.getNextLine()).isNotNull();

    assertThat(output.toString()).isEmpty();
    assertThat(script.getPlayersToAttack()).isEmpty();
  }

  @Test
  public void attacked_allHostileArea() {
    String text =
        RED
            + "Super Conductor just discharged a maelstrom of searing flame clouds and huge ice"
            + " shards at all hostiles in the area!\r\n"
            + "Test\r\n";

    TAScript script = getScript(text);
    assertThrows(
        AttackException.class,
        () -> {
          ParsedAnsiText tmp = script.getNextLine();
        });

    assertThat(output.toString()).isEmpty();
    assertThat(script.getPlayersToAttack()).containsExactly("Super Conductor");
  }

  @Test
  public void attacked_allHostileArea_inGroup() {
    String text =
        RED
            + "Super Conductor just discharged a maelstrom of searing flame clouds and huge ice"
            + " shards at all hostiles in the area!\r\n"
            + "Test\r\n";

    TAScript script = getScript(text);
    script.addGroupMember("Super Conductor");

    assertThat(script.getNextLine()).isNotNull();

    assertThat(output.toString()).isEmpty();
    assertThat(script.getPlayersToAttack()).isEmpty();
  }

  @Test
  public void playerJoining() {
    String text = "Super Conductor is asking to join your group.\r\nTest\r\n";

    TAScript script = getScript(text);

    assertThat(script.getNextLine()).isNotNull();

    assertThat(output.toString()).isEqualTo("add Super\r\n");
    assertThat(script.getGroupMembers()).containsExactly("Super Conductor");
  }

  @Test
  public void playerJoining_onHostileList() {
    String text = "Super Conductor is asking to join your group.\r\nTest\r\n";

    TAScript script = getScript(text);
    script.addPlayerToAttack("Super Conductor");

    assertThat(script.getNextLine()).isNotNull();

    assertThat(output.toString()).isEmpty();
    assertThat(script.getGroupMembers()).isEmpty();
  }

  @Test
  public void playerLeaving() {
    String text = "Super Conductor has just left your group.\r\nTest\r\n";

    TAScript script = getScript(text);
    script.addGroupMember("Super Conductor");
    assertThat(script.getGroupMembers()).containsExactly("Super Conductor");

    assertThat(script.getNextLine()).isNotNull();

    assertThat(output.toString()).isEmpty();
    assertThat(script.getGroupMembers()).isEmpty();
  }

  @Test
  public void isDamageLine_normalAttack() {
    String text = "Your attack hit the female orc for 18 damage!\r\nTest\r\n";

    TAScript script = getScript(text);

    assertThat(script.getNextLine()).isNotNull();

    assertThat(output.toString()).isEmpty();
    assertThat(script.getStats(AttackType.Physical).getCount()).isEqualTo(1);
    assertThat(script.getStats(AttackType.Physical).getTotalDamage()).isEqualTo(18);

    assertThat(script.getStats(AttackType.Spell).getCount()).isEqualTo(0);
  }

  @Test
  public void isDamageLine_spellDischarge() {
    String text = "You discharged the spell at the imp for 379 damage!\r\nTest\r\n";

    TAScript script = getScript(text);

    assertThat(script.getNextLine()).isNotNull();

    assertThat(output.toString()).isEmpty();
    assertThat(script.getStats(AttackType.Spell).getCount()).isEqualTo(1);
    assertThat(script.getStats(AttackType.Spell).getTotalDamage()).isEqualTo(379);

    assertThat(script.getStats(AttackType.Physical).getCount()).isEqualTo(0);
  }

  @Test
  public void isDamageLine_spellDischarge_area() {
    String text =
        "You discharged the spell at all hostiles in the area for 298 damage!\r\nTest\r\n";

    TAScript script = getScript(text);

    assertThat(script.getNextLine()).isNotNull();

    assertThat(output.toString()).isEmpty();
    assertThat(script.getStats(AttackType.Spell).getCount()).isEqualTo(1);
    assertThat(script.getStats(AttackType.Spell).getTotalDamage()).isEqualTo(298);

    assertThat(script.getStats(AttackType.Physical).getCount()).isEqualTo(0);
  }

  @Test
  public void run_complete() {
    configuration = configuration.toBuilder().setLogOffCommand("=x\r\n").build();
    String text =
        "Your group currently consists of:\r\n"
            + "  t                              (L) [HE:100% ST:Ready]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n"
            + "Super Conductor nodded to you in agreement!\r\n";

    TAScript script = getScript(text);
    assertThrows(NoSuchElementException.class, () -> script.run());

    assertThat(output.toString()).isEqualTo("gr\r\nnod t\r\nnod Super\r\ngr\r\nx\r\n\r\n=x\r\n");
  }

  @Test
  public void run_withTemple() {
    configuration = configuration.toBuilder().setLogOffCommand("=x\r\n").build();
    String text =
        "Your group currently consists of:\r\n"
            + "  t                              (L) [HE:100% ST:Ready]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n"
            + "Super Conductor nodded to you in agreement!\r\n"
            + GREEN
            + "There is a grey robed priest here.\r\n";

    TAScript script = getScript(text);
    assertThrows(DeadException.class, () -> script.run());

    assertThat(output.toString()).isEqualTo("gr\r\nnod t\r\nnod Super\r\ngr\r\nx\r\n\r\n=x\r\n");
  }

  @Test
  public void run_invalidConfiguration_badUsername() {
    configuration = configuration.toBuilder().setLogOffCommand("=x\r\n").build();
    String text =
        "Your group currently consists of:\r\n"
            + "  t                              (L) [HE:100% ST:Ready]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n"
            + "t nodded to you in agreement!\r\n"
            + GREEN
            + "There is a grey robed priest here.\r\n";

    TAScript script = getScript(text);
    assertThrows(IllegalArgumentException.class, () -> script.run());

    assertThat(output.toString()).isEqualTo("gr\r\nnod t\r\nnod Super\r\n");
  }

  @Test
  public void validateConfiguration_criticalLessThanZero() {
    configuration = configuration.toBuilder().setCriticalPercentage(-0.01).build();
    assertThrows(
        IllegalArgumentException.class,
        () -> getScript("").validateConfiguration("Super Conductor"));
  }

  @Test
  public void validateConfiguration_criticalMoreThanOne() {
    configuration = configuration.toBuilder().setCriticalPercentage(1.01).build();
    assertThrows(
        IllegalArgumentException.class,
        () -> getScript("").validateConfiguration("Super Conductor"));
  }

  @Test
  public void validateConfiguration_healLessThanZero() {
    configuration = configuration.toBuilder().setHealSpell("motu").setHealPercentage(-0.01).build();
    assertThrows(
        IllegalArgumentException.class,
        () -> getScript("").validateConfiguration("Super Conductor"));
  }

  @Test
  public void validateConfiguration_healMoreThanOne() {
    configuration = configuration.toBuilder().setHealSpell("motu").setHealPercentage(1.01).build();
    assertThrows(
        IllegalArgumentException.class,
        () -> getScript("").validateConfiguration("Super Conductor"));
  }

  @Test
  public void validateConfiguration_bigHealLessThanZero() {
    configuration =
        configuration.toBuilder().setBigHealSpell("motu").setBigHealPercentage(-0.01).build();
    assertThrows(
        IllegalArgumentException.class,
        () -> getScript("").validateConfiguration("Super Conductor"));
  }

  @Test
  public void validateConfiguration_bigHealMoreThanSmallHeal() {
    configuration =
        configuration.toBuilder()
            .setBigHealSpell("motu")
            .setBigHealPercentage(0.8)
            .setHealPercentage(0.79)
            .build();
    assertThrows(
        IllegalArgumentException.class,
        () -> getScript("").validateConfiguration("Super Conductor"));
  }

  @Test
  public void validateConfiguration_groupHealLessThanZero() {
    configuration =
        configuration.toBuilder().setGroupHealSpell("motu").setGroupHealPercentage(-0.01).build();
    assertThrows(
        IllegalArgumentException.class,
        () -> getScript("").validateConfiguration("Super Conductor"));
  }

  @Test
  public void validateConfiguration_groupHealMoreThanOne() {
    configuration =
        configuration.toBuilder().setGroupHealSpell("motu").setGroupHealPercentage(1.01).build();
    assertThrows(
        IllegalArgumentException.class,
        () -> getScript("").validateConfiguration("Super Conductor"));
  }

  @Test
  public void validateConfiguration_noAttacksConfigured() {
    configuration =
        configuration.toBuilder()
            .setNumberOfPhysicalAttacks(0)
            .setAdditionalAttackCommand("")
            .build();
    assertThrows(
        IllegalArgumentException.class,
        () -> getScript("").validateConfiguration("Super Conductor"));
  }

  @Test
  public void validateConfiguration_healGroup_andAttacks_smallHeal() {
    configuration = configuration.toBuilder().setHealGroup(true).setHealSpellIsAttack(true).build();
    assertThrows(
        IllegalArgumentException.class,
        () -> getScript("").validateConfiguration("Super Conductor"));
  }

  @Test
  public void validateConfiguration_healGroup_andAttacks_bigHeal() {
    configuration =
        configuration.toBuilder().setHealGroup(true).setBigHealSpellIsAttack(true).build();
    assertThrows(
        IllegalArgumentException.class,
        () -> getScript("").validateConfiguration("Super Conductor"));
  }

  @Test
  public void trigger_publishes_to_output() {
    triggers =
        Triggers.of(
            ImmutableList.of(
                Trigger.newBuilder()
                    .setId(1)
                    .setTriggerRegex(".*Your Experience is (\\d+)\\..*")
                    .setCommand("Experience is $1 dude.\r\n")
                    .setExpectedColor(Color.RED)
                    .build()));

    String text =
        "Your group currently consists of:\r\n"
            + "  t                              (L) [HE:100% ST:Ready]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n"
            + "Super Conductor nodded to you in agreement!\r\n"
            + RED
            + "  Your Experience is 10003.\r\n"
            + GREEN
            + "There is a grey robed priest here.\r\n";

    TAScript script = getScript(text);
    assertThrows(DeadException.class, () -> script.run());

    assertThat(output.toString())
        .isEqualTo("gr\r\nnod t\r\nnod Super\r\ngr\r\nExperience is 10003 dude.\r\n\r\nx\r\n\r\n");
  }

  @Test
  public void getValueWorkingBackwards_normal() {
    assertThat(TAScript.getValueWorkingBackwards("abc123", 5)).isEqualTo(123);
  }

  @Test
  public void getValueWorkingBackwards_endsAtStart() {
    assertThat(TAScript.getValueWorkingBackwards("456123", 5)).isEqualTo(456123);
  }

  @Test
  public void getValueWorkingBackwards_from0() {
    assertThat(TAScript.getValueWorkingBackwards("456123", 0)).isEqualTo(4);
  }

  @Test
  public void getValueWorkingBackwards_percentage() {
    assertThat(TAScript.getValueWorkingBackwards("Testing 50%", 9)).isEqualTo(50);
  }

  @Test
  public void getValueWorkingBackwards_percentage_withPrefix() {
    assertThat(TAScript.getValueWorkingBackwards("Testing :100%", 11)).isEqualTo(100);
  }

  @Test
  public void checkHealth_groupHeal() {
    configuration =
        configuration.toBuilder()
            .setHealPercentage(0.9)
            .setBigHealPercentage(0.7)
            .setGroupHealPercentage(0.8)
            .setCriticalPercentage(0.5)
            .setLogOffCommand("=x\r\n")
            .setHealSpellIsAttack(false)
            .setBigHealSpellIsAttack(false)
            .setHealSpell("motu")
            .setBigHealSpell("kusamotu")
            .setGroupHealSpell("kusamotumaru")
            .build();

    String text =
        "Mana:         0 / 0\r\n"
            + "Vitality:     100 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Your group currently consists of:\r\n"
            + "  t                              (L) [HE: 79% ST:Ready]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     100 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Your group currently consists of:\r\n"
            + "  t                              (L) [HE: 81% ST:Ready]\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n";

    TAScript tascript = getScript(text);

    getScript(text).checkHealth();

    assertThat(output.toString()).isEqualTo("he\r\ngr\r\nc kusamotumaru\r\nhe\r\ngr\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void checkHealth_groupHeal_notEnoughMembers() {
    configuration =
        configuration.toBuilder()
            .setHealPercentage(0.9)
            .setBigHealPercentage(0.7)
            .setGroupHealPercentage(0.8)
            .setCriticalPercentage(0.5)
            .setLogOffCommand("=x\r\n")
            .setHealSpellIsAttack(false)
            .setBigHealSpellIsAttack(false)
            .setHealSpell("motu")
            .setBigHealSpell("kusamotu")
            .setGroupHealSpell("kusamotumaru")
            .build();

    String text =
        "Mana:         0 / 0\r\n"
            + "Vitality:     79 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Your group currently consists of:\r\n"
            + "  Super Conductor                (L) [HE: 79% ST:Ready]\r\n"
            + "\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     100 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Your group currently consists of:\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n";

    TAScript tascript = getScript(text);

    getScript(text).checkHealth();

    assertThat(output.toString()).isEqualTo("he\r\ngr\r\nc motu Super\r\nhe\r\ngr\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void checkHealth_groupHeal_individual() {
    configuration =
        configuration.toBuilder()
            .setHealPercentage(0.9)
            .setBigHealPercentage(0.7)
            .setGroupHealPercentage(0.8)
            .setCriticalPercentage(0.5)
            .setLogOffCommand("=x\r\n")
            .setHealSpellIsAttack(false)
            .setBigHealSpellIsAttack(false)
            .setHealGroup(true)
            .setHealSpell("fadi")
            .build();

    String text =
        "Mana:         0 / 0\r\n"
            + "Vitality:     79 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Your group currently consists of:\r\n"
            + "  Super Conductor                (L) [HE: 79% ST:Ready]\r\n"
            + "\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     100 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Your group currently consists of:\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "\r\n";

    TAScript tascript = getScript(text);

    getScript(text).checkHealth();

    assertThat(output.toString()).isEqualTo("he\r\ngr\r\nc fadi Super\r\nhe\r\ngr\r\n");
    verify(sleeper).sleep(any());
  }

  @Test
  public void checkHealth_groupHeal_healsEachPerson() {
    configuration =
        configuration.toBuilder()
            .setHealDuringBattle(false)
            .setHealPercentage(0.9)
            .setBigHealPercentage(0.7)
            .setGroupHealPercentage(0.8)
            .setCriticalPercentage(0.5)
            .setLogOffCommand("=x\r\n")
            .setHealSpellIsAttack(false)
            .setBigHealSpellIsAttack(false)
            .setHealGroup(true)
            .setHealSpell("fadi")
            .build();

    String text =
        "Mana:         0 / 0\r\n"
            + "Vitality:     79 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Your group currently consists of:\r\n"
            + "  Super Conductor                (L) [HE: 79% ST:Ready]\r\n"
            + "  Paladine                       (L) [HE: 78% ST:Ready]\r\n"
            + "  Fisty                          (L) [HE: 77% ST:Ready]\r\n"
            + "\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     100 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Your group currently consists of:\r\n"
            + "  Super Conductor                (L) [HE: 79% ST:Ready]\r\n"
            + "  Paladine                       (L) [HE: 78% ST:Ready]\r\n"
            + "  Fisty                          (L) [HE:100% ST:Ready]\r\n"
            + "\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     100 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Your group currently consists of:\r\n"
            + "  Super Conductor                (L) [HE: 79% ST:Ready]\r\n"
            + "  Paladine                       (L) [HE:100% ST:Ready]\r\n"
            + "  Fisty                          (L) [HE:100% ST:Ready]\r\n"
            + "\r\n"
            + "Mana:         0 / 0\r\n"
            + "Vitality:     100 / 100\r\n"
            + "Status:       Healthy\r\n"
            + "Your group currently consists of:\r\n"
            + "  Super Conductor                (L) [HE:100% ST:Ready]\r\n"
            + "  Paladine                       (L) [HE:100% ST:Ready]\r\n"
            + "  Fisty                          (L) [HE:100% ST:Ready]\r\n"
            + "\r\n";

    TAScript tascript = getScript(text);

    getScript(text).checkHealth();

    assertThat(output.toString())
        .isEqualTo(
            "he\r\n"
                + "gr\r\n"
                + "c fadi Fisty\r\n"
                + "he\r\n"
                + "gr\r\n"
                + "c fadi Paladine\r\n"
                + "he\r\n"
                + "gr\r\n"
                + "c fadi Super\r\n"
                + "he\r\n"
                + "gr\r\n");
    verify(sleeper, times(3)).sleep(any());
  }

  private TAScript getScript(String input) {
    Scanner scanner = new Scanner(new ByteArrayInputStream(input.getBytes(UTF_8)));

    lineSupplier = timeout -> scanner.nextLine();

    return tascript.get();
  }
}
