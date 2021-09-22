package com.jeffreys.scripts.tascript;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.jeffreys.common.ansi.AnsiColorParser;
import com.jeffreys.common.ansi.AnsiColorParser.AnsiCharacterAttribute;
import com.jeffreys.common.ansi.AnsiColorParser.AnsiColor;
import com.jeffreys.common.ansi.AnsiColorParser.ParsedAnsiText;
import com.jeffreys.common.queue.NonBlockingSupplier;
import com.jeffreys.scripts.common.Triggers;
import com.jeffreys.scripts.tascript.Annotations.LogfilePrintWriter;
import com.jeffreys.scripts.tascript.Annotations.OutputPrintWriter;
import java.io.PrintWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.inject.Inject;

public class TAScript {
  private static final int ST_INDEX = 46;

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration ATTACK_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration HEAL_TIMEOUT = Duration.ofSeconds(3);

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults();
  private static final Splitter SPACE_SPLITTER = Splitter.on(' ').trimResults().omitEmptyStrings();
  private static final GetTargetResult EMPTY_TARGET_RESULT =
      GetTargetResult.create(/* target= */ null, /* count= */ 0, /* isPlayer= */ false);
  private static final ImmutableMap<String, Integer> TEXT_TO_COUNT =
      ImmutableMap.<String, Integer>builder()
          .put("a", 1)
          .put("an", 1)
          .put("two", 2)
          .put("three", 3)
          .put("four", 4)
          .put("five", 5)
          .put("six", 6)
          .put("seven", 7)
          .put("eight", 8)
          .put("nine", 9)
          .put("ten", 10)
          .build();

  private final NonBlockingSupplier<String> lineSupplier;
  private final PrintWriter output;
  private final AnsiColorParser ansiColorParser = new AnsiColorParser();
  private final Configuration configuration;
  private final Movements movements;
  private final Triggers triggers;
  private final HashSet<String> playersToAttack = new LinkedHashSet<>();
  private final Clock clock;
  private final PrintWriter logFile;
  private final Sleeper sleeper;
  private final Duration movePauseDuration;
  private final Set<String> groupMembers = new HashSet<>();
  private final Stats[] stats = new Stats[] {new Stats(), new Stats()};

  private String adjustedUsername;
  private boolean needsYari = false;

  static class ResumeableException extends RuntimeException {
    private ResumeableException(String msg) {
      super(msg);
    }
  }

  @VisibleForTesting
  static class AttackException extends ResumeableException {
    private AttackException(String msg) {
      super(msg);
    }
  }

  @VisibleForTesting
  static class ArrivalException extends ResumeableException {
    private ArrivalException(String msg) {
      super(msg);
    }
  }

  @VisibleForTesting
  static class LogoffException extends RuntimeException {
    private LogoffException() {
      super();
    }
  }

  @VisibleForTesting
  static class DeadException extends RuntimeException {
    private DeadException(String reason) {
      super(reason);
    }
  }

  static class InterruptedRuntimeException extends RuntimeException {
    private InterruptedRuntimeException(InterruptedException ex) {
      super(ex);
    }
  }

  @VisibleForTesting
  enum AttackType {
    Physical,
    Spell
  }

  @Inject
  TAScript(
      NonBlockingSupplier<String> lineSupplier,
      @OutputPrintWriter PrintWriter output,
      Configuration configuration,
      Movements movements,
      Triggers triggers,
      Clock clock,
      @LogfilePrintWriter PrintWriter logFile,
      Sleeper sleeper) {
    this.lineSupplier = lineSupplier;
    this.output = output;
    this.configuration = configuration;
    this.movements = movements;
    this.triggers = triggers;
    this.clock = clock;
    this.adjustedUsername = getFirstWord(configuration.getUsername());
    this.logFile = logFile;
    this.sleeper = sleeper;
    this.movePauseDuration = Duration.ofMillis(configuration.getMovePauseMilliseconds());

    playersToAttack.addAll(configuration.getPlayersToAttackList());
  }

  void run() {
    String username = getUsername();
    logger.atInfo().log("Found user [%s]", username);

    validateConfiguration(username);

    logger.atInfo().log("Starting script with configuration: \n%s", configuration);

    try {
      mainLoop();
    } catch (Throwable t) {
      output.print("x\r\n\r\n");
      output.flush();
      sendLogoffCommand();
      throw t;
    }
  }

  private void move() {
    sleeper.sleep(movePauseDuration);

    output.printf("%s\r\n", movements.getNextMovement());
    output.flush();

    // TODO get targets, just in case, and ignore it since we refresh anyways
    // getTargets();
  }

  private void mainLoop() {
    getGroupStatus();

    while (true) {
      attackTargets();

      try {
        checkHealth();

        waitUntilReady();

        move();
      } catch (ResumeableException ex) {
        logger.atWarning().withCause(ex).log("Exception in non-attacking loop");
      }
    }
  }

