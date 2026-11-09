# Windows Registry for JetBrains IDEs

Native language support for Windows Registry (`.reg` and `.REG`) files in IntelliJ-based IDEs **2026.3 and newer**. The plugin runs on Windows, macOS, and Linux and never reads or writes the system Registry.

## Features

- Theme-aware highlighting with configurable colors under **Editor → Color Scheme → Windows Registry**.
- Completion for file headers, Registry roots, strings, DWORDs, binary data, expandable strings, multi-strings, QWORDs, and deletion entries.
- Syntax diagnostics for malformed headers, sections, names, strings, assignments, DWORDs, byte lists, and continuations.
- Semicolon line commenting and section bracket matching.
- Modern `Windows Registry Editor Version 5.00` and legacy `REGEDIT4` syntax, including key/value deletion and continued hex data.

Completion uses file context. It does not discover installed Registry keys or validate Windows settings. Arbitrary hexadecimal type identifiers in `hex(type):` are supported.

## Build and install

Install a **JDK 25** and set `JAVA_HOME` to it. The Gradle wrapper downloads the build dependencies and IntelliJ SDK on the first run.

PowerShell:

```powershell
./gradlew.bat test buildPlugin
```

macOS/Linux:

```sh
bash ./gradlew test buildPlugin
```

Install `build/distributions/reg-jetbrains-0.1.0.zip` using **Settings → Plugins → gear menu → Install Plugin from Disk**, then open a Registry file. Use **Code → Code Completion → Basic** to invoke suggestions. Existing custom file associations may need to be removed under **Editor → File Types**.

## Development

```powershell
./gradlew.bat runIde
./gradlew.bat verifyPlugin
```

`runIde` launches an isolated IntelliJ IDEA sandbox. `verifyPlugin` checks binary compatibility against IntelliJ IDEA, PyCharm, and WebStorm 2026.3. The verifier uses bundled IDE dependencies without Marketplace lookups; Gradle still downloads the IDE distributions when needed. Override the SDK and verification version together with `-PplatformVersion=2026.3.x` when testing a later patch. A verifier pass establishes binary compatibility, not a visual or functional check in every IDE.

Use `examples/modern.reg` and `examples/legacy.reg` to inspect colors and completion in light and dark themes. The tests cover parser recovery, completion insertion, lexer restarts, incremental highlighting, UTF-8, and BOM-marked UTF-16LE file loading. CI runs tests and packaging on Windows and Linux and compatibility verification on Linux.

The IDE controls file encoding. BOM-marked UTF-16 files use its standard detection; select the appropriate encoding through the IDE for legacy files without a BOM. The plugin does not guess an ANSI code page or change encoding settings.

## Boundaries

The plugin checks file syntax, not whether an import succeeds, a key exists, or a value has the intended effect. It does not import files, launch Registry Editor, format documents, or provide refactoring. Compatibility is declared from platform branch 263 with no upper bound; later versions still require verification.

## References and license

Implemented independently using the [IntelliJ Platform language APIs](https://plugins.jetbrains.com/docs/intellij/custom-language-support.html). The [VS Code REG extension](https://github.com/mihai-vlc/reg-vscode) was consulted as a feature reference; its implementation and assets are not included.

Licensed under [Apache License 2.0](LICENSE).
