# TeslaMaps 2.0 is here

This is the biggest update the mod has ever had. 1.7 was a dungeons mod with a few extras — 2.0 is a
full SkyBlock mod. Garden, Foraging, Fishing, Critter Safari, Skills and Mining all got proper
support, the terminal solver was rebuilt from scratch, and the entire interface was redesigned.

**300+ new settings. 160+ new features and systems.**

Everything is off-by-default where it should be, and every category has a master switch so you can
turn whole sections off at once.

Open the settings with **Right Shift** or `/tmap`.

## Terminals — rebuilt from scratch

- **Three display modes**: Odin-style highlight, vanilla highlight, or a **custom terminal GUI** with
  its own size, corner roundness and slot spacing
- **Click prediction** — clicked slots disappear instantly instead of waiting for the server
- **First-click protection** so you stop failing terminals by clicking too early, with a ping setting
- **Lag protection** — optionally hold clicks until the server has actually ticked
- **Rubix**: pick *Fewest Clicks* or *Left Clicks Only*
- **Solve times with personal bests** for every terminal type
- Full colour customisation for every terminal state

## Terminal practice mode

`/termsim <panes|rubix|numbers|starts_with|select|melody>`

All six terminals, practiceable anywhere, with **simulated ping** so you can train at your real
connection instead of a perfect one. Separate PBs from your live runs.

## Garden & Farming

- **Pest routine** — a keybind-driven loop that swaps your loadouts, warps you around and walks you
  through a full pest round step by step, with an on-screen next-step display
- **Pest overlay** — cooldown, alive pests and infested plots at a glance, with its own cooldown timer
  for when tab is wrong, plus early warnings, titles and sounds
- **Pest timer** with a personal best per round
- **Pest ESP** with tracers — see the invisible silverfish
- **Farm profit panel** — what your farming and pests are actually earning, with break detection
- **Lane bar** — set two points, get a progress bar and a warning before you overshoot the end
- **Farming sensitivity** — automatically lower mouse sensitivity while holding a farming tool
- **Plot grid** next to your inventory for instant plot teleports
- Reminders for pest gear, pests on another plot, Philip buff and fortune buff expiry
- Garden panels can be dragged and resized straight from the inventory

## Foraging & Torrhus Canyon

- **Heart of the Forest** — perks colour-coded by state, perk level shown as the stack size, unused
  tokens on the heart itself, plus a HUD for your current Lottery/Beekeeper perk
- **Lather overlay & reminder** — per-tree lather time and a warning when it runs out
- **Honeyhive reminder** with a Queen Bee instant-refill alert
- **Clean tree view** — hide the floating blocks from chopping and rune effects
- **Compact tree gifts** — the whole gift block squashed into one line
- Mute the annoying stuff: Phantoms, Fusion Machine, tree-break sound, Sweep Details spam

## Fishing

- **Hook display** — the bite countdown big on your screen, with your own alert text, sound and pitch
- **Bobber timer**
- **Hotspot radar** — reads the radar particles and shows you where the hotspot actually is, with a
  tracer and optional beam, plus a warning when your whirlpool disappears
- **Umbrella warning** before and when it runs out
- **Exploding frog ESP**

## Critter Safari

- Full **run tracker** parsed live from chat, with a HUD and personal bests
- **Per-biome checklist** so you always know what is still missing
- **Critter ESP** coloured by shard rarity, with names, tracers and an "only what nobody caught yet"
  mode
- **Sparkling critter alert** the moment the particles appear, optionally shared to party chat
- Bee nest highlighting, dedicated Hideyho tracer
- Biome-complete and full-dex announcements, with optional party sharing
- Option to remove the frost overlay in the Icy Biome

## Skills

- **Skill tracker HUD** — XP per hour plus the rates that actually matter for that skill
- **Overflow levels** in the skill menu and on level up
- Skill level shown as the stack size in the skills menu
- Overflow skill average added to the tooltip

## Dungeons

- **Wither dragon suite** — spawn messages, a per-dragon timer (ms, seconds or ticks), health above
  every live dragon, kill confirmation, time-alive messages with personal bests, ice spray messages,
  and a priority-dragon HUD