  @AutoValue
  abstract static class GetTargetResult {
    @Nullable
    abstract String getTarget();

    abstract int getCount();

    abstract boolean isPlayer();

    static GetTargetResult create(@Nullable String target, int count, boolean isPlayer) {
      if (target != null) {
        checkArgument(count > 0, "count == 0 when there is a valid target");
      } else {
        checkArgument(count == 0, "count is non-zero and there's a null target");
      }

      return new AutoValue_TAScript_GetTargetResult(target, count, isPlayer);
    }
  }

  /** Handles the case like 'Rush, Dude, and Todd are here' */
  private static ImmutableSet<String> parseCommaSeparatedMultiplePlayers(String line) {
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    Iterator<String> strings = COMMA_SPLITTER.split(line).iterator();
    while (strings.hasNext()) {
      String player = strings.next();

      if (strings.hasNext()) {
        builder.add(player);
        continue;
      }

      // strip off 'and ' prefix
      player = player.substring(4);
      // strip off ' are here.' suffix
      int idx = player.lastIndexOf(" are here");
      if (idx > 0) {
        player = player.substring(0, idx);
      }

      builder.add(player);
    }
    return builder.build();
  }

  /** Handles the case like 'Todd and Rush are here' */
  private static ImmutableSet<String> parseTwoSeparatedMultiplePlayers(String line) {
    int idx = line.indexOf(" and ");
    String first = line.substring(0, idx);
    int endIdx = line.lastIndexOf(" are here");
    String second = line.substring(idx + 5, endIdx);

    return ImmutableSet.of(first, second);
  }

  private static ImmutableSet<String> parseSinglePlayerInRoom(String line) {
    int idx = line.indexOf(" is here");
    return ImmutableSet.of(line.substring(0, idx));
  }

  @VisibleForTesting
  static ImmutableSet<String> getPlayersInRoom(ParsedAnsiText line) {
    if (line.getText().indexOf(',') > 0) {
      return parseCommaSeparatedMultiplePlayers(line.getText());
    } else if (line.getText().contains(" is here")) {
      return parseSinglePlayerInRoom(line.getText());
    } else if (line.getText().contains(" and ") && line.getText().contains(" are here")) {
      return parseTwoSeparatedMultiplePlayers(line.getText());
    }

    return ImmutableSet.of();
  }

  /**
   * Adds the monsters in {@code words} to the {@code map}.
   *
   * <p>
   */
  private static void parseMonstersIntoConsumer(
      List<String> words, int countIndex, int lastIndex, BiConsumer<String, Integer> consumer) {
    Integer count = TEXT_TO_COUNT.get(words.get(countIndex));
    if (count == null) {
      // something went wrong, so handle gracefully
      logger.atWarning().log("Found weird shit: %d:%d:%s", countIndex, lastIndex, words);
      return;
    }
    String monster = removePluralSuffix(words.get(lastIndex));
    consumer.accept(monster, count);
  }

  /**
   * Parses monsters in {@code text} into a map.
   *
   * <p>There is a cave bear, a female orc, and a lizard woman here.
   */
  private static ImmutableMap<String, Integer> parseCommaSeparatedMultipleMonsters(String text) {
    Map<String, Integer> map = new LinkedHashMap<>();
    List<String> monsterGroups = COMMA_SPLITTER.splitToList(text);
    BiConsumer<String, Integer> consumer =
        (monster, count) ->
            map.compute(
                monster,
                (mapMonster, mapCount) -> {
                  if (mapCount != null) {
                    return count + mapCount;
                  }
                  return count;
                });

    // parse the first section, skip over "there is" or "there are"
    List<String> words = SPACE_SPLITTER.splitToList(monsterGroups.get(0));
    parseMonstersIntoConsumer(
        words, /* countIndex= */ 2, /* lastIndex= */ words.size() - 1, consumer);

    // the ones in the middle is just raw monsters
    for (int i = 1; i < monsterGroups.size() - 1; ++i) {
      words = SPACE_SPLITTER.splitToList(monsterGroups.get(i));
      parseMonstersIntoConsumer(
          words, /* countIndex= */ 0, /* lastIndex= */ words.size() - 1, consumer);
    }

    // the last one starts with "and" and ends with "here"
    words = SPACE_SPLITTER.splitToList(monsterGroups.get(monsterGroups.size() - 1));
    parseMonstersIntoConsumer(
        words, /* countIndex= */ 1, /* lastIndex= */ words.size() - 2, consumer);
    return ImmutableMap.copyOf(map);
  }

