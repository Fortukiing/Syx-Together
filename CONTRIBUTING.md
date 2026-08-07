# Contributing to Syx Together

Thank you for helping improve Syx Together. The mod is an early Alpha and changes must
be reviewed carefully because a small error can crash the game, corrupt a session or
create a silent desync between host and client.

## Contribution Workflow

1. Search existing issues before opening a new one.
2. Fork the repository and create a focused branch.
3. Keep the change limited to one bug or feature.
4. Build with Java 21 and run the available tests.
5. Test both host and client when changing multiplayer behavior.
6. Submit a pull request using the repository template.
7. Address review comments before the change is merged.

Do not push directly to `main` or `development`.

## Branches

- `main` represents the current public Steam Workshop release.
- `development` contains work intended for the next test release.
- Feature and fix branches should normally target `development`.

## Required Rules

- Compile with Java 21 (`--release 21`, class-file version 65).
- Do not catch `Throwable` for ordinary error handling.
- Do not silently ignore network, save or synchronization failures.
- Do not add anonymous long-running networking implementations when a named class is clearer.
- Keep LAN and Steam transport code separate from shared gameplay synchronization logic.
- Keep new Syx Together code under `coopmod` whenever possible.
- Document every necessary change to a game override class.
- Do not commit generated JARs, classes, ZIPs, logs, saves, backups or local configuration.
- Do not commit `SongsOfSyx.jar`, `data.zip`, Steam libraries or game assets.
- Do not include public IP addresses, Steam credentials, access tokens or personal paths.

## Multiplayer Changes

A synchronization pull request must explain:

- Which side is authoritative: host, client or transport-independent shared state.
- What message or command is sent.
- How duplicate, late, missing and out-of-order messages are handled.
- Whether the behavior is identical over LAN and Steam.
- What happens during new game, load game, reconnect and shutdown.
- How the change behaves at higher game speeds.

Opening or closing a menu does not normally need synchronization. Gameplay values and
commands changed through that menu do.

## Testing Expectations

At minimum, run:

```powershell
./tools/build.ps1
```

Changes touching live multiplayer behavior also require the relevant manual checks from
[docs/TESTING.md](docs/TESTING.md). Attach sanitized host and client logs when a failure
cannot be reproduced reliably.

## Pull Request Size

Small, focused pull requests are easier to review and test. Large refactors should begin
with an issue describing the ownership boundaries, migration plan and expected risks.

