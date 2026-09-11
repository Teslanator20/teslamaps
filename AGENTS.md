# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

# TeslaMaps — Hypixel SkyBlock mod (Fabric, MC 26.1.2)

**Hypixel SkyBlock mod** — NOT Wynncraft, despite living under `Codex/`. Started as a dungeons mod
(map, ESP, puzzle/terminal solvers, score, splits, waypoints, party) and grew into a general SkyBlock
mod: Garden/farming/pests, Foraging/HotF, Fishing, Skills, Mining, Slayer, Critter Safari, Torrhus
Canyon, Croesus, Bazaar/Auction, storage, profile viewer, screenshot/image sharing.
Package `com.teslamaps`, ~380 java files.

**Repo `github.com/Teslanator20/teslamaps` is PRIVATE and personal-use only** — no distribution, no
release pipeline right now. `LICENSE` and `NOTICE.md` were deleted, the `license` field is gone from
`fabric.mod.json`, and no source file carries a license header or attribution line. Do not re-add any
of that. Ports may keep a plain `// Ported from <mod>'s <file>` note as a pointer, without a license tag.
If the repo ever goes public, the whole licensing question has to be redone from scratch (Devonian is
GPL-3, which forced GPL v3 on the work before).
**This AGENTS.md is untracked** (listed in `.git/info/exclude`) — keep it that way.

## Build & Deploy

```bash
cd C:/Users/tim/Codex/teslamaps
JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.0.36-hotspot" ./gradlew.bat build
# deploy to BOTH test profiles (game must be CLOSED or the jar is locked)
for p in "tmapaa" "tmapaa (1)"; do
  rm -f "/c/Users/tim/AppData/Roaming/ModrinthApp/profiles/$p/mods/"teslamaps-*.jar \
    && cp $(ls build/libs/teslamaps-*.jar | grep -v -E '(-all|-sources)\.jar$') \
      "/c/Users/tim/AppData/Roaming/ModrinthApp/profiles/$p/mods/"
done
```
- Jar name follows `mod_version` in `gradle.properties`. **Every deploy auto-bumps the patch digit**
  (2.1.0 -> 2.1.1) unless Tim names a version or says "no bump" — full flow (bump, build, commit,
  deploy, Discord, release publish) lives in `~/.Codex/commands/deploy.md`.
- **`Device or resource busy` on deploy = game running.** Tell the user to close it; do NOT force.
- Crash reports: `…/profiles/tmapaa/crash-reports/`.
- **No SkyHanni installed** (user dropped it), so no chat `all.log`. `sendSystemMessage` chat does NOT
  reach `latest.log` — read `latest.log` for errors/warnings, otherwise ask the user for in-game output.
- **After EVERY deploy: post jar + changelog to Discord** via webhook — see the "Discord announce"
  section in `~/.Codex/commands/deploy.md` (webhook URL + curl command + last-announce tracking live
  there). Applies to any deploy, not only `/deploy` invocations.
- **Auto-updater** (`features/Updater` + `screen/UpdateScreen`): checks the private repo
  `Teslanator20/teslamaps-releases` on launch, popup on the title screen + `/tmap update [check]`.
  Publish side: `scripts/publish-update.sh` (run after every deploy) signs a manifest with the
  Ed25519 key at `~/.Codex/keys/teslamaps-update-private.pem` and creates release `v<version>`.
  The client verifies manifest signature (public key baked into `Updater.PUBKEY_B64`) AND jar sha256 —
  an unsigned/hand-edited release installs nothing. Download auth = read-only fine-grained PAT split
  across `Updater.T0/T1/T2`, scoped to that one repo (expires yearly — empty token just disables the
  check). Never widen the token scope, never move the private key into the repo.

## Dev client

```bash
JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.0.36-hotspot" ./gradlew.bat runClient
# with JFR (allocation/GC/safepoints across all threads, unlike spark's render-thread sampler):
./gradlew.bat runClient -Pjfr=120     # -> run/teslamaps.jfr
```
Runs alongside the user's real game. No DevAuth — the auth exception on boot is harmless, singleplayer
works offline. `runClient` always reports exit 1 on close; that is not a crash.

**Killing the dev client — match on process NAME, never window title:** Modrinth launcher starts
`javaw`, the Gradle dev client starts `java`. A title filter kills the user's real game when it happens
to sit on the main menu.
```powershell
Get-Process java -ErrorAction SilentlyContinue | Where-Object {$_.MainWindowTitle -like '*Minecraft*'} |
  Stop-Process -Force
```