  private static ImmutableMap<String, Integer> parseSingleMonsterInRoom(String text) {
    ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();
    List<String> words = SPACE_SPLITTER.splitToList(text);
    parseMonstersIntoConsumer(
        words, /* countIndex= */ 2, /* lastIndex= */ words.size() - 2, builder::put);
    return builder.build();
  }

  @VisibleForTesting
  static ImmutableMap<String, Integer> getMonstersInRoom(ParsedAnsiText line) {
    if (line.getText().indexOf(',') > 0) {
      return parseCommaSeparatedMultipleMonsters(line.getText());
    }

    return parseSingleMonsterInRoom(line.getText());
  }

  private class GetTargetSendUntilHandler implements SendUntilHandler {
    private final StringBuilder monsterBuilder = new StringBuilder(/* capacity= */ 256);
    private String target = null;
    private int count = 0;
    private boolean isPlayer = false;
    private AnsiCharacterAttribute firstAttribute;
    private GetTargetResult result;

    @Override
    public boolean onLineReceived(ParsedAnsiText line) {
      // TODO handle case where we can't see

      // check if we are at the end
      if (line.getFirstAttributeOrDefault().getForegroundColor().equals(AnsiColor.CYAN)
          && line.getText().contains("floor")) {
        return setResult(GetTargetResult.create(target, count, isPlayer));
      }

      if (line.getFirstAttributeOrDefault().getForegroundColor().equals(AnsiColor.MAGENTA)) {
        ImmutableSet<String> players = getPlayersInRoom(line);
        Set<String> intersection = Sets.intersection(playersToAttack, players);
        if (!intersection.isEmpty()) {
          // attack the player now
          target = getFirstWord(Iterables.getFirst(intersection, ""));
          count += intersection.size();
          isPlayer = true;
        }
      }

      if (line.getFirstAttributeOrDefault().getForegroundColor().equals(AnsiColor.RED)) {
        parseRedMonsterLine(line);
      }

      return false;
    }

    private void parseRedMonsterLine(ParsedAnsiText line) {
      boolean lineStartsWithThere = line.getText().startsWith("There");
      boolean lineEndsWithHere = line.getText().endsWith("here.");

      // if broken over multiple lines, use the builder and add to it
      // Accumulate on two conditions:
      //
      // 1) we're already accumulating
      // 2) Found a line starting with "There" but not ending with "here.", that
      //    starts accumulation
      if (monsterBuilder.length() > 0 || (lineStartsWithThere && !lineEndsWithHere)) {
        if (monsterBuilder.length() == 0) {
          firstAttribute = line.getFirstAttributeOrDefault();
        } else {
          monsterBuilder.append(' ');
        }
        monsterBuilder.append(line.getText());
      }

      if (lineEndsWithHere) {
        ParsedAnsiText lineToParse;
        if (lineStartsWithThere) {
          // contained all within one line, optimize and skip the builder
          lineToParse = line;
        } else {
          // synthesize a new line that concatenates all
          lineToParse = ParsedAnsiText.create(monsterBuilder.toString(), firstAttribute);
        }

        ImmutableMap<String, Integer> monsters = getMonstersInRoom(lineToParse);
        count += monsters.values().stream().mapToInt(Integer::intValue).sum();
        if (!monsters.isEmpty() && target == null) {
          target = Iterables.getFirst(monsters.keySet(), "");
        }
      }
    }

    @Override
    public void onResend() {
      target = null;
      count = 0;
      isPlayer = false;
      // this clears the builder
      monsterBuilder.setLength(0);
    }

    @Override
    public void handleException(RuntimeException ex) {
      if (ex instanceof ResumeableException) {
        // ignore it
        logger.atWarning().withCause(ex).log("ResumeableException while retrieving monsters");
        return;
      }

      throw ex;
    }

    private boolean setResult(GetTargetResult result) {
      checkState(this.result == null, "setResult called twice");

      this.result = result;
      return true;
    }

    GetTargetResult getResult() {
      return result;
    }
  }

  @VisibleForTesting
  GetTargetResult getTargets() {
    GetTargetSendUntilHandler getTargetSendUntilHandler = new GetTargetSendUntilHandler();

    sendUntil("\r\n", getTargetSendUntilHandler);

    return getTargetSendUntilHandler.getResult();
  }

  @AutoValue
  abstract static class GroupMemberStatus {
    abstract ImmutableSet<String> getGroupMembers();

    abstract boolean youAreReady();

    abstract boolean allAreReady();

    /** Returns the lowest health percentage, should be between 0.0 and 1.0 (full health). */
    abstract double getLowestHealthPercentage();

    /** Returns the group member with the lowest health percentage. */
    abstract String getLowestHealthMember();

    int getGroupSize() {
      return getGroupMembers().size();
    }

