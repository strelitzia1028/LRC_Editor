# LRC Editor Win7 Support

This directory is an independent Windows 7 build of LRC Editor v5.3. The main
JavaFX 21 project remains unchanged.

## Compatibility baseline

- Runtime: Zulu OpenJDK 8u504 with JavaFX 8, 64-bit.
- Packaging: JDK 8 `javapackager` application image with a private JRE.
- Video engine: FFmpeg 4.4.1 essentials, including `ass`, `gblur`, `unsharp`,
  `colorlevels`, H.264 and AAC support.
- FLAC: jFLAC Codec 1.5.2.
- Target: Windows 7 SP1 x64.

The Java sources were converted to Java 8 syntax. Help text now lives in UTF-8
resources under `src/main/resources/help`.

## Build

By default the script uses the compatibility toolchain downloaded under the main
project's `target` directory:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-win7.ps1
```

The script verifies the JDK 8 build, runs a real FFmpeg export smoke test, and
creates:

- `..\LRC-Editor-Win7-x64\`
- `..\LRC-Editor-Win7-x64.zip`

Toolchain paths can be overridden with `-Jdk8Home`, `-FfmpegExe`,
`-JflacJar`, and `-IconFile`.

## Verification

The compatibility build is checked with:

- JDK 8 compilation.
- A JavaFX 8 interface snapshot.
- The video settings window.
- MP3 playback through JavaFX Media.
- FLAC decoding through jFLAC.
- Real H.264/AAC exports with scrolling lyrics, texture processing, audio, and
  trim ranges.
- Native import checks for Windows 8/10-only APIs in the JVM, JavaFX, launcher,
  and FFmpeg binaries.

A physical Windows 7 SP1 machine is still the final deployment check for
graphics-driver and codec behavior.
