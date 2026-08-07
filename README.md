# Syx Together

Syx Together is an experimental two-player co-op multiplayer mod for Songs of Syx.
One player hosts the shared city while a second player connects through the LAN or
Steam lobby. The project is in active Alpha development and is not an official
multiplayer implementation.

## Important Alpha Warning

- Back up important saves before testing.
- Both players must use the same Songs of Syx version, Syx Together version and mod list.
- LAN and Steam networking are experimental.
- Not every gameplay system is guaranteed to remain synchronized yet.
- Test builds can contain crashes, desyncs and save compatibility changes.

## Current Scope

The project currently contains systems for:

- LAN and Steam lobbies for one host and one client.
- Initial save transfer and session bootstrap.
- Host-authoritative gameplay commands.
- Construction and blueprint synchronization.
- NPC and animal state synchronization.
- Time, weather, resources and selected gameplay values.
- Diplomacy, technology, trade and army order synchronization.
- Shared cursor rendering and cursor customization.
- Protocol validation, transfer limits and diagnostic logging.

This list describes implemented areas, not a guarantee that every edge case is complete.

## Repository Layout

```text
src/main/java/coopmod/       Syx Together networking and synchronization code
src/game-overrides/java/     Modified Songs of Syx classes required by the mod
src/test/java/coopmod/       Standalone protocol and save-transfer tests
mod/                         Clean mod metadata and default configuration
docs/                        Architecture, testing and publishing documentation
tools/                       Local build and repository verification scripts
```

Keeping project code and game overrides separate makes reviews easier and shows which
changes depend directly on a specific Songs of Syx version.

## Building

The full mod requires a locally installed copy of Songs of Syx. The game JAR is used
only as a compile-time dependency and must never be committed to this repository.

See [BUILDING.md](BUILDING.md) for the complete Java 21 build instructions.

## Contributing

Contributions are welcome through GitHub issues and pull requests. Contributors should
read [CONTRIBUTING.md](CONTRIBUTING.md) and [docs/TESTING.md](docs/TESTING.md) first.

Please do not submit game binaries, saves, logs containing personal information, Steam
credentials, public IP addresses or decompiled classes unrelated to the proposed fix.

## Licensing Status

The repository is being prepared for public collaboration. A final source-code license
must be selected before publication, and the game-derived override classes require a
separate permission review. See [License.txt](License.txt).

## Disclaimer

Syx Together is an unofficial community project. Songs of Syx and its assets belong to
their respective owners. This repository must not contain the game JAR, data archives,
textures, saves or other proprietary game assets.