    static GroupMemberStatus create(
        Collection<String> groupMembers,
        boolean youAreAready,
        boolean allAreReady,
        double lowestHealthPercentage,
        String lowestHealthMember) {
      return new AutoValue_TAScript_GroupMemberStatus(
          ImmutableSet.copyOf(groupMembers),
          youAreAready,
          allAreReady,
          lowestHealthPercentage,
          lowestHealthMember);
    }
  }

  @VisibleForTesting
  static int getValueWorkingBackwards(String string, int endIndex) {
    int startIndex = endIndex;
    while (startIndex > 0 && Character.isDigit(string.charAt(startIndex - 1))) {
      startIndex--;
    }

    int value = 0;
    for (int i = startIndex; i <= endIndex; ++i) {
      int digit = Character.digit(string.charAt(i), /* radix= */ 10);
      value = 10 * value + digit;
    }
    return value;
  }

  private class GroupMemberStatusSendUntilHandler implements SendUntilHandler {
    private int numReady = 0;
    private boolean youAreReady = false;
    private boolean foundBeginning = false;
    private double lowestHealthPercentage = 100.0;
    private String lowestHealthMember = null;
    private ImmutableSet.Builder<String> groupMembersBuilder = ImmutableSet.builder();
    private GroupMemberStatus groupMemberStatus;

    @Override
    public void onResend() {
      numReady = 0;
      youAreReady = false;
      foundBeginning = false;
      lowestHealthPercentage = 100.0;
      lowestHealthMember = null;
      groupMembersBuilder = ImmutableSet.builder();
    }

    @Override
    public boolean onLineReceived(ParsedAnsiText line) {
      if (!foundBeginning) {
        if (line.getText().startsWith("Your group currently consists of:")) {
          foundBeginning = true;
        }
        return false;
      }

      if (line.getText().isEmpty()) {
        ImmutableSet<String> groupMembers = groupMembersBuilder.build();
        boolean allAreReady = (numReady == groupMembers.size());
        return setResult(
            GroupMemberStatus.create(
                groupMembers,
                youAreReady || allAreReady,
                allAreReady,
                lowestHealthPercentage,
                lowestHealthMember));
      } else if (line.getText().lastIndexOf("ST:", ST_INDEX) == ST_INDEX) {
        String username = getUsernameFromSTLine(line.getText());
        // add them to our list
        groupMembersBuilder.add(username);

        // if they're ready, increment ready count
        if (line.getText().lastIndexOf("Ready", 49) == 49) {
          ++numReady;

          if (getFirstWord(username).equals(adjustedUsername)) {
            youAreReady = true;
          }
        }

        int percentIndex = line.getText().lastIndexOf('%');
        if (percentIndex > 0) {
          double value =
              (double) getValueWorkingBackwards(line.getText(), percentIndex - 1) / 100.0;
          if (value < lowestHealthPercentage) {
            lowestHealthPercentage = value;
            lowestHealthMember = username;
          }
        }
      }
      return false;
    }

    private boolean setResult(GroupMemberStatus status) {
      this.groupMemberStatus = status;
      return true;
    }

    GroupMemberStatus getResult() {
      return groupMemberStatus;
    }
  }

  @VisibleForTesting
  GroupMemberStatus getGroupStatus() {
    GroupMemberStatusSendUntilHandler handler = new GroupMemberStatusSendUntilHandler();
    sendUntil("gr\r\n", handler);

    groupMembers.clear();
    groupMembers.addAll(handler.getResult().getGroupMembers());

    return handler.getResult();
  }

  private interface SendUntilHandler {
    boolean onLineReceived(ParsedAnsiText line);

    default void onResend() {}

    default void handleException(RuntimeException ex) {
      throw ex;
    }
  }

  private void sendUntil(String message, SendUntilHandler handler, Duration timeout) {
    output.print(message);
    output.flush();

    Instant repeatTime = clock.instant().plus(timeout);

    while (true) {
      try {
        ParsedAnsiText line = getNextLine();
        if (line != null && handler.onLineReceived(line)) {
          return;
        }
      } catch (RuntimeException ex) {
        handler.handleException(ex);
      }

      // repeat if nothing has happened
      Instant now = clock.instant();
      if (now.isAfter(repeatTime)) {
        output.print(message);
        output.flush();

        handler.onResend();

        repeatTime = now.plus(timeout);
      }
    }
  }

  private void sendUntil(String message, SendUntilHandler handler) {
    sendUntil(message, handler, DEFAULT_TIMEOUT);
  }

  private void removeFromGroup(String player) {
    output.printf("remove %s\r\n", player);
    output.flush();
  }

  private void attack(GetTargetResult targets) {
    String attackString = String.format("a %s\r\n", targets.getTarget());
    for (int i = 0; i < configuration.getNumberOfPhysicalAttacks(); ++i) {
      output.print(attackString);
    }

    output.flush();
  }