Fake SkyBlock items for testing item features, typed in-game:
`/item replace entity @s weapon.mainhand with diamond_sword[minecraft:custom_data={id:"HYPERION",…},minecraft:lore=[{text:"MYTHIC DUNGEON SWORD"}]]`
**No `§` in a command** — the server kicks with "Illegal characters in chat"; use plain lore text.

Terminal testing: `terminalSolver` on → `/termsim <panes|rubix|numbers|starts_with|select|melody>`.
The terminal closes itself on solve and prints the time to chat. Terminals override the GUI scale
while open (`terminalNormalSize`, default 3) and restore it on close.

- New classes and mixins need a client restart; they don't hot-swap.
- Singleplayer has no Hypixel data — dungeon/scoreboard/chat features need injected fake data
  (`/tmap debug fakelevel|dragon|map|fish|area|sound|croesus`).

## Minecraft 26.1.2 = FIRST UNOBFUSCATED VERSION (critical)

- **No Yarn.** Official **Mojang mappings**. `net.fabricmc.fabric-loom` (non-remapping variant), no
  `mappings` line, `implementation` not `modImplementation`. AccessWidener namespace `official`.
- **Java 25 required.** Gradle 9.5, Loom 1.17.11, loader 0.19.3, fabric-api `0.152.1+26.1.2`.
- **Inspect MC API with javap** against the deobf jar before guessing:
  `javap -cp "/c/Users/tim/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.1.2/minecraft-merged-deobf-26.1.2.jar" <class>`

### 26.1.2 API renames (the ones that bit us)
- GUI: `GuiGraphics`→`GuiGraphicsExtractor`; `drawString`→`text`, `drawCenteredString`→`centeredText`,
  `renderItem`→`item`; `pose()` returns 2D `Matrix3x2fStack` (pushMatrix/popMatrix/translate/scale/rotate).
- Screen overrides: `render`→`extractRenderState(GuiGraphicsExtractor,int,int,float)`,
  `renderBackground`→`extractBackground`; widget content → `extractContents`;
  `mouseClicked(MouseButtonEvent,boolean)`, `keyPressed(KeyEvent)`.
- HUD: `HudRenderCallback` → `HudElementRegistry`, render is `(GuiGraphicsExtractor, DeltaTracker)`.
  `HudElementRegistry.replaceElement(VanillaHudElements.X, …)` wraps vanilla elements.
- World render: `WorldRenderEvents.AFTER_ENTITIES` → **`LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN`**;
  `context.poseStack()` / `context.bufferSource()`.
- ESP pipelines: builder lost `withBlend/withDepthWrite/withDepthTestFunction` → derive from vanilla
  `RenderPipelines.LINES_SNIPPET`/`DEBUG_FILLED_SNIPPET` (exposed via access widener) +
  `withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))`.
  **LINES need a per-vertex line width**: `.setLineWidth(w)` per vertex (POSITION_COLOR_NORMAL_LINE_WIDTH).
- Screen-space effects (`features/MotionBlur` + `TeslaRenderPipelines.MOTION_BLUR`): no post-chain needed.
  Pipeline on vanilla's `core/screenquad` vertex shader with `DefaultVertexFormat.EMPTY` / `Mode.TRIANGLES`,
  then `createRenderPass(name, target.getColorTextureView(), OptionalInt.empty())` + `setPipeline` +
  `bindDefaultUniforms` + `bindTexture("InSampler", …)` + `draw(0, 3)` = fullscreen quad.
  **Per-draw scalars go through `DynamicTransforms`**:
  `RenderSystem.getDynamicUniforms().writeTransform(mat, colorModulator, offset, mat)` →
  `pass.setUniform("DynamicTransforms", slice)`; fragment shader reads `ColorModulator` after
  `#moj_import <minecraft:dynamictransforms.glsl>` (declare `.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)`).
  Copy a frame with `createCommandEncoder().copyTextureToTexture(...)` — main target is RGBA8, usage 15.
  **Hook placement decides scope**: after the `GameRenderer.renderLevel` call inside `GameRenderer.render`
  = world only; after the `GameRenderer.render` call inside `Minecraft.renderFrame` = whole frame.
  Sampling a render target through the *GUI* pass needs `v0=1, v1=0` (its rows are bottom-up); through
  `core/screenquad` no flip.
