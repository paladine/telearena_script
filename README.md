# telearena_script
Tele-Arena Fighting Script. For running your character through a route and allowing them to fight, all day long.

## How to build?
Install [bazel](https://bazel.build/)

To build a deployable jar, run `bazel build //java/com/jeffreys/scripts/tascript:TAScript_deploy.jar`

## How to test?
To execute tests, run `bazel test ...`

## How to execute?
`java -jar TAScript.jar --config=<file with text proto of scripts.tascript.Configuration>`

You can also run out of the repo directory, `bazel run //java/com/jeffreys/scripts/tascript:TAScript -- --config=<file with text proto>`

But you're connected to a BBS playing TA, so you need to execute this in the context of my other program - [TelnetScripter](https://github.com/paladine/telnet_scripter)

## How do you run this script?
Use [TelnetScripter](https://github.com/paladine/telnet_scripter), connect to your system, and then execute the script by
writing a wrapper script

```
#!/bin/sh

java -jar TAScript.jar --config=configuration.textproto 2> /tmp/script.stderr
```

## How to stop your script?
You can kill the script process in your OS. You cannot stop it via special text commands.

Or you get killed - that'll stop the script :)

## Why Java?
For maximum cross platform support. You can run this on Windows, Linux, Raspberry Pi, etc.

## Features
Once the script is running, do nothing. I'll describe the configuration options below (to be entered in the configuration
proto).

  * **double heal_percentage**
    * What percentage of health to heal, specified as a floating point number between 0->1, e.g. gimotu
  * **double big_heal_percentage**
    * What percentage of health to trigger a big heal, specified as a floating point number between 0->1, e.g. kusamotu
  * **double group_heal_percentage**
    * What percentage of health one group member must have to cast an area heal specified as a floating point number between 0->1,
      e.g. kusamotumaru. Will not cast an area if there is only one member in your group. Allows you to combine one TAScript with
      several [TAFollow](https://github.com/paladine/telearena_follow) scripts to script multiple characters at the same time.
  * **double critical_percentage**
    * what percentage of health to bail, specified as a floating point number between 0->1. Executes your logoff command
  * **bool heal_spell_is_attack**
    * Whether you heal by attacking, i.e. a necromancer with yilazi
  * **bool big_heal_spell_is_attack**
    * Whether your big heal is an attack, i.e. a necromancer with yilazidaku
  * **int32 minimum_attack_mana**
    * Minimum mana to have before issuing a spell attack (heal not affected)
  * **int32 minimum_move_mana**
    * Minimum mana to have before moving to the next room
  * **int32 maximum_mana**
    * Your maximum mana. Currently not used, but would be used for tracking being drained and having to cast ganazi to emend.
  * **int32 maximum_vitality**
    * Your maximum vitality. Currently not used, but would be used for tracking being drained and having to cast ganazi to emend.
  * **int32 number_of_physical_attacks**
    * Self explanatory
  * **int32 move_pause_milliseconds**
    * How long to wait between movements in milliseconds. A value of 1 is a safe option to prevent tripping
  * **bool wait_for_all_members**
    * Whether to wait for all members to be ready before making a move
  * **bool has_yari**
    * Whether you're a sorceror with yari who will maintain yari
  * **string sustenance_command**
    * Whether you're a druid with kotarimaru/kotari for your group eating purposes. Command should be `c kotari <username>`,
      `c kotarimaru` or `co c kotarimaru` if you're the controlling scripter.
  * **string username**
    * Your character name. Script nods to itself when starting to validate the name
  * **string heal_spell**
    * Your heal spell, associated with `heal_percentage`
  * **string big_heal_spell**
    * Your big heal spell, associated with `big_heal_percentage`
  * **string group_heal_spell**
    * Your group heal spell, associated with `group_heal_percentage`
  * **string attack_spell**
    * Your single monster attack spell
  * **string additional_attack_command**
    * An additional attack command to send, supports $1 for the target, e.g. `co as $1`
  * **string group_attack_spell**
    * Your area monster attack spell
  * **string additional_group_attack_command**
    * An additional group attack command to send, supports $1 for the first target, e.g. `co ag $1`
  * **string log_off_command**
    * Command to send before a forceful logoff, e.g. `=x\r\n`
  * **string log_file**
    * File to log everything to, e.g. `/tmp/script.log`
  * **string movement_file**
    * File specifying the moves/route when the player is ready. File contains one command per line. Empty lines and commented
      lines are ignored, e.g. lines beginning with # or //
  * **repeated string protected_players**
    * A list of players never to attack

  * **scripts.common.Trigger triggers**
    * A powerful regex trigger based system lets you match inputs and autogenerate outputs. See the example
      below to see how to use this feature

## Other Features
  * The script will automatically attack people who have attacked you (except those listed under `protected_players`)
  * The script will automatically add people to your group if they ask, so you don't nuke other players with area attack spells.
    It will not add players who have attacked you.
  * The script will automatically log off if it detects that you're in the temple, since you've probably been killed. So don't
    have a movement file that takes you through the temple.
  
## Sample script configuration for a High Priest
See the [text proto definition](https://github.com/paladine/telearena_script/blob/master/java/com/jeffreys/scripts/tascript/tascript.proto)
for the list of available fields.

```
critical_percentage: 0.3

heal_spell: "kusamotu"
heal_percentage: 0.77

big_heal_spell: "tomotu"
big_heal_percentage: 0.6

group_heal_spell: "tomotumaru"
group_heal_percentage: 0.75

attack_spell: "totami"
group_attack_spell: "tamidaku"

minimum_attack_mana: 74
minimum_move_mana: 80
maximum_mana: 176
maximum_vitality: 3078
number_of_physical_attacks: 5

additional_attack_command: "co as $1"
additional_group_attack_command: "co ag $1"

username: "Paladine"
log_off_command: "co =x\r\n=x\r\n"
movement_file: "/scripts/paladine.move"
log_file: "/tmp/paladine.log"
move_pause_milliseconds: 1000
wait_for_all_members: true

triggers: {
  expected_color: CYAN
  trigger_regex: ".*You found (\\d+) gold crowns while searching the.*"
  command: "sh $1"
}
```

## Sample movement file (for dwarven level 8, beginning slightly north of the warlords around the bends)
```
# start on the horizontal line, right before going south to the hard guys
s
s
w
w
s
s
e
e
e
s
s
s

# turn around
n
n
n
w
w
w
n
n
e
e
n
n
# back at beginning , head left
w
w
w
w
w
w
w
w
w
# at left fork
n
n
w
w
w
w
e
e
e
e
s
s
# back at left fork
s
s
w
w
w
w
e
e
e
e
n
n
# back at left fork, return
e
e
e
e
e
e
e
e
e
```