  private void performAdditionalAttackCommands(GetTargetResult targets) {
    String additionalCommand = null;
    if (targets.getCount() > 1 && !configuration.getAdditionalGroupAttackCommand().isEmpty()) {
      additionalCommand = configuration.getAdditionalGroupAttackCommand();
    } else if (!configuration.getAdditionalAttackCommand().isEmpty()) {
      additionalCommand = configuration.getAdditionalAttackCommand();
    }

    if (additionalCommand != null) {
      output.printf(
          "%s\r\n",
          additionalCommand.replace(
              "$1", Optional.ofNullable(targets.getTarget()).orElse("NO_TARGET")));
    }
  }

  @AutoValue
  abstract static class PlayerStatus {
    abstract int getVitality();

    abstract int getMaxVitality();

    abstract int getMana();

    abstract int getMaxMana();

    abstract boolean isHungry();

    abstract boolean isThirsty();

    abstract Optional<GroupMemberStatus> getGroupMemberStatus();

    static Builder builder() {
      return new AutoValue_TAScript_PlayerStatus.Builder()
          .setHungry(false)
          .setThirsty(false)
          .setGroupMemberStatus(Optional.empty());
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setVitality(int vitality);

      abstract Builder setMaxVitality(int maxVitality);

      abstract Builder setMana(int mana);

      abstract Builder setMaxMana(int maxMana);

      abstract Builder setHungry(boolean isHungry);

      abstract Builder setThirsty(boolean isThirsty);

      abstract Builder setGroupMemberStatus(Optional<GroupMemberStatus> groupMemberStatus);

      Builder setGroupMemberStatus(GroupMemberStatus groupMemberStatus) {
        return setGroupMemberStatus(Optional.of(groupMemberStatus));
      }

      abstract PlayerStatus build();
    }
  }

  private static class PlayerStatusSendUntilHandler implements SendUntilHandler {
    private final PlayerStatus.Builder builder = PlayerStatus.builder();
    private boolean hasMana = false;
    private boolean hasVitality = false;

    private PlayerStatus.Builder getPlayerStatusBuilder() {
      return builder;
    }

    @Override
    public boolean onLineReceived(ParsedAnsiText line) {
      if (line.getText().startsWith("Mana:")) {
        List<String> words = SPACE_SPLITTER.splitToList(line.getText());
        builder.setMana(Integer.parseInt(words.get(1)));
        builder.setMaxMana(Integer.parseInt(words.get(3)));
        hasMana = true;
      } else if (line.getText().startsWith("Vitality:")) {
        List<String> words = SPACE_SPLITTER.splitToList(line.getText());
        builder.setVitality(Integer.parseInt(words.get(1)));
        builder.setMaxVitality(Integer.parseInt(words.get(3)));
        hasVitality = true;
      } else if (line.getText().startsWith("Status:")) {
        List<String> words = SPACE_SPLITTER.splitToList(line.getText());
        if (words.get(1).equals("Hungry")) {
          builder.setHungry(true);
        } else if (words.get(1).equals("Thirsty")) {
          builder.setThirsty(true);
        }
        return hasMana && hasVitality;
      }
      return false;
    }
  }

  @VisibleForTesting
  PlayerStatus getPlayerStatus() {
    PlayerStatusSendUntilHandler playerStatusSendUntilHandler = new PlayerStatusSendUntilHandler();

    sendUntil("he\r\n", playerStatusSendUntilHandler);

    PlayerStatus.Builder builder = playerStatusSendUntilHandler.getPlayerStatusBuilder();

    if (!configuration.getGroupHealSpell().isEmpty() || configuration.getHealGroup()) {
      builder.setGroupMemberStatus(getGroupStatus());
    }

    return builder.build();
  }

  private PlayerStatus getPlayerStatusIgnoringResumeableExceptions() {
    while (true) {
      try {
        return getPlayerStatus();
      } catch (ResumeableException ex) {
        // ignore
      }
    }
  }

  @VisibleForTesting
  void attackTargets() {
    while (true) {
      GetTargetResult targetResults = getTargets();
      if (targetResults.getCount() <= 0) {
        return;
      }

      // if they are a player, remove them from our group
      if (targetResults.isPlayer()) {
        logger.atInfo().log("Attacking %s", targetResults.getTarget());
        removeFromGroup(targetResults.getTarget());
      }

      // attack
      attack(targetResults);

      PlayerStatus playerStatus = getPlayerStatusIgnoringResumeableExceptions();
      heal(playerStatus, targetResults, HealScenario.InBattle);

      if (playerStatus.getMana() >= configuration.getMinimumAttackMana()) {
        castAttackSpell(targetResults);
      }

      performAdditionalAttackCommands(targetResults);

      sleeper.sleep(ATTACK_TIMEOUT);
    }
  }