- Misc: `ClickType`→`ContainerInput`, `handleInventoryMouseClick`→`handleContainerInput`,
  `LightTexture`→`Lightmap`, `player.displayClientMessage(x,false)`→`sendSystemMessage(x)`,
  `ClientCommandManager`→`ClientCommands`, `KeyBindingHelper`→`KeyMappingHelper.registerKeyMapping`,
  `ResourceLocation` is `Identifier` (`Identifier.fromNamespaceAndPath`).
- Fabric module renames: `fabric-key-binding-api-v1`→`fabric-key-mapping-api-v1`;
  `fabric-command-api-v2` `ClientCommandManager`→`ClientCommands`; `fabric-rendering-v1` dropped
  `HudRenderCallback`/`WorldRenderEvents`.

## Performance tooling

Three layers, use in this order.

**1. `/tmap perf` — in-mod per-feature timers** (`debug/Perf`, off by default).
```
/tmap perf on          # enable + reset
/tmap perf             # table: us/frame | worst frame | worst call | calls
/tmap perf spike 12    # log the per-feature breakdown of any frame over 12ms to latest.log
/tmap perf off
```
Wired at: every HUD registration (via the `hud(...)` helper, see Architecture), every world-render and
tick call in `TeslaMaps.java` (`Perf.begin("X"); ...; Perf.end();`), plus the hot mixins
(`mixin:shouldRender`, `MotionBlur`). Frame boundary comes from `PerfFrameMixin` on
`Minecraft.renderFrame`. Names come from your features, not Java frames, and `worst call` catches
spikes an average hides. Disabled cost is one static boolean load.
Adding a feature? Wrap its registration too — an unwrapped feature is invisible here.

**2. JFR — everything spark cannot see.** Built into Java 25, no agent, works on Windows.
```
./gradlew.bat runClient -Pjfr=120     # -> run/teslamaps.jfr
scripts/jfr-report.sh run/teslamaps.jfr
```
Covers allocation by call site, GC/safepoint pauses, and **all threads** — notably the netty thread,
where the packet mixins in `ClientPlayNetworkHandlerMixin` live (`handleMoveEntity`,
`handleSetEntityData`, `handleAddEntity` are high frequency).
For the real game add to the Modrinth profile's JVM args:
`-XX:StartFlightRecording=duration=120s,settings=profile,filename=tm.jfr,dumponexit=true`

**3. spark** (`https://spark.lucko.me/<id>`) — fine for a broad "what is hot" pass, but note the
limits: the client dump samples **only the Render thread**, carries no allocation data, and reports
averages, so a 300ms world-join freeze is 0.008% of an hour and invisible.
Decode: `curl -sL -o spark.bin https://bytebin.lucko.me/<id>` then parse the raw protobuf —
ROOT f2=ThreadNode; ThreadNode f1=name, f3=repeated StackTraceNode (**flat pool, no children**),
f4=packed LE doubles = thread total; StackTraceNode f3=class, f4=method, f8=packed LE doubles (ms,
**inclusive**). Aggregate f8 by `class.method` with `max`, never sum. `Minecraft.run` must land at 100%.

### Recurring performance bugs here (check these first)
- `String.replaceAll`/`split`/`matches`/`format` in a per-frame or per-entity path = a hidden
  `Pattern.compile` per call. Use `utils/StringUtil.strip` for `§` codes, and hoist patterns into
  `static final Pattern` + `matcher(...)`.
- Re-scraping the tab list / scoreboard per frame instead of the throttled helper
  (`TabListUtils`, `ScoreboardUtils`, `SkyblockUtils.getArea`). Grep for an existing throttled
  helper before writing a new scrape.
- Recomputing per frame what only changes per tick (`SkillTracker.lines`, `DungeonScore`,
  `ClearTreeLogs` reading `Display` `Transformation` interpolation).
- `.toLowerCase()` inside a loop over config patterns, per entity, per tick.
- Sweeping `level.entitiesForRendering()` per frame without a cheap "is anything targeted" gate.

## Architecture

**Everything is wired in `TeslaMaps.java`** — there is no annotation/registry system. Adding a feature
means adding a line to one of three places in `onInitializeClient`:

1. **HUD overlay** — `hud("name", (g, d) -> { if (cfg.section("X")) Feature.render(g, d); })`.
   That private helper wraps `Perf.hud(...)` and registers via
   `HudElementRegistry.attachElementBefore(VanillaHudElements.PLAYER_LIST, …)`, which draws the overlay
   *under* the tab list — `addLast` drew over it. Only DarkMode bypasses the helper (it needs
   `addFirst`/`addLast` to sandwich the vanilla HUD).
