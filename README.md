# TeslaMaps

A Hypixel SkyBlock Dungeons mod for Minecraft **26.1.2** (Fabric, Java 25). — **v1.3.0**

## Features

### Dungeon Map
- Full dungeon map showing all rooms (including unopened ones)
- Room identification with names, secrets count, crypts, and total crypts
- Checkmark status tracking (white / green / failed)
- Player markers/heads on the map with names (toggleable, rotatable, scalable)
- Live score estimate, secrets (found / needed / total), crypts, and mimic status below the map
- Movable, scalable HUD elements (HUD editor via `/tmap gui`)

### ESP
- Mimic detection and chest ESP
- Wither / Blood door ESP and tracers
- Starred mob / miniboss ESP
- Etherwarp guess box (where your etherwarp will land), with optional fail box

### Puzzle Solvers
- **Water Board** – highlights the levers to pull and when (with countdown + box highlight)
- **Quiz / Trivia** – highlights the correct answer (in-world box + in-chat recolor; can hide wrong answers)
- **Tic Tac Toe** – highlights the optimal move
- **Three Weirdos** – identifies the correct NPC
- **Boulder** – shows the solution path
- **Creeper Beams** – highlights targets
- **Simon Says** – tracks the button sequence
- **Blaze** – shows the kill order
- **Teleport Maze** – shows the correct pads
- **Arrow Alignment** – shows which arrows to rotate

### Terminal Solvers (F7/M7)
- Click in Order · Correct All Panes · Starts With · Select All Color · Rubix · Melody

### Blood Camp
- Watcher move-prediction timer (HUD + optional chat / party message)
- "Kill Mobs" title and Watcher HP bar

### Dungeon Waypoints
- Renders Odin-format waypoints from `config/teslamaps/dungeon_waypoints.json`
- Add / remove / clear waypoints in-game via commands or rebindable keybinds (`/tmap` → Waypoints)
- Anchored to the room's terracotta marker so they work in every room rotation

### Splits & Reminders
- Dungeon run splits HUD (Blood Open, Blood Clear, Portal Entry, bosses, Total)
- Each split's duration printed to chat as it completes
- Crypt reminder ~30s into blood camp if you have fewer than 5 crypts
- Score milestone messages (270 / 300) locally, with optional party-chat announce

### Party / Team Messages
- Blaze Done, Mimic Dead, Prince Dead announces to party chat (each toggleable)
- Party chat commands: `!8ball` `!cf` `!dice` `!coords` `!ping` `!fps` `!time` `!warp` `!pt` `!kick` `!promote` `!demote`
- Floor queue: `!f1`–`!f7` / `!m1`–`!m7` (party leader queues the whole party)

### Sounds
- Secret found sound + ascending **secret chime** (rises in pitch per secret in the room)
- Etherwarp custom sound
- A shared library of ~30 clean sounds with volume + pitch, selectable for each

### Utility
- `/ping` and `!ping` – real round-trip latency (not the faked tab value)
- Hotkeys: bind keys to chat messages or commands (`/tmap hotkeys`)
- Command shortcuts: map `/alias` → `/command`, e.g. `/pk` → `party kick` (`/tmap shortcut`)
- Auto GFS – refills sacks on dungeon start / timer
- Secret Waypoints, Leap Overlay (enhanced spirit leap menu)

## Commands
- `/tmap` – open the settings GUI (`/tmap gui` for the HUD editor)
- `/tmap hotkeys` – manage key → message/command bindings
- `/tmap shortcut` – manage command shortcuts
- `/tmap waypoints [add|remove|clear|debug]` – manage dungeon waypoints
- `/ping` – show your real ping
- `/pd` `/pk` `/pt` – party disband / kick / transfer (default shortcuts, editable)

## Installation
1. Install Fabric for Minecraft 26.1.2
2. Install Fabric API
3. Place the mod jar in your `mods` folder

## Configuration
Use `/tmap` (or `/tmap config`) to open the settings menu. Everything is searchable and grouped by category.

## License
TeslaMaps is licensed under the **GNU General Public License v3.0** — see the `LICENSE` file.

This project ports/adapts code from other open-source mods. Their notices and the
reason the whole project is GPL v3 are documented in `NOTICE.md`:
- **Odin** (https://github.com/odtheking/Odin) — BSD 3-Clause
- **Devonian** (https://github.com/Synnerz/devonian) — GPL v3