  private boolean shouldHealDuringBattle() {
    return configuration.getHealDuringBattle()
        || (!configuration.getBigHealSpell().isEmpty() && configuration.getBigHealSpellIsAttack())
        || (!configuration.getHealSpell().isEmpty() && configuration.getHealSpellIsAttack());
  }

  private enum HealScenario {
    InBattle,
    PostBattle,
  }

  private boolean heal(PlayerStatus playerStatus) {
    return heal(playerStatus, EMPTY_TARGET_RESULT, HealScenario.PostBattle);
  }

  private boolean heal(
      PlayerStatus playerStatus, GetTargetResult targetResults, HealScenario healScenario) {
    double healthRatio =
        (double) playerStatus.getVitality() / (double) playerStatus.getMaxVitality();

    if (healthRatio <= configuration.getCriticalPercentage()) {
      logoff();
      return false;
    }

    if (healScenario.equals(HealScenario.InBattle) && !shouldHealDuringBattle()) {
      return false;
    }

    boolean canCastGroupHeal =
        !configuration.getGroupHealSpell().isEmpty()
            && playerStatus.getGroupMemberStatus().map(GroupMemberStatus::getGroupSize).orElse(1)
                > 1;
    if (canCastGroupHeal
        && playerStatus
                .getGroupMemberStatus()
                .map(GroupMemberStatus::getLowestHealthPercentage)
                .orElse(1.0)
            <= configuration.getGroupHealPercentage()) {
      castGroupHeal();
      return true;
    }

    String recipient;
    if (configuration.getHealGroup() && playerStatus.getGroupMemberStatus().isPresent()) {
      recipient = getFirstWord(playerStatus.getGroupMemberStatus().get().getLowestHealthMember());
      healthRatio = playerStatus.getGroupMemberStatus().get().getLowestHealthPercentage();
    } else {
      recipient = adjustedUsername;
    }

    boolean canCastBigHeal =
        !configuration.getBigHealSpell().isEmpty()
            && (!configuration.getBigHealSpellIsAttack() || targetResults.getCount() > 0);
    boolean canCastHeal =
        !configuration.getHealSpell().isEmpty()
            && (!configuration.getHealSpellIsAttack() || targetResults.getCount() > 0);

    if (canCastBigHeal && healthRatio <= configuration.getBigHealPercentage()) {
      castHeal(
          targetResults.getTarget(),
          recipient,
          configuration.getBigHealSpell(),
          configuration.getBigHealSpellIsAttack());
      return true;
    } else if (canCastHeal && healthRatio <= configuration.getHealPercentage()) {
      castHeal(
          targetResults.getTarget(),
          recipient,
          configuration.getHealSpell(),
          configuration.getHealSpellIsAttack());
      return true;
    }
    return false;
  }

  @VisibleForTesting
  void checkHealth() {
    while (true) {
      PlayerStatus playerStatus = getPlayerStatus();
      if (!heal(playerStatus)) {
        return;
      }

      sleeper.sleep(HEAL_TIMEOUT);
    }
  }

  private void castYari() {
    output.printf("c yari %s\r\n", adjustedUsername);
    output.flush();
  }

  private void doSustenanceCommand() {
    output.printf("%s\r\n", configuration.getSustenanceCommand());
    output.flush();
  }
  /**
   * Performs any necessary actions before you move.
   *
   * <p>Returns true if you did an action that requires rest.
   */
  private boolean doPreMoveActions() {
    if (needsYari && configuration.getHasYari()) {
      castYari();

      needsYari = false;
      return true;
    }

    return false;
  }

  @VisibleForTesting
  void waitUntilReady() {
    while (true) {
      GroupMemberStatus groupMemberStatus = getGroupStatus();

      if (groupMemberStatus.youAreReady()) {
        if (doPreMoveActions()) {
          continue;
        }
      }

      boolean ready =
          configuration.getWaitForAllMembers()
              ? groupMemberStatus.allAreReady()
              : groupMemberStatus.youAreReady();

      if (ready) {
        PlayerStatus playerStatus = getPlayerStatus();

        if (!configuration.getSustenanceCommand().isEmpty()
            && (playerStatus.isHungry() || playerStatus.isThirsty())) {
          doSustenanceCommand();
          continue;
        }

        if (playerStatus.getMana() >= configuration.getMinimumMoveMana()) {
          return;
        }
      }

      sleeper.sleep(DEFAULT_TIMEOUT);
    }
  }

  private void sendLogoffCommand() {
    output.print(configuration.getLogOffCommand());
    output.flush();
  }

  private void logoff() {
    sendLogoffCommand();
    throw new LogoffException();
  }

  private void castGroupHeal() {
    checkArgument(!configuration.getGroupHealSpell().isEmpty());

    output.printf("c %s\r\n", configuration.getGroupHealSpell());
    output.flush();
  }