2. **World ESP** — a `Perf.begin(...)/Feature.render(poseStack, cameraPos)/Perf.end()` line inside the
   `LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN` lambda, under the right `section(...)` gate.
   `render/ESPRenderer` draws boxes/lines/tracers/text.
3. **Per-tick logic** — a `Feature.tick()` line inside `ClientTickEvents.END_CLIENT_TICK`, likewise gated.

Every render/tick call is wrapped in `Perf.begin/end`. Follow that pattern for new entries; an
unwrapped call is invisible to `/tmap perf`.

**A feature is only alive if it is wired here.** Writing `Feature.render/tick` and a config toggle is
not enough — both `TerracottaTimer` and `SimonSaysSolver` shipped fully implemented, with toggles, and
were never called. Before finishing a feature, grep for `Feature.tick(` / `Feature.render(` outside its
own file.

### Config
- `config/TeslaMapsConfig` — one big Gson POJO at `config/teslamaps/config.json`. Public fields, no
  getters. New collection/map fields must be null-guarded in `normalize()` (a hand-edited or
  older json can leave them null; `sectionEnabled` being null NPEs every `section()` call).
- **Section gating**: `cfg.section("Map")` reads `sectionEnabled` (absent = true). Every feature is
  AND-gated behind its section master before its render/tick runs. The master toggle itself is the
  first entry of each category (`MapConfigScreen.addSectionMasters`), and every other `ToggleEntry`
  in that category gets `.gatedBy(cat)` so it greys out while the master is off.
  **The gate name must be one of `TeslaMapsConfig.SECTIONS`** — that list is the single source of
  truth and has to match the `categories.put(...)` keys. A name outside it never matches any master;
  `section()` logs a warning for unknown names in dev. 10 categories are deliberately ungated
  (Croesus, F4 / M4, F7 / M7, Hide, Interface, Legit Mode, Menu & Skybox, Profiles, Screenshots,
  Tooltips & Value) and therefore show no master.
- **A config field with no GUI entry is invisible to the user.** Worth a periodic sweep: diff the
  `public <type> name =` fields against what `MapConfigScreen`/`HudEditScreen` reference. Internal
  state (PBs, caches, `activeProfile`) is fine; a real toggle is not.
- **`@Global`** on a field = belongs to the player, not the profile; carried over unchanged on a
  profile switch (`ConfigProfiles.carryGlobals`).
- **`config/ConfigProfiles`** — named profiles in `config/teslamaps/profiles/*.json`. Presets:
  `Recommended` (the default), `Minimal`, `All off`. `Full Legit` and `Teslanator` are retired and
  get dropped on launch; an old `Default` profile is renamed to `Recommended`. Presets name fields as
  **strings** and set them by reflection — renaming a field silently drops it from every preset.
- **`features/LegitMode`** — `LegitMode.blocksCheats()` gates every cheat-ish feature (ESP, auto*,
  livid finder, …). In `MapConfigScreen` the matching toggles are listed in `cheatLabels` and get
  `.legit()` so they grey out. New cheat toggles must be added to both. **`cheatLabels` matches on
  the exact (trimmed) toggle label** — renaming a toggle without updating the list silently drops the
  grey-out. Diff the two before shipping.

### GUI
- **`screen/MapConfigScreen`** (~2.5k lines) is the single config screen; `MapConfigScreen2..5` were
  merged into it and deleted. Structure: `categories` (LinkedHashMap name → entry list) grouped by
  `sections` (sidebar groups). Entry types: `LabelEntry / InfoEntry / ToggleEntry / SliderEntry /
  ColorEntry / DropdownEntry / SoundDropdownEntry / ButtonEntry`. A new option = one entry line in the
  right category + the field in the POJO.
- **`render/gui/HudStyle.panel(...)`** is the ONE place a HUD panel background is drawn — it switches on
  the `hudStyle` config (Modern / Vanilla / Tooltip / Glass). `TeslaGui.backdrop` delegates to it.
  Never draw a panel rect inline.
- `render/gui/` also holds the custom shader GUI stack: `TeslaGui`, `TeslaGuiPipelines`,
  `TeslaVertexFormats`, `SdfRectRenderState`, `ShaderQuadRenderState`, `SkyboxRenderer`,
  `MenuBackgroundRenderer`, `ImageBackgrounds`, `GuiStyle`, `GuiSounds`, plus the chroma stack
  (`ChromaColors`, `ChromaHook`, `ChromaPipelines`, `ChromaUniform` + `GuiRendererChromaMixin` /
  `RenderSystemChromaMixin`). Pipelines are `init()`ed from `TeslaMaps.onInitializeClient`.
