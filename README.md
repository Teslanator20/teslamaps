# TeslaMaps

A Hypixel SkyBlock mod for Minecraft **26.1.2** (Fabric, Java 25). — **v2.0.0**

Started as a dungeons mod and grew into a general SkyBlock mod. Everything is configured in one
screen (`/tmap`), every HUD element is dragged in one editor (`/tmap gui`), and every category has a
master toggle so a whole area can be switched off at once.

## Dungeons

### Map
- Full dungeon map including unopened rooms, room names, secrets, crypts and total crypts
- Checkmark tracking (white / green / failed), mimic status, prince icon + structure ESP
- Player markers or heads with names, rotatable, class-colored border
- Live score estimate under the map

### ESP
- Starred mobs, minibosses, Fels, Snipers, Shadow Assassins, dungeon bats
- Wither / blood doors and fairy doors with tracers, key-colored, "next door only"
- Wither and blood keys, mimic chest, livid finder, teammates, invisible armor stands
- Dropped items, item-pickup readiness, Hoppity eggs
- Etherwarp guess box with fail box

### Puzzle Solvers
Water Board · Quiz/Trivia (box, beacon, chat recolor, hide wrong answers) · Tic Tac Toe ·
Three Weirdos · Boulder · Creeper Beams · Blaze (classic + modern) · Teleport Maze · Ice Fill ·
Ice Path · Arrow Alignment · Simon Says (wrong-click block, progress, alerts) · puzzle timers ·
Terracotta spawn timers (F6/M6)

### Terminals (F7 / M7)
- Click in Order · Correct All Panes · Starts With · Select All Color · Rubix · Melody
- Client prediction, lag compensation, custom scaled terminal GUI, solve-time announces
- Terminal simulator to practise offline: `/termsim <panes|rubix|numbers|starts_with|select|melody>`
- Phase timers: purple pad, Necron drop, Goldor ticks, storm pad, storm lightning, M7 relic

### F4 / M4 and Dragons
- Thorn / Bear / mob highlights, decoy spot, bow trajectories, spirit bear and thorn stun timers
- Overkill counter, bow miss / pickup titles, bear event titles
- Wither dragon spawn timer, spawn boxes, priority split, health, ice-spray and time-alive announces

### Blood Camp
- Watcher move-prediction timer (HUD, chat, party message), "Kill Mobs" title, Watcher HP bar
- Return-to-blood timer, Watcher kill alert, speed grade, stats HUD
- Spawn-box assist with ping/interpolation tuning

### Score, Splits, Waypoints
- Splits HUD (Blood Open, Blood Clear, Portal Entry, bosses, Total) with per-split PBs
- Live room splits, insta-clear alert, trinity alert, auto extra stats
- 270 / 300 announces, crypt reminder
- Secret waypoints by type (chests, items, bats, essence, redstone key) with tracers
- Dungeon waypoints in Odin format from `config/teslamaps/dungeon_waypoints.json`, anchored to the
  room's terracotta marker so they hold in every rotation

### Party & Automation
- Party chat commands: `!8ball` `!cf` `!dice` `!coords` `!ping` `!fps` `!time` `!warp` `!pt` `!kick`
  `!promote` `!demote`, floor queue `!f1`–`!f7` / `!m1`–`!m7`
- Auto requeue, party duplicate alert, PB on party join (needs a Hypixel API key)
- Auto GFS (pearls, jerries, superboom) on dungeon start or timer, sack tab-completion,
  bazaar fallback, auto draft on puzzle fail, auto wish, auto close chests, auto sprint
- Leap overlay with heads, map-click leaping and per-class keybinds

## Skills & Islands

- **General** — skill tracker HUD (XP/h), overflow levels in menu and chat, deployable overlay
- **Combat / Slayer** — boss and miniboss ESP, tracers, miniboss / cocoon / boss alerts
- **Garden** — plot teleport grid, pest ESP and tracker, cooldown routine, spray trip queue,
  round timer with PB, profit tracker, farm lane bar, pest fortune buff warning, tool sensitivity
- **Mining** — Crystal Hollows scanner (dragon nest, crystals, mob spots, grottos, worm spots),
  glacite corpse ESP
- **Fishing** — hook countdown, hotspot radar with beam, hotspot-gone warning, exploding frog ESP,
  bobber timer, umbrella expiry
- **Foraging** — Heart of the Forest perk highlighting and rotating perk display, honeycomb lather
  reminder + overlay, honeyhive refill reminder, Queen Bee title, tree chat and sound cleanup