  private void castHeal(
      String target, String recipient, String healSpell, boolean healSpellIsAttack) {
    checkArgument(!healSpell.isEmpty());
    checkArgument(!healSpellIsAttack || target != null);

    if (healSpellIsAttack) {
      output.printf("c %s %s\r\n", healSpell, target);
    } else {
      output.printf("c %s %s\r\n", healSpell, recipient);
    }
    output.flush();
  }

  private void castAttackSpell(GetTargetResult target) {
    if (!configuration.getGroupAttackSpell().isEmpty()
        && (target.getCount() > 1 || configuration.getAttackSpell().isEmpty())) {
      output.printf("c %s\r\n", configuration.getGroupAttackSpell());
      output.flush();
    } else if (!configuration.getAttackSpell().isEmpty()) {
      output.printf("c %s %s\r\n", configuration.getAttackSpell(), target.getTarget());
      output.flush();
    }
  }

  @VisibleForTesting
  void validateConfiguration(String detectedUsername) {
    if (configuration.getUsername().isEmpty()) {
      throw new IllegalArgumentException("No name given");
    }

    if (!configuration.getUsername().equals(detectedUsername)) {
      throw new IllegalArgumentException(
          String.format(
              "Username doesn't match detected username [%s:%d]:[%s:%d]",
              configuration.getUsername(),
              configuration.getUsername().length(),
              detectedUsername,
              detectedUsername.length()));
    }

    adjustedUsername = getFirstWord(detectedUsername);
    if (adjustedUsername.isEmpty()) {
      throw new IllegalArgumentException("AdjustedUsername is empty");
    }

    if (configuration.getCriticalPercentage() < 0.0
        || configuration.getCriticalPercentage() > 1.0) {
      throw new IllegalArgumentException("Critical percentage is not in range");
    }

    if (!configuration.getHealSpell().isEmpty()
        && (configuration.getHealPercentage() < 0.0 || configuration.getHealPercentage() > 1.0)) {
      throw new IllegalArgumentException("Heal percentage is not in range");
    }

    if (!configuration.getBigHealSpell().isEmpty()
        && (configuration.getBigHealPercentage() < 0.0
            || configuration.getBigHealPercentage() > configuration.getHealPercentage())) {
      throw new IllegalArgumentException("Big heal percentage is not in range");
    }

    if (!configuration.getGroupHealSpell().isEmpty()
        && (configuration.getGroupHealPercentage() < 0.0
            || configuration.getGroupHealPercentage() > 1.0)) {
      throw new IllegalArgumentException("Group heal percentage is not in range");
    }

    if (configuration.getNumberOfPhysicalAttacks() <= 0
        && configuration.getAdditionalAttackCommand().isEmpty()) {
      throw new IllegalArgumentException("You have no attack configured");
    }

    if (configuration.getHealGroup()
        && (configuration.getHealSpellIsAttack() || configuration.getBigHealSpellIsAttack())) {
      throw new IllegalArgumentException("Can't set heal_group and have attacking heal spells");
    }
  }

  private static boolean isCommandLine(ParsedAnsiText line) {
    // TODO implement
    return false;
  }

  private boolean isArrivedLine(ParsedAnsiText line) {
    return line.getText().contains(" has just arrived from ");
  }

