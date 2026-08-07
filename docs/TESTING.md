# Testing

Multiplayer changes must be tested on both computers. A successful host screen alone is
not sufficient evidence that a feature is synchronized.

## Automated Checks

Run from the repository root:

```powershell
./tools/build.ps1
```

This checks compilation, Java 21 bytecode, protocol rejection and save-transfer integrity.

## Startup Smoke Test

1. Enable only Syx Together.
2. Start Songs of Syx and reach the main menu.
3. Open and close the Multiplayer menu.
4. Start a normal single-player new game.
5. Load an existing compatible save.

Any startup crash is release-blocking.

## Two-Player Matrix

Run the relevant scenario over both LAN and Steam:

| Scenario | Host | Client |
| --- | --- | --- |
| New game | Creates world and places throne | Waits, receives snapshot and joins automatically |
| Load game | Selects save and starts session | Receives save, becomes Ready and joins automatically |
| Reconnect | Keeps authoritative session | Reconnects without creating a second world |
| Shutdown | Closes session cleanly | Returns to a usable state without hanging |

## Gameplay Checks

- Place, modify, cancel and complete construction blueprints from both players.
- Modify the same room blueprint from both players and verify conflict handling.
- Build roads, doors, decorations and rooms at game speeds 1x through 4x.
- Verify NPC position, task, animation, needs and visible state on both computers.
- Verify animals and high-entity-count behavior.
- Change time speed, pause and weather-relevant state.
- Change technology, trade, workforce, diplomacy and army orders.
- Verify money, resources and event notifications.
- Test city view, world view and battle transitions.

## Evidence for Bug Reports

Provide:

- Exact Songs of Syx and Syx Together versions.
- LAN or Steam and which computer was host.
- Steps leading to the first visible difference.
- Host and client logs from the same session.
- Screenshots from both computers when the problem is visual.
- Save only when it is necessary and safe to share.

Remove public IP addresses, Steam IDs, usernames and personal paths before posting logs.