- **Rare room alert** — title, chat and sound when Trinity gets scanned
- **Extra stats** — run `/showextrastats` automatically at the end of a run
- **Invincibility timer** for Bonzo, Spirit and Phoenix, including the cooldown block
- **Terracotta spawn timers** for F6/M6
- **Simon Says solver**
- The map hides itself during the boss fight

## Items & economy

- **Estimated item value** — a full breakdown of what an item is worth, with a selectable price
  source (bazaar instant buy/sell, lowest BIN, NPC), caps on how many enchants and star materials get
  listed, exact or compact totals, and switches to ignore skins, dyes or runes
- **Item protection** — block dropping, salvaging and shift-click selling anything above a coin
  threshold you set
- **Slot binds** — bind slots together for instant swapping, with multiple profiles
- **Get from sacks** — `/gfs` commands are queued so Hypixel stops eating them, tab-completes from
  your actual sack contents, has a keybind over a slot, can top up the rest from the bazaar, and adds
  a clickable grab line after supercrafting
- **Auction price input** — the starting-bid sign replaced with a real textbox, with an undercut mode
- **Rare drop effects** — animation, particles, the drop name and what it is worth on screen
- **Progress highlight** for menu items at 100%, **commission highlight** for finished commissions
- **Wardrobe keys**, **page keybinds** (A/D for pages, W/S to scroll in any GUI)
- **Double-click fix** for blaze daggers and autopet rods

## Screenshots & image sharing

- **Screenshot manager** with an in-game browser and viewer
- Clickable **Copy / Browse / Delete** right under the save message
- **Share an image into chat** — upload with one click (1h to permanent), and anyone else running
  TeslaMaps sees the picture inline in their chat instead of a link
- Hold a key to blow the newest image up full screen
- Per-sender block list, and only one trusted host is ever loaded

## Mining

- **Corpse ESP** for unlooted Glacite Mineshaft corpses, coloured per type
- **Tiki ESP** with name labels
- **Commission highlighting**

## Interface — completely redesigned

- **4 HUD styles**: Modern, Glass, Vanilla, Tooltip — every panel in the mod changes at once
- **4 GUI styles** for the mod's own screens
- **Menu sound themes**: Clicky, Chime, Soft, Digital, with hover ticks and a volume slider
- **Custom main menu** with 10 animated backgrounds (Liquid Metal, Nebula, Starfield, Deep Space,
  Waves, Shimmer, Snow, Surge, Mesh, Liquid), your own tint, speed and accent colour — or use your own
  image as the background
- **Custom skybox** — the same animated shaders applied to the in-game sky
- **Chroma** — moving rainbow on max-level enchants and menu text
- **Motion blur**, world-only or everywhere
- **Smoother entities** — less stuttery movement of other players and mobs
- **Rarity backgrounds** — Square, Rounded, Circle, Diamond, Corners or Underline, in Filled, Outline
  or Gradient
- The settings menu is now **one searchable screen** instead of five

## Quality of life

- **Config profiles** — save whole setups and switch between them. Presets: *Recommended*, *Minimal*,
  *All off*. Your keybinds and personal bests follow you across profiles
- **Master switches** per category — turn off everything dungeon-related with one click
- **Command suggestions** for Hypixel's own commands, including player names pulled from your guild
  and friends list
- **Deployables overlay** — time left on power orbs, lanterns, umbrellas, black holes and flares
- **Sprinting overlay** and **auto sprint**
- **Smooth zoom**
- Hide clouds, skip the resource-reload overlay, hide decorative entities at packet level for extra
  FPS, and hide the bodies of dead mobs

## Performance

- Built-in profiler (`/tmap perf`) that shows exactly which feature costs how much per frame
- Several per-frame recalculations removed from the dungeon score, storage overlay and map

## Removed

- The old terminal solvers and their settings — replaced by the new system above
- Custom font
- Secret clicker
- Combat tracker (replaced by the skill tracker)
- Reforge stone list (replaced by the item value system)
- Auto-copy screenshot (replaced by the screenshot manager)

If you are updating from 1.7, your config carries over. Anything removed above is simply ignored.
Have a look through the settings — a lot is new and most of it is off by default.
