# TeslaMaps — Attribution & Third-Party Notices

TeslaMaps is licensed under the **GNU General Public License v3.0** (see `LICENSE`).
Copyright (c) 2026 Teslanator20.

All third-party code listed here was modified by Teslanator20 in 2026 to port it to
Minecraft 26.1.2 (Fabric, Java 25, Mojang mappings) and to fit this project's
architecture. Files that are direct ports carry a one-line pointer back to this
document.

## Sources

| Project | License | What was taken |
|---|---|---|
| [Odin](https://github.com/odtheking/Odin) | BSD-3-Clause | Dungeon waypoints, Blood Camp, puzzle and terminal solver logic |
| [OdinLegacy](https://github.com/odtheking/OdinLegacy) | BSD-3-Clause | Crystal Hollows structure scanner (`features/WorldScanner.java`) |
| [BirdAddon](https://github.com/BarefootBird/BirdAddon) | BSD-3-Clause | F4/M4 overkill counter (`features/OverkillCounter.java`) |
| [Devonian](https://github.com/Synnerz/devonian) | GPL-3.0 | Assorted dungeon features. This is why TeslaMaps as a whole is GPL v3. |
| [SkyHanni](https://github.com/hannibal002/SkyHanni) | LGPL-2.1 | Foraging features, deployables, fishing timers, hotspot radar guess, `/gfs` queue and sack lists, garden pest patterns |
| [Skyblocker](https://github.com/SkyblockerMod/Skyblocker) | LGPL-3.0 | Hoppity egg detection, Garden plot grid, rare drop effects |
| [NoammAddons](https://github.com/Noamm9/NoammAddons) | CC0 1.0 | Map player marker, Simon Says alerts, bossbar health, enchant lore colouring, dark mode, auction price input |

LGPL-2.1 §3 and LGPL-3.0 §2.2 both allow conveying the covered work under the GNU
General Public License, which is how that code is redistributed here. CC0 requires
no attribution; NoammAddons is credited as a courtesy.

## BSD 3-Clause License

Applies to Odin (Copyright (c) 2025, odtheking), OdinLegacy (Copyright (c)
2023-2025, odtheking) and BirdAddon (Copyright (c) 2025, BarefootBird).

```
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## Bundled assets

Sounds (`assets/teslamaps/sounds/`) and shaders (`assets/teslamaps/shaders/`) are
third-party and kept out of this repository — see `.gitignore` — because their
provenance has not been cleared for redistribution. Builds made from a clean clone
simply do without them.