- `screen/TeslaButton` is the standard button; `screen/ScreenFit.clamp(screen)` keeps every widget
  reachable at any gui scale — call it at the end of `init()` on new screens.
- **`screen/HudEditScreen`** (`/tmap gui`) is hand-wired per element: add to the `DragTarget` enum, add
  an `isMouseOverX` hit-test, a preview draw, the `Ctx` visibility (`ANY/DUNGEON/GARDEN/SLAYER/SAFARI/
  FORAGING` — TAB shows all), and `xxxX/xxxY/xxxScale` config fields. **Every new movable HUD overlay
  must be wired in here — no X/Y sliders in the config screen.** Screen-anchored overlays that are not
  freely positionable (livid marker, rare-drop effect, chat images) stay out of it.

### Chat & commands
- `mixin/ChatMixin` injects `ChatComponent.addMessage(Component,MessageSignature,GuiMessageSource,GuiMessageTag)`
  (the private funnel for ALL messages) and routes `text` to feature `onChatMessage(String)` handlers.
  `StringUtil.isServerMessage(text)` marks the lines a player could not have typed — trigger-style
  features (auto-requeue, PB, name cache) must only act on those.
- `command/TMapCommand.register` (FabricClientCommandSource) holds the whole command tree. Top level:
  `/ping`, `/pd`, `/pk`, `/pt`, `/termsim`, `/tmap …` (gui, safari, profile, perf, debug, waypoints,
  hotkeys, shortcut, scan, screenshots, image, share, croesus, …).
  `command/HypixelSuggestions` adds server-command suggestions, fed by `command/PlayerNameCache`.
- Command shortcuts (`/pk` → `party kick`) run through `ClientSendMessageEvents.MODIFY_COMMAND`.
- Keybinds are **not** Fabric `KeyMapping`s — they are raw GLFW key ints stored in the config
  (`TeslaMapsConfig.Keybind`, `utils/KeyNames`) and polled/handled by the features themselves.
  `KeyboardHandlerMixin` (priority 1500) gives `features/ConfigHotkey` first refusal on a key press.

### Dungeon subsystems
- `scanner/` — `MapScanner` (map item pixels), `RoomScanner`, `DoorScanner`, `ComponentGrid`
  (world↔grid), `CoreHasher`, `SecretTracker`. `database/RoomDatabase` matches core hashes to
  `map/DungeonRoom`. Core hashing differs from Odin (0 shared cores) but room NAMES match.
- `DungeonManager.getCurrentRoom()` → `DungeonRoom` (getName/getComponents/getCornerX/Z/getRotation).
- `dungeon/puzzle/` — per-puzzle solvers (DungeonBlaze, SimonSays, TicTacToe, WaterBoard, Boulder,
  IceFill, IcePath, TPMaze, CreeperBeams, Quiz, ThreeWeirdos, ArrowAlign, SpiritBearTimer,
  TerracottaTimer, PuzzleTimers).

### Terminals (rewritten — old `TerminalGuiManager` and `*Terminal` solvers are gone)
- `dungeon/terminals/` is the source of truth: `TerminalHandler` (abstract: `solve()`, `renderSlot()`,
  click simulation, first-click protection) with `PanesHandler / RubixHandler / NumbersHandler /
  StartsWithHandler / SelectAllHandler / MelodyHandler`, plus `TerminalTypes` and `TerminalUtils`
  (ticked from the main tick loop).
- `features/TerminalSolver` is the config/render facade — colors, `renderType` (0/1 vanilla highlight,
  2 custom GUI), `onSlotClick`/`onRenderSlot`/`onDrawTooltip` hooks called from the Terminal* mixins.
- `dungeon/termgui/TermGui` + `MelodyGui` draw the custom scaled overlay.
- `dungeon/termsim/` are the practice sims (`TermSimGUI`, `StartGUI`, `PanesSim`, `RubixSim`,
  `NumbersSim`, `StartsWithSim`, `SelectAllSim`, `MelodySim`), opened via `/termsim <type>`.
- The old `dungeon/puzzle/*Terminal.java` solvers, `TerminalManager` and the never-wired
  experimentation solvers (`ChronomatronSolver`, `SuperpairsSolver`, `UltrasequencerSolver`) are
  **deleted**; recover from `eea67da` if ever needed. Their config fields (`solve*Terminal`,
  `terminalClick*`, `experimentClick*`) were removed too.

