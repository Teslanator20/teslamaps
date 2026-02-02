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

## Project Structure

- Port of IllegalMap from ChatTriggers/1.8.9 to Fabric 1.21.10
- Shows full dungeon map including unopened rooms by pre-scanning chunks
- Reference implementation: `C:/Users/tim/IllegalMap-ref/`
