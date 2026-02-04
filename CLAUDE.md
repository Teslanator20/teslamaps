# TeslaMaps - Fabric 1.21.10 Dungeon Map Mod

## Build & Deploy

```bash
cd C:/Users/tim/TeslaMaps
./gradlew.bat build && rm -f "/c/Users/tim/AppData/Roaming/ModrinthApp/profiles/TMAP/mods/"teslamaps-*.jar && cp $(ls build/libs/teslamaps-*.jar | grep -v -E '(-sources)\.jar$' | head -1) "/c/Users/tim/AppData/Roaming/ModrinthApp/profiles/TMAP/mods/"
```

**ALWAYS deploy to the TMAP Modrinth profile, NOT .minecraft/mods/**

## Logs & Crash Reports

```
C:/Users/tim/AppData/Roaming/ModrinthApp/profiles/TMAP/logs/latest.log
C:/Users/tim/AppData/Roaming/ModrinthApp/profiles/TMAP/crash-reports/
```

## Project Overview

- Port of IllegalMap from ChatTriggers/1.8.9 to Fabric 1.21.10
- Shows full dungeon map including unopened rooms by pre-scanning chunks
- Reference implementation: `C:/Users/tim/IllegalMap-ref/`

## Key Coordinates & Constants

| Constant | Value | Description |
|----------|-------|-------------|
| Dungeon bounds | -200,-200 to -10,-10 | World coordinates |
| Room size | 31 blocks | Without door |
| Cell size | 32 blocks | Room + door |
| Grid size | 6x6 | Component grid |
| Core Y level | 68 | Block sampling height |

## Architecture

### Core Classes

| Class | Purpose |
|-------|---------|
| `TeslaMaps.java` | Main mod entry, registers tick/render callbacks |
| `DungeonManager.java` | Central state manager, coordinates all systems |
| `ComponentGrid.java` | 6x6 grid management, world-to-grid conversion |
| `RoomScanner.java` | Pre-scans chunks on dungeon entry |
| `CoreHasher.java` | Calculates block hashes for room identification |
| `MapRenderer.java` | HUD overlay rendering |

### Room Identification

Rooms are identified by hashing blocks at specific positions within each room component:
- Sample positions defined in `CoreHasher.SAMPLE_OFFSETS`
- Hash compared against `rooms.json` database (100+ rooms)
- Multi-component rooms (1x2, 2x2, L-shapes) merge when adjacent components share the same room name

### Important Implementation Details

**Multi-component Room Merging:**
- Adjacent components with same room name merge into single DungeonRoom
- L-shaped rooms have 3 components (not 4)
- Text centering for L-rooms: display name on the 2x1 (bigger) side

**Mimic Detection (`MimicDetector.java`):**
- Scans trapped chests via `chunk.getBlockEntities()` (NOT block iteration)
- Some rooms normally have trapped chests - use EXPECTED_CHESTS map:
  - Buttons: 1, Slime: 1, Dueces: 1, Redstone Key: 1
- Mimic room = room with MORE trapped chests than expected
- Listens for "mimic dead/killed/done" in chat from party members
- Sends `/pc Mimic Dead!` when mimic killed

**Livid Solver (`LividSolver.java`):**
- Wool indicator block at `BlockPos(5, 110, 42)`
- Detects correct Livid by matching wool color to entity name prefix
- Handles blindness effect (delays announcement)
- Sends `/pc Livid Dead!` when correct Livid dies

**Score Calculation:**
- Skill: 20 base + 80 * (rooms%) - deaths*2
- Exploration: 60 * (rooms%) + 40 * (secrets% / required%)
- Time: 100 (assumed full)
- Bonus: min(crypts, 5) + (mimic killed ? 2 : 0)
- Floor-specific secret requirements: F1=30%, F2=40%, F3=50%, F4=60%, F5=70%, F6=85%, F7=100%, Master=100%

### ESP Rendering

All ESP uses `ESPRenderer` utility class:
```java
ESPRenderer.drawBoxOutline(matrices, box, color, lineWidth, cameraPos);
ESPRenderer.drawTracerFromCamera(matrices, targetPos, color, cameraPos);
```

Colors are ARGB integers (e.g., `0xFFFF0000` = red).

### Chat Integration

`ChatMixin.java` forwards messages to:
- `AutoGFS.onChatMessage()` - dungeon start/puzzle fail
- `LividSolver.onChatMessage()` - fight start detection
- `MimicDetector.onChatMessage()` - mimic dead from party
- `SlayerHUD` - slayer quest complete/failed
- `StarredMobESP` - key pickup notifications

### Config

All config in `TeslaMapsConfig.java`:
- Colors stored as hex strings ("AARRGGBB" or "RRGGBB")
- Parse with `TeslaMapsConfig.parseColor(hexString)`
- Lists (customESPMobs, recentPlayers) need null checks after Gson load

## Floor Enum

`DungeonFloor`: F1-F7, M1-M7, UNKNOWN (no E/entrance floor)

## Custom Terminal GUI

TeslaMaps now features a custom terminal GUI system ported from OdinFabric, providing a cleaner overlay for F7 terminals:

**Location:** `src/main/java/com/teslamaps/dungeon/termgui/`

**Key Classes:**
- `CustomTermGui.java` - Base class for all terminal GUI implementations
- `TerminalGuiManager.java` - Manages GUI lifecycle and rendering
- `NumbersTermGui.java` - GUI for "Click in order!" terminal
- `PanesTermGui.java` - GUI for "Correct all the panes!" terminal
- `StartsWithTermGui.java` - GUI for "What starts with: '*'?" terminal
- `SelectAllTermGui.java` - GUI for "Select all the [color] items!" terminal

**Config Options (in TeslaMapsConfig.java):**
- `customTerminalGui` - Enable/disable custom GUI (default: false)
- `terminalGuiSize` - Size multiplier (0.5 - 3.0)
- `terminalGuiGap` - Gap between slots (0 - 15 pixels)
- `terminalGuiRoundness` - Corner roundness (0 - 15)
- `terminalGuiShowNumbers` - Show numbers in Click in Order
- Various color options for highlights

**Mixins:**
- `HandledScreenMixin` - Intercepts mouse clicks
- `GenericContainerScreenMixin` - Renders overlay and hides default GUI

**How It Works:**
1. When a terminal opens, `TerminalGuiManager` detects the terminal type by screen title
2. Creates appropriate GUI instance (NumbersTermGui, PanesTermGui, etc.)
3. Renders custom overlay with color-coded slots showing the solution
4. Mouse clicks are routed through the custom GUI to the correct slots

**Differences from OdinFabric:**
- Uses Minecraft's standard rendering APIs instead of NanoVG
- Simplified rounded rectangles (currently just filled rects)
- No smooth animations (could be added later)
- Colors are configured via hex strings in config

## Adding New Features

1. **New ESP:** Add to `TeslaMaps.onWorldRender()`, use `ESPRenderer`
2. **New Chat Detection:** Add handler call in `ChatMixin.onChatMessage()`
3. **New Config Option:** Add field to `TeslaMapsConfig.java`
4. **New Room Data:** Add core hash to `rooms.json`
5. **New Terminal GUI:** Extend `CustomTermGui` and add to `TerminalGuiManager`

## Common Issues

- **Room not detected:** Missing core hash in rooms.json - check logs for "Unknown room with core X"
- **Multi-room not merging:** Components must have identical room names from database
- **Mimic in wrong room:** Check EXPECTED_CHESTS map for rooms with normal trapped chests
- **ESP not showing:** Check config toggle and that you're in dungeon (`DungeonManager.isInDungeon()`)

## Files Reference

```
src/main/java/com/teslamaps/
├── TeslaMaps.java              # Entry point, render callbacks
├── config/TeslaMapsConfig.java # All config options
├── dungeon/
│   ├── DungeonManager.java     # State coordination
│   ├── DungeonDetector.java    # Scoreboard detection
│   ├── DungeonFloor.java       # F1-F7, M1-M7 enum
│   ├── DungeonState.java       # IDLE, IN_DUNGEON, BOSS_FIGHT
│   └── MimicDetector.java      # Mimic tracking
├── scanner/
│   ├── RoomScanner.java        # Chunk pre-scanning
│   ├── CoreHasher.java         # Block hash calculation
│   ├── ComponentGrid.java      # Grid coordinate system
│   └── DoorScanner.java        # Door type detection
├── map/
│   ├── DungeonRoom.java        # Room instance with state
│   ├── RoomType.java           # NORMAL, PUZZLE, TRAP, etc.
│   └── CheckmarkState.java     # UNEXPLORED, WHITE, GREEN, FAILED
├── render/
│   ├── MapRenderer.java        # Main HUD, score display
│   └── ESPRenderer.java        # Box/tracer drawing
├── features/
│   ├── LividSolver.java        # F5/M5 correct Livid finder
│   └── AutoGFS.java            # Auto sack refill
├── esp/
│   └── StarredMobESP.java      # Mob highlighting
├── slayer/
│   └── SlayerHUD.java          # Slayer boss overlay
├── mixin/
│   ├── ChatMixin.java          # Chat message forwarding
│   └── InGameHudMixin.java     # HUD render injection
└── utils/
    ├── TabListUtils.java       # Tab list parsing (secrets, crypts, deaths)
    └── ScoreboardUtils.java    # Scoreboard parsing
```