### Images & screenshots
- `features/ScreenshotManager` + `mixin/ScreenshotCopyMixin` capture/copy; `screen/ScreenshotScreen`
  browses them, `screen/ImageViewerScreen` shows one.
- `features/ImageShare` uploads on explicit click to litterbox (expiring) with a catbox fallback, and
  packs the link into a domain-free token (`[img] L-<id>-p`).
- `features/SharedImageChat` rewrites an incoming line that carries a token/catbox link, pads the chat
  with blank rows, and `SharedImageChatRenderer` + `ChatGeometryMixin` draw the picture into that gap.
  `features/SharedImageLoader` downloads and caches the texture.
- **Only `catbox.moe` (exact host or a dot-subdomain) is ever fetched.** A chat line must never be able
  to steer the client at an arbitrary host — keep the check exact, `endsWith("catbox.moe")` alone also
  accepts `notcatbox.moe`.

### Shards / Hunting Box (`features/hunting/`, ported from SkyHanni)
- `ShardData` loads NEU `constants/attribute_shards.json` (per-rarity tier costs, shard list) and owns
  the tier math plus the stored amounts (`shardsSyphoned` / `shardsInBox`, both `@Global`).
  Bazaar ids match Hypixel exactly (`SHARD_GROVE`), so `PriceManager` needs no name mapping.
- `HuntingBox.tick()` reads the open **Hunting Box** (slots 9..44 minus the border columns) and the
  **Attribute Menu**; multi-page boxes only zero out missing shards once every page was seen, and the
  sync is skipped while a search query is active. Chat (`sent … to your Hunting Box`, syphon lines)
  keeps the amounts in step.
- `ShardOverlay` draws both panels right of the container (hunting box value / what is left to max,
  cheapest first), the tier as stack size and the red tint on disabled attributes. Hovering a row
  outlines the slot that holds the shard; clicking it runs `/bz <name> Shard` and copies the missing
  amount to the clipboard (the bazaar amount prompt is a sign), silently — no chat line.
  `ShardReport` = `/tmap shards [count]`.
- `BazaarOrders` reads `Your/Co-op Bazaar Orders` (buy side only, shards only) and is kept current by
  the `[Bazaar] Buy Order Setup!` / `Claimed` chat lines. Open orders count as covered, so a shard
  just bought shows `Already bought` and sinks to the bottom instead of leading the cheapest list.
  An **instant** buy leaves the shards in the player inventory, so `HuntingBox.scanPlayerInventory`
  counts those too (live, never stored). Same for anything fully covered by the box (`owned`).
- Section gate `Shards`. `ContainerValue` stands down where these render — same panel spot.
- **Everything shard-side is built on the tick, never per frame.** `ShardOverlay.update()` (called from
  `HuntingBox.tick`, every 5 ticks or immediately on a screen change) builds the panel lines, the
  panel width and the per-slot tier/disabled maps; the render pass only draws them and does hover
  rect math. Title regexes run once per tick into `boxScreen`/`menuScreen`/`bazaarScreen` — the
  render path asks for those every frame. The inventory scan (instant-buy shards) runs every 10 ticks
  and only looks at `SHARD_*` ids, never at hover names. Keep it that way: rebuilding 380 shards ×
  ~12 strings per frame is what this replaced.

### Item search (`features/ItemIndex` + `screen/ItemSearchScreen`)
- `ItemIndex.tick()` snapshots every container the player opens into
  `config/teslamaps/item_index.json` (SNBT per slot, same encoding as `StorageCache`). Source type
  comes from the container title (`typeFor`): Museum / Sacks / Vault / Wardrobe / Accessories /
  Bags / Forge. An unknown title only counts as `Chests` when a chest/barrel block was clicked in
  the last 4s (`onChestClick` from `ClientPlayerInteractionManagerMixin`, skipped in dungeons so
  secret chests stay out). Ender-chest pages and backpacks are NOT duplicated here — the search
  reads `StorageCache` for those, and the live player inventory directly.
- Capture waits 500ms after opening and repeats every 500ms; an all-empty menu never overwrites a
  good snapshot (Hypixel fills the GUI async). Sacks keep their real amount in the `Stored:` lore
  line, so `amounts` overrides the stack size. Cap is 400 sources, oldest dropped.
