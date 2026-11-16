# Immersive Train Lines

Client-only NeoForge 1.21.1 mod: draws [Create](https://modrinth.com/mod/create) railways onto the [Hoofprint](https://modrinth.com/mod/hoofprint) world map and the [Immersive Minimaps](https://modrinth.com/mod/immersive-minimaps) minimap, at map resolution and in map colours, limited to what the map-reader could know.

Player-facing description: [docs/MODRINTH.md](docs/MODRINTH.md).

## Requirements

- Minecraft 1.21.1, NeoForge 21.1.x, client only
- Create 6.0.x
- Hoofprint 1.3+ (a Fabric mod, run through Sinytra Connector); Immersive Minimaps optional

## Development

Toolchain comes from the nix flake (`direnv allow` or prefix commands with `nix develop -c`).

```sh
nix develop -c ./gradlew build              # compile, unit tests, jar
nix develop -c ./gradlew test               # rasterizer tests; writes a proof sheet to build/preview/styles.png
nix develop -c python3 scripts/gen_icon.py  # regenerate icon

# In-game screenshots (client under Xvfb: no window, no sound) -> build/showcase/screenshots
nix develop -c python3 scripts/headless.py ./gradlew runShowcase -PpackMods=<instance>/mods
nix develop -c ./gradlew modrinth -PmodrinthDryRun
```

`-PpackMods` symlinks Create, Connector, Forgified Fabric API, Surveyor, Hoofprint, Immersive Minimaps and Asphodel from an existing instance into the run; they are not on any Maven in Mojang names.

## Layout

- `raster/` — pure Java, no Minecraft: turns sampled track centrelines into map pixels (`RailRaster`), classifying tunnels and bridges from a `Terrain`.
- `map/RailNetwork` — reads Create's client-side track graph and stations.
- `map/HoofprintTerrain` — charted chunks from Hoofprint, surface heights from Surveyor.
- `map/RailLayers` — bakes the raster into 512-block region textures, re-baked when the network or the charted area changes.
- `map/TrainSightings` — asks Create for train positions and filters them to what the player could know.
- `map/MapPainter` — draws lines, trains, station labels and tooltips into a map view.
- `mixin/` — hooks into Hoofprint's screen and the Immersive Minimaps overlay. Both are compiled against the signature stubs in `src/stubs`.
- `src/testmod` — `Showcase`: builds a railway in a fresh world and photographs the maps.