- **Torrhus Canyon** — critter ESP per species, tiki ESP
- **Critter Safari** — run tracker HUD with dex, checklist, PB and hotspot, sparkling alerts,
  biome/unique-done announces, critter and bee nest ESP, auto-accept Hideyho

## Items & Interface

- **Inventory** — storage overlay, backpack/ender preview, searchbar, salvage helper, sellable
  highlighter, slot lock, slot binds, item protection, rarity backgrounds, wardrobe and loadout
  keybinds, auction price input, bazaar own-order highlight
- **Item Search** — `/tmap search` finds an item in every container you opened: museum, sacks,
  vault, accessory bag, wardrobe, forge, island chests, ender chest pages, backpacks, inventory
- **Tooltips & Value** — estimated item value with breakdown panel, lowest BIN / bazaar line,
  container top-5 value, scrollable tooltips, enchant colors and chroma max enchants
- **Croesus** — chest profit overlay, highlight the most profitable and the unopened runs
- **Screenshots** — browser, clipboard copy, chat actions, and shared images rendered inline in chat

## Visual, Chat, Sounds

- **Render** — fullbright, zoom, motion blur, smooth entity movement, dark mode, block overlay,
  rare drop effects with item name and value, custom font
- **Hide** — players, dead mobs, dungeon props (soulweaver skulls, thrown bones, superboom TNT,
  blessings, healer orbs, ...), clouds, fog, lightning, falling blocks, XP orbs (optionally dropped
  at packet level), explosions, arrows, screen overlays, hurt camera, view bobbing
- **Menu & Skybox** — custom title and loading screen, modern vanilla buttons, in-world skybox
- **Chat** — infinite history, duplicate stacking, right-click copy, and ~30 filters for dungeon,
  combat and spam lines; `/warp`, `/call` and player-name suggestions
- **Sounds** — key pickup/spawn, secret found + rising secret chime, bear spawn warning, etherwarp,
  last breath, plus sound hiders (explosions, Bonzo staff, lightning)

## Utility

- **Capes** — `/tmap cape`, three tabs. **Library** = the 12 official capes (MineCon, Migrator, …)
  plus every png in `config/teslamaps/capes`, loadable from disk or a url with a progress bar.
  **Gallery** = 2808 drawn capes off mc-capes.com, searchable, thumbnails stream in, one click
  downloads the 1024x512 original. **Player** = look a name up on capes.dev and take whatever they
  wear on Mojang / OptiFine / MinecraftCapes / LabyMod / 5zig / TLauncher / SkinMC.
  Any 2:1 texture works at any resolution; OptiFine's 46x22 and the old 22x17 sheet are padded.
  Client side only, nobody else sees it
- `/ping` and `!ping` — real round-trip latency, not the faked tab value
- Hotkeys: bind keys to chat messages or commands (`/tmap hotkeys`)
- Command shortcuts: `/alias` → `/command`, e.g. `/pk` → `party kick` (`/tmap shortcut`)
- Config profiles with presets (`Recommended`, `Minimal`, `All off`) — `/tmap profile`
- Legit Mode: one switch that blocks every cheat-ish feature
- `/tmap perf` — per-feature frame timers for debugging FPS

## Commands
- `/tmap` — settings GUI · `/tmap gui` — HUD editor
- `/tmap hotkeys` · `/tmap shortcut` · `/tmap profile` — bindings, shortcuts, config profiles
- `/tmap waypoints [add|remove|clear|debug]` — dungeon waypoints
- `/tmap screenshots [copy|delete|share|folder]` · `/tmap image [block|unblock]` — screenshots and
  image sharing
- `/tmap search [query|clear]` — find items across every indexed container
- `/tmap cape [off|reload]` — cape picker
- `/tmap safari [scan|debug|names|clear]` · `/tmap perf` · `/tmap debug [...]`
- `/termsim <type>` — terminal practice
- `/f1`–`/f7`, `/m1`–`/m7` — queue a floor
- `/ping` · `/pd` `/pk` `/pt` — ping, party disband / kick / transfer (default shortcuts, editable)

## Build
```bash
JAVA_HOME="<jdk-25>" ./gradlew build      # -> build/libs/teslamaps-2.0.0.jar
```

## Installation
1. Install Fabric for Minecraft 26.1.2
2. Install Fabric API
3. Drop the jar in your `mods` folder

## Configuration
`/tmap` opens the settings menu — searchable, grouped by category, with a master toggle per
category. HUD positions and scales are set by dragging in `/tmap gui`.