- `/tmap search [query|clear]`, the `itemSearchKey` hotkey, or the Inventory category button open
  `ItemSearchScreen`: rows are grouped per item name with the total and every place it sits.
- An item is only findable once its container was opened at least once — there is no API here.

### Item customization (`features/customize/` + `screen/customize/`, ported from Skyblocker)
- `/tmap customize` (and the small button in the vanilla inventory, `screen/customize/CustomizeButton`)
  opens a two-tab screen: **Armor** (piece picker, 3D player preview, trim pattern+material grid,
  head-skin grid, dye picker with animated keyframe timeline, armor model override) and **Item**
  (styled name editor with formatting/colour/gradient, glint tri-state, item model override).
  Cancel restores whatever each touched item looked like on open (`CustomizeScreen.backupConfigs`).
- Everything is keyed by **Hypixel's per-item uuid**, so it follows the item, not the slot. The uuid is
  cached on the stack by `ItemStackUuidMixin` (which also implements `injected/CustomizableStack` and
  applies the custom name in `getHoverName`); `CustomComponentMixin` swaps TRIM / PROFILE / GLINT /
  ITEM_MODEL, `CustomDyeColorMixin` the leather colour, `CustomArmorModelMixin` the equipment asset.
- **`CustomComponentMixin` sits on `DataComponentHolder.get`, the hottest method in the game.** It asks
  `Customize.active()` first, which is two booleans: `anyCustomization` (recomputed only when the
  screen writes) and `onSkyblock` (recomputed every 20 ticks — `SkyblockUtils.isInSkyblock()` is a
  regex plus a scoreboard scan and must never run per component read). Keep it that way.
- Config lives in the main POJO as nine uuid-keyed maps (`customItemNames`, `customDyeColors`,
  `customAnimatedDyes`, `customArmorTrims`, `customHelmetTextures`, `customAnimatedHelmetTextures`,
  `customGlint`, `customItemModel`, `customArmorModel`), all `@Global`. `Component` and `Identifier`
  round-trip through Gson adapters registered on `TeslaMapsConfig.GSON`.
