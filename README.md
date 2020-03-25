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

java -jar TAScript.jar --config=configuration.textproto
```

## How to stop your script?
You can kill the script process in your OS. You cannot stop it via special text commands.

Or you get killed - that'll stop the script :)

## Why Java?
For maximum cross platform support. You can run this on Windows, Linux, Raspberry Pi, etc.

## Features
Once the script is running, do nothing. I'll describe the configuration options below (to be entered in the configuration
proto).

  * Attacking
    * Define `number_of_physical_attacks`, command `a <target>`
  * Attacking with a single spell
    * Define `attack_spell`, command `as <target>`
  * Attacking with an area spell
    * Define `group_spell`, command `ag <target>`
  * Healing a target
    * Define `heal_spell`, command `heal <target>`
  * Healing your group
    * Define `group_heal_spell`, command `healg`
  * Logging off
    * In the case of script failure, rather than just exiting quietly, you can define a `logoff_command` to
      execute to safely exit your character
  * Logging data
    * Define `log_file` and all script output will be written to this file for later analysis/debugging
  * Triggers
    * A powerful regex trigger based system lets you match inputs and autogenerate outputs. See the example
      below to see how to use this feature

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
