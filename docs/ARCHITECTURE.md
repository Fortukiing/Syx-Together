# Architecture

## Design Goal

Syx Together aims to let two players operate one Songs of Syx campaign. The host owns
the authoritative simulation. The client begins from a compatible snapshot and then
receives host state while both players exchange validated gameplay commands.

The game was not designed as a deterministic multiplayer simulation. The project must
therefore distinguish between authoritative gameplay state, visual correction and
transport-specific connection behavior.

## Source Boundaries

### Project Code

`src/main/java/coopmod` contains code written specifically for Syx Together:

- `CoopLanLobby` handles direct-IP lobby transport.
- `CoopSteam` handles Steam lobby, invite and P2P behavior.
- `CoopProtocol` defines validation and serialization boundaries.
- `CoopSaveTransfer` validates chunked save transfers.
- `CoopCommandRouter` routes incoming gameplay commands.
- `CoopSnapshotSync` and `CoopPendingFinish` manage bootstrap and completion work.
- `CoopNpcSync`, `CoopNpcLogicSync` and `CoopAnimalSync` handle entity state.
- `CoopWorldSync` handles time, weather and world state.
- `CoopGameplaySync` handles gameplay values shared through menus.
- `CoopCursor` and `CoopRemotePointer` handle cursor state and presentation.
- `CoopRuntime` currently coordinates the shared runtime and remains a refactoring target.

### Game Overrides

`src/game-overrides/java` contains classes in Songs of Syx packages that the mod replaces
or extends. These files are tightly coupled to one game version and require additional
review. New logic should live in `coopmod` unless an override is unavoidable.

Every pull request changing an override must identify the original behavior, the minimum
changed area and the tested Songs of Syx version.

## Authority Model

- The host is authoritative for simulation state.
- Client gameplay actions are requests that the host validates and applies.
- Authoritative results are returned to the client.
- Visual interpolation must never become a second source of gameplay truth.
- State checks detect drift but should not conceal recurring command-loss bugs.

## Shared Versus Transport Code

LAN and Steam should differ only in connection establishment and packet transport.
Construction, NPC, world, resource, diplomacy and other gameplay synchronization must
use shared logic. A gameplay bug should normally require one fix, not separate LAN and
Steam implementations.

## Session Bootstrap

1. Host creates a LAN or Steam lobby.
2. Client connects and compatibility information is checked.
3. Host chooses a new game or save.
4. A valid initial snapshot is transferred when the host world is ready.
5. The client loads the snapshot and confirms readiness.
6. Live command and state synchronization begins from a known cursor.
7. Commands occurring during transfer are replayed in order.

New-game synchronization must not expose a snapshot before required world initialization,
including throne placement, is complete.

## Failure Handling

- Network and protocol errors must contain context and reach the diagnostic log.
- Malformed, oversized and unexpected packets are rejected.
- Save chunks require ordered offsets, expected lengths and checksum verification.
- Recoverable connection failures should return the player to a usable menu state.
- Fatal startup errors must not be swallowed.
- Static class initialization must not access game systems that are not ready yet.

## Known Engineering Work

- Continue reducing `CoopRuntime` by moving ownership into focused components.
- Expand automated tests for routing, replay ordering and session transitions.
- Add reproducible integration testing for two game processes.
- Document every packet type and its authority rules.
- Reduce dependence on full game-class overrides where possible.

