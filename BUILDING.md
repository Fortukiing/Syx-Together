# Building Syx Together

## Requirements

- JDK 21 or newer with `javac` and `jar` available on `PATH`.
- Songs of Syx installed locally.
- PowerShell 7 or Windows PowerShell 5.1 for the provided build script.

The compiler is always instructed to produce Java 21 bytecode. A newer JDK is allowed,
but the generated class-file version must remain `65`.

## Game JAR Location

The build script checks the `SONGS_OF_SYX_JAR` environment variable first. If it is not
set on Windows, it uses the default Steam location:

```text
C:\Program Files (x86)\Steam\steamapps\common\Songs of Syx\SongsOfSyx.jar
```

To use another installation:

```powershell
$env:SONGS_OF_SYX_JAR = "D:\Games\Songs of Syx\SongsOfSyx.jar"
```

Never copy the game JAR into this repository.

## Build Command

From the repository root:

```powershell
./tools/build.ps1
```

To compile and run every check without creating a JAR:

```powershell
./tools/build.ps1 -SkipPackage
```

The script performs a clean compile, runs the standalone tests, verifies Java 21 class
versions and creates:

```text
build/dist/Syx Together/
```

The generated distribution is intentionally ignored by Git.

## Gradle

Contributors with Gradle installed can also use:

```powershell
gradle clean check assembleMod -PsongsOfSyxJar="C:\path\to\SongsOfSyx.jar"
```

The repository does not include a downloaded Gradle distribution. A wrapper may be
added later after its generated files have been reviewed.