  private boolean isAttackLine(ParsedAnsiText line) {
    if (!line.getFirstAttributeOrDefault().getForegroundColor().equals(AnsiColor.RED)) {
      return false;
    }

    int i = line.getText().indexOf(" attacked you with ");
    if (i > 0) {
      if (!line.getText().startsWith("The ")) {
        String player = line.getText().substring(0, i);
        addPlayerToAttack(player);
        return true;
      }
    }

    i = line.getText().indexOf(" discharged ");
    if (i > 0) {
      if (!line.getText().startsWith("The ")) {
        int j = line.getText().indexOf(" just discharged ");
        boolean isAreaSpell = j > 0;
        String player = line.getText().substring(0, isAreaSpell ? j : i);
        // if directly attacked or area attacked by a non-group member, aggro on them
        if (!isAreaSpell || !groupMembers.contains(player)) {
          addPlayerToAttack(player);
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isDeadLine(ParsedAnsiText line) {
    return line.getFirstAttributeOrDefault().getForegroundColor().equals(AnsiColor.GREEN)
        && line.getText().contains("There is a grey robed priest here.");
  }

  private boolean isDamageLine(ParsedAnsiText line) {
    AttackType attackType;
    if (line.getText().startsWith("Your attack hit")
        || line.getText().startsWith("Your skillful attack hit")) {
      attackType = AttackType.Physical;
    } else if (line.getText().startsWith("You discharged the spell")) {
      attackType = AttackType.Spell;
    } else {
      return false;
    }

    Iterator<String> iterator = SPACE_SPLITTER.split(line.getText()).iterator();
    while (iterator.hasNext()) {
      String word = iterator.next();
      if (word.equals("for")) {
        int damage = Integer.parseInt(iterator.next());
        stats[attackType.ordinal()].addDamage(damage);
      }
    }
    return true;
  }

  private boolean isJoinLine(ParsedAnsiText line) {
    int i = line.getText().indexOf(" is asking to join your group");
    if (i < 0) {
      return false;
    }

    String user = line.getText().substring(0, i);
    // you're on the shit list, no joining for you
    if (playersToAttack.contains(user)) {
      return true;
    }

    addGroupMember(user);

    output.printf("add %s\r\n", getFirstWord(user));
    output.flush();
    return true;
  }

  private boolean isLeaveLine(ParsedAnsiText line) {
    int i = line.getText().indexOf(" has just left ");
    if (i < 0) {
      return false;
    }

    String user = line.getText().substring(0, i);
    groupMembers.remove(user);
    return true;
  }

  private boolean isNeedsYariLine(ParsedAnsiText line) {
    return line.getFirstAttributeOrDefault().getForegroundColor().equals(AnsiColor.BLUE)
        && line.getText().equals("You suddenly feel very vulnerable!");
  }

  private boolean preprocessLine(ParsedAnsiText parsedAnsiText, String rawLine) {
    logFile.println(rawLine);
    logFile.flush();

    triggers.processLine(parsedAnsiText, (id, command) -> output.printf("%s\r\n", command));

    if (isCommandLine(parsedAnsiText)) {
      return true;
    } else if (isAttackLine(parsedAnsiText)) {
      throw new AttackException("You've been attacked!");
    } else if (isArrivedLine(parsedAnsiText)) {
      throw new ArrivalException("Something has arrived");
    } else if (isDeadLine(parsedAnsiText)) {
      throw new DeadException("You're dead!");
    } else if (isDamageLine(parsedAnsiText)) {
      return true;
    } else if (isJoinLine(parsedAnsiText)) {
      return true;
    } else if (isLeaveLine(parsedAnsiText)) {
      return true;
    } else if (isNeedsYariLine(parsedAnsiText)) {
      needsYari = true;
      return true;
    }

    return false;
  }

  private static String getUsernameFromSTLine(String line) {
    return line.substring(2, 32).trim();
  }

  private static String getFirstWord(String line) {
    return Iterables.getFirst(SPACE_SPLITTER.split(line), "");
  }

  private String getUsername() {
    ParsedAnsiText line;
    int i;
    // first ditch group we're in
    output.print("gr\r\n");
    output.flush();

    while (true) {
      line = getNextLine();
      if (line == null) {
        continue;
      }

      if (line.getText().lastIndexOf("ST:", ST_INDEX) == ST_INDEX) {
        String user = getFirstWord(getUsernameFromSTLine(line.getText()));
        // ok now nod to the user
        output.printf("nod %s\r\n", user);
        output.flush();
      } else if ((i = line.getText().indexOf(" nodded to you in agreement!")) >= 0) {
        return line.getText().substring(0, i);
      }
    }
  }

  @VisibleForTesting
  @CheckReturnValue
  @Nullable
  ParsedAnsiText getNextLine() {
    while (true) {
      try {
        String line = lineSupplier.get(DEFAULT_TIMEOUT);
        if (line == null) {
          return null;
        }

        ParsedAnsiText parsedText = ansiColorParser.parseAnsi(line);
        if (!preprocessLine(parsedText, line)) {
          return parsedText;
        }
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new InterruptedRuntimeException(ex);
      } catch (ExecutionException ex) {
        if (ex.getCause() instanceof RuntimeException) {
          throw (RuntimeException) ex.getCause();
        } else {
          throw new RuntimeException(ex);
        }
      }
    }
  }

  private static String removePluralSuffix(String text) {
    if (text.endsWith("ves")) {
      return text.substring(0, text.length() - 3);
    }
    if (text.endsWith("es")) {
      return text.substring(0, text.length() - 2);
    }
    if (text.endsWith("s") || text.endsWith("i")) {
      return text.substring(0, text.length() - 1);
    }
    return text;
  }

  @VisibleForTesting
  void addPlayerToAttack(String playerName) {
    playersToAttack.add(playerName);
  }

  @VisibleForTesting
  ImmutableSet<String> getPlayersToAttack() {
    return ImmutableSet.copyOf(playersToAttack);
  }

  @VisibleForTesting
  void addGroupMember(String playerName) {
    groupMembers.add(playerName);
  }

  @VisibleForTesting
  ImmutableSet<String> getGroupMembers() {
    return ImmutableSet.copyOf(groupMembers);
  }

  @VisibleForTesting
  Stats getStats(AttackType statsType) {
    return stats[statsType.ordinal()];
  }
}