- Data sources: head skins come from `SkyblockItems.headTextures()` (Hypixel's item resource), animated
  skulls and the real skyblock dyes from the NEU repo (`CustomizeRepo` → `constants/animatedskulls.json`,
  `constants/dyes.json`).
- **Deviation from Skyblocker:** its trim *pattern* buttons render a rotating 3D armor piece through a
  registered picture-in-picture renderer. 26.1.2 has no `PictureInPictureRendererRegistry` and
  `renderToTexture` has no `SubmitNodeCollector`, so the pattern buttons draw the matching smithing
  template item instead. Also `GuiHelper.horizontalGradient` is replaced by
  `render/gui/CustomizeGui.horizontalGradient`, a `fillGradient` rotated a quarter turn.
- Nothing applies off SkyBlock, including the preview inside the screen — same gate Skyblocker uses.

### Misc subsystems worth knowing
- `features/PacketHiders` + `ClientPlayNetworkHandlerMixin` drop spawn packets for decorative entities
  (lightning, falling blocks, xp orbs) so they are never created or ticked. Only entities with no
  gameplay meaning belong there — `SoulweaverHider` deliberately stays render-side because it also
  hides collectable secret drops.
- `features/DeadMobHider` — Hypixel plays no death animation; the only reliable death signal is an
  equipment packet clearing an armor slot.
- `features/ItemProtection` / `SlotLock` / `SlotBinds` / `DropProtectMixin` — inventory safety nets.
- `features/FixDoubleClicks` cancels the duplicate `ServerboundUseItemOnPacket` for rods/daggers via
  `PacketSendMixin`. Cancelling packets is the only fix for that Hypixel bug — do not widen it.
- `utils/SbRepo` fetches SkyBlock repo data; `utils/SkyblockItems`, `SackItems`, `SeaCreatureList` are
  loaded once at init.
- `debug/DeathProbe`, `dungeon/ExtraStats`, `dungeon/RareRoomAlert` are newer, small, self-contained.

## GOTCHAS (cost us crashes/bugs — read before touching chat/sound)
1. **Hypixel chat carries literal `§` codes** even after `Component.getString()`. Strip before
   prefix/regex matching: `utils/StringUtil.strip`. (Broke party `!commands`, PB-on-join, requeue.)
2. **ChatMixin re-routes the mod's OWN sent messages.** A handler that sends a message matching its own
   trigger → infinite recursion → StackOverflow. Use a re-entrancy guard, or make replies never match.
3. **Forwarding a command from a client command executor must NOT use `sendCommand`.** The Fabric
   command API hooks `ClientPacketListener.sendCommand`, so a client command re-sending its own line
   lands back in its own executor → StackOverflow. Send `new ServerboundChatCommandPacket(line)` through
   `connection.send(...)`. See `command/HypixelSuggestions`.
4. **A suggestion equal to what is already typed keeps the popup open**, and while open Enter applies
   the entry instead of submitting — the command can never be sent. Return no suggestions on exact match.
5. **Send chat/commands** via `mc.getConnection().sendChat(s)` / `.sendCommand(s)` (no leading `/`).
   From async callbacks wrap in `mc.execute(...)` (main thread).
6. **HEAD packet injects run on the netty thread.** Playing a sound there → `SoundEngine` CME crash.
   Hop to the main thread first. Netty-thread code may only read config fields — no world, no sound.
7. **Do not `@ModifyConstant` the chat 100-line cap** — Devonian already does it; two mods = crash.
8. **Preserve UTF-8.** A past commit stripped symbols (❤ ✯ ♨ ☠ ✔) and caused crashes + silent bugs.
9. **Anything injected into `DataComponentHolder.get` must not read a component through `get`.**
   `CustomComponentMixin` asks for the item uuid, the uuid reads CUSTOM_DATA — going through `get()`
   there is infinite recursion and a StackOverflow the moment one customization exists (2.1.5 shipped
   with it). Read straight off `getComponents()`, which is not injected.

## Style
- Comments: none by default; if any, a few words max. No long AI-style explanations. Match the
  surrounding code.
- Commit messages: terse, lowercase, no AI attribution.
- No license headers, no per-file attribution lines, no `LICENSE`/`NOTICE.md` — private repo, personal use.

## Reference mods (all local source, for porting)
| Mod | Path | Language |
|---|---|---|
| Odin | `C:/Users/tim/OdinFabric/src/main/kotlin/com/odtheking/odin/` | Kotlin |
| SkyHanni | `C:/Users/tim/Codex/skyhanni/` | Kotlin |
| Skyblocker | `C:/Users/tim/Codex/skyblocker/` | Java |
| Devonian | `C:/Users/tim/Codex/devonian/` | Kotlin |
| NoammAddons | `C:/Users/tim/NoammAddons/` | Kotlin |
| SkyOcean | `C:/Users/tim/Codex/skyocean/` | Kotlin |

Port faithfully, then adapt to this project's architecture (section gate + `Perf` wrapper + wiring in
`TeslaMaps.java`). A port that is not wired is dead code.

## Known TODO / not-done
- **Shards**: the lore/name formats were taken from SkyHanni's regexes, never seen live here. If the
  amounts stay at 0, dump the Hunting Box with the GUI dump key and check `Owned: n Shards`,
  `<ability> <roman> (<skill>)` and the item id against `HuntingBox`'s patterns.
- **Dungeon Waypoints** (Odin format, user's 50h file at `config/teslamaps/dungeon_waypoints.json`):
  renders by scanning the real BLUE_TERRACOTTA as Odin's `clayPos` (teslamaps' own corner is wrong for
  multi-component rooms), then `world = rotateAroundNorth(rotation, relXZ) + clayPos` (y absolute).
  Needs in-game verification per room; `/tmap waypoints debug` prints scanned clayPos/rotation.
  4 room names don't match the file (alias them if needed).
- **Blood Camp**: stage 1 (move timer/message/Kill-Mobs) + stage 3 (Watcher HP bar) done. Stage 2
  (spawn-box packet prediction, ported from Odin's BloodCamp.kt) **needs in-game tuning** — config
  `bloodCampAssist` + Advanced (Offset/Tick/Ping). Packet hooks in `ClientPlayNetworkHandlerMixin`;
  mobs spawn random tick ~37-41; `/tmap debug` logs `[BloodDbg]`.
- **PB-on-join** needs a Hypixel API key (`config.hypixelApiKey`).
- **`DungeonManager.isInBoss()` is unreliable** — it needs a scoreboard line containing both "Boss"
  and "❤", which never matches on F4 Thorn. **Use `DungeonManager.isInBossArea()`** instead: same
  check ORed with "player is outside the room grid" (`ComponentGrid.worldToGrid(...) == null`), which
  every boss arena satisfies. All call sites are on it now, including `DungeonScore`, where the bad
  check inflated the extra-completed-room count on F4.
