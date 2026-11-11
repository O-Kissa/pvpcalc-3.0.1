# PvPCalc 3.0.0

Client-side PvP damage calculator, combat tracker, match summaries and cheat-estimate tool for
Minecraft Java **1.21.11** (Fabric). **Made by O_Kissa.**

PvPCalc runs only on your own computer. It does not need a server plugin and it never sends your
data anywhere. The only network request it can make is the optional update notice (it reads the
public release information of this project on GitHub and downloads nothing; you can turn it off with
`/pvpcalc updates off`). All files stay in `config/pvpcalc/`, and no traces of use are left anywhere
else.

> Cheat estimates are **estimates, not proof**. Never accuse a player based on them alone.

## Installation

1. Install Fabric Loader 0.18+ for Minecraft 1.21.11 and Fabric API `0.141.x+1.21.11`.
2. Put `pvpcalc-3.0.0.jar` into your `mods` folder (in Lunar Client: profile > Fabric > Mods).
3. Start the game. The folder `config/pvpcalc/` and all files are created automatically.
   A missing or broken config is replaced with defaults. Help is shown in chat on the first start.

### Build the jar yourself

Upload this folder to a GitHub repository (including `.github`). The Actions tab builds the jar
(artifact `pvpcalc-jar`). Or run `gradle build` with JDK 21 and Gradle 9.2+; the jar is created in
`build/libs/`.

## Quick start

| What | How |
|---|---|
| Choose a kit (clickable) | `/pvpcalc pvp`, then click a kit. Starts a new match and writes the marker row |
| Open all settings | key **O** or `/pvpcalc settings` |
| Match summary | shown in chat after every match, click **[Open]** (or `/pvpcalc matchover`) |
| Help | `/pvpcalc help` |
| Check that everything works | `/pvpcalc check` |

All commands work in English and Finnish, in any letter case (for example `/pvpcalc OHJE`).
If you mistype a command the mod suggests the closest one. `/pvp` and `/hack` are short aliases.
Language: `/pvpcalc language en|fi` (default English).

## Commands

| Command | Description |
|---|---|
| `/pvpcalc on` / `off` | Master switch. Off stops all calculating and logging; CSV data is kept |
| `/pvpcalc reset` | Clears the session CSV files. Long-term stats (streaks, records, history) are **not** reset |
| `/pvpcalc pvp [netherite\|diamond\|other\|additional]` | Clickable kit menus |
| `/pvpcalc active_kit <KIT>` | Select a kit (case-insensitive, tab completion) |
| `/pvpcalc version 1.8\|1.9+` | Combat system: 1.8 style (no cooldown, no sweep, Strength +130 %) or 1.9+ |
| `/pvpcalc target looked\|self` | Calculate damage on the player you look at, or on yourself |
| `/pvpcalc shield on\|off` | Assume the opponent is blocking |
| `/pvpcalc difficulty easy\|normal\|hard` | Scales explosion damage (see Notes) |
| `/pvpcalc units hp\|hearts\|both`, `decimals 0-3` | Number formatting (default 1 decimal) |
| `/pvpcalc gear [target]` | Shows the gear that is read automatically from your inventory (or the opponent) |
| `/pvpcalc calc` | Damage, hits to kill, TTK and (with a mace) the fall height needed |
| `/pvpcalc stats`, `matchover`, `report [copy]` | Match summary, big summary screen, copy a report |
| `/pvpcalc recommend <player>` | Best kit against that player from your own results |
| `/pvpcalc records`, `streaks`, `goal`, `trend [n]` | Records, win streaks, goals, progress trend |
| `/pvpcalc quiet on\|off` | Hold alerts and reports until the match is over |
| `/pvpcalc hud on\|off`, `hphud`, `durability` | HUD switches |
| `/pvpcalc sound pop\|kill on\|off` | Totem pop / kill sounds |
| `/pvpcalc music play <disc>\|stop\|list` | Play a vanilla music disc or any resource-pack sound id |
| `/pvpcalc config export\|import\|reset\|get\|set\|list` | Move settings to another computer; reset asks for a click to confirm |
| `/pvpcalc status [all]` | Current values with their defaults |
| `/pvpcalc update` | Check for a newer version (notice only) |
| `/pvpcalc hack ...` | Cheat estimate: `status`, `track`, `untrack`, `check <player>`, `threshold`, `sensitivity`, `mode`, `whitelist`, `category`, `training`, `simulate`, `reset` |

## Key bindings

Change them in the PvPCalc settings screen (key **O**, tab *Hotkeys*: click a key, press the new key, Esc clears it, red = conflict with another action) or in Minecraft: *Options > Controls > Key Binds > PvPCalc*. Defaults: **O** settings, **U**
on/off, **I** HUD, **Y** cheat estimate, **B** new match, **G** track the player you look at.
Unbound by default: open last summary, reset HUD position.

## CSV files (`config/pvpcalc/`)

No timestamps anywhere. Matches are separated by `--- NEW MATCH STARTED ---`.

| File | Content | Cleared by `/pvpcalc reset` |
|---|---|---|
| `session_data.csv` | Event log: `Event,Kit Context,Player,Details,Value` (damage, totem pops, gapples, pearls, XP bottles, hack rows) | yes |
| `matches.csv` | One row per match (damage, accuracy, crit %, combos, reaction, style, death info, hack chance) | yes |
| `match_players.csv` | One row per opponent per match (damage, totems, gapples, pearls, XP, distance, hack chance) | yes |
| `live_match.csv` | Totals of the running match, refreshed once a second when something changed | yes |
| `kit_stats.csv` | Wins, losses, win % and win streak per kit | **no** |
| `vs_players.csv` | Wins/losses against every player, per kit | **no** |
| `records.csv` | Biggest hit and longest combo per kit and per player | **no** |
| `hack_suspects.csv` | Players who crossed the alert limit in several matches | **no** |

Excel tips: for the sums use a filter on `Event = DAMAGE` and sum the `Value` column. Excel does not
reload files live; use *Data > From Text/CSV* and set the connection to refresh every minute.
With a Finnish Excel set `/pvpcalc config set csv.delimiter semicolon` before the files are created
(numbers then use a decimal comma). See `examples/` for sample files.

## Notes and limits

* **Difficulty** changes explosion damage (crystals, TNT minecarts, anchors). Plain player-versus-player
  melee is not scaled by difficulty in vanilla, so it is not scaled here either.
* **Opponent golden apples** cannot be seen directly: they are counted when a player was eating
  almost the full time. Exact for yourself, a good estimate for others.
* **Explosion source** (crystal, TNT minecart, creeper, anchor) is inferred from nearby entities.
* **Kit resource counts** in `kits.json` are estimates; edit the file to match your server.
* Cheat checks implemented: Reach, Speed, Fly, Aimbot (snap), Autoclicker, Velocity, NoFall, AutoTotem,
  Mace. Velocity, NoFall, AutoTotem and Mace are experimental and off by default. Scaffold and
  Criticals cannot be seen reliably from the client and are not included.
* Works for Minecraft 1.21.11. Minecraft 26.x uses different internal names and needs its own build.

## Settings

Every setting is available in the in-game screen (key O), with a reset button per option, a search
field, Reset tab, Export, Import and Done. Changes apply immediately and are saved when the screen
closes. All settings are also reachable with `/pvpcalc config get|set <id> <value>`.

## License

MIT. Copyright (c) O_Kissa.
