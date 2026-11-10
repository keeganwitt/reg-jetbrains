# Windows Registry for JetBrains IDEs

Native language support for Windows Registry (`.reg` and `.REG`) files in IntelliJ-based IDEs **2026.3 and newer**. The plugin runs on Windows, macOS, and Linux and never reads or writes the system Registry.

## Features

- Theme-aware highlighting with configurable colors under **Editor → Color Scheme → Windows Registry**.
- Completion for file headers, Registry roots, strings, DWORDs, binary data, expandable strings, multi-strings, QWORDs, and deletion entries.
- Syntax diagnostics for malformed headers, sections, names, strings, assignments, DWORDs, byte lists, and continuations.
- Configurable **Registry value data** inspection for incorrect numeric byte counts, invalid text encoding, and suspicious string terminators or multi-string items.
- Semicolon line commenting and section bracket matching.
- Structure tool window and File Structure popup with sections, values, type/decoded summaries, and navigation. Repeated sections stay separate and retain file order.
- Reformat Code support for assignment spacing, byte separators, and hex wrapping at the configured right margin. Existing entry order, comments, string contents, encoding, and line endings are preserved; malformed and partially selected entries are skipped.
- Inline decoded value previews for typed strings, multi-strings, DWORDs, and QWORDs, plus byte counts for other data. Disable them under **Editor → Inlay Hints → Windows Registry → Decoded Registry values**.
- Quick Documentation for file headers, Registry roots, value types, escapes, continuations, and deletion syntax.
- Syntax quick fixes and previewable intentions for equivalent DWORD and string representations.
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

## Navigation, formatting, and previews

Open **View → Tool Windows → Structure** or **Navigate → File Structure** to browse keys and values. Deleted keys/values have explicit labels; selecting an item jumps to its declaration. The outline follows section boundaries in the file, without merging repeated sections or inventing parent keys.

Use **Code → Reformat Code** for the whole file or a selection containing complete entries. Formatting produces `"Name"=value`, compact comma-separated byte lists, and `,\` continuations for long hex data. Continuation indentation defaults to two spaces; right margin and indentation are configured under **Editor → Code Style → Windows Registry**. Formatting is idempotent and preserves numeric/type spelling, blank lines, comments, and the final newline state. Values containing comments within continued data are left unchanged. It does not repair syntax errors or reorder entries.

Inline previews are display-only. Modern text data is decoded strictly as UTF-16LE, and environment variables such as `%TEMP%` remain unexpanded. Numbers are shown as unsigned decimal and hexadecimal. Invalid lengths, malformed text, and missing terminators produce explanations. Hex data in `REGEDIT4` files uses ASCII when possible; non-ASCII text requires selecting the appropriate legacy file encoding in the IDE. UTF-8/UTF-16 file encodings do not identify an original ANSI code page, so the plugin does not guess one.

Each hint is limited to 80 Unicode code points; text decoding is limited to 4096 bytes. Larger values show their byte count instead. Plain quoted strings and deletion entries do not get redundant hints.

The **Registry value data** inspection under **Editor → Inspections → Windows Registry** uses the same validation as decoded previews. Warnings describe data conventions, not guaranteed import failures. Text validation requires a valid header and known encoding and is limited to 4096 bytes; numeric byte-count checks apply at any size. Syntax errors, unknown types, and ambiguous legacy encodings do not get additional data warnings. No byte-changing quick fixes are provided.

## Documentation and context actions

Use **View → Quick Documentation**, or hover with documentation on hover enabled, to read explanations of Registry syntax and data types. Value documentation includes the same bounded decoded preview as inline hints, plus links to Microsoft references. Documentation describes the file's syntax and types; it does not identify the purpose of arbitrary Windows or application settings.

Use **Alt+Enter** on a syntax error for the available quick fixes:

- Insert a missing **Version 5.00** or **REGEDIT4** header, with the format chosen explicitly. Malformed existing header text is left for manual correction.
- Insert a closing section bracket for a recognizable key path without ambiguous comment text.
- Insert a missing assignment operator when the name and remaining value form an otherwise valid entry.

Use **Alt+Enter** within a valid value for conversion intentions, with a change preview before applying:

- **Convert DWORD representation:** switch between `dword:` digits and four little-endian `hex(4):` bytes, preserving the unsigned 32-bit value and Registry type.
- **Convert REG_SZ representation:** switch between a quoted string and NUL-terminated UTF-16LE `hex(1):` data in Version 5.00 files, preserving the full text and Registry type. Conversion uses the complete value, even when the inline preview is shortened.

Conversions preserve names, surrounding comments, other entries, and file encoding. They are not offered for malformed or incorrectly sized data, comments inside the value data, legacy string encodings, unsupported types, embedded NULs or unrepresentable line breaks, or string payloads exceeding 4096 bytes. A conversion to quoted text is also withheld if the current file encoding cannot represent that text. Use **Reformat Code** afterward to wrap generated hex data. The actions participate in normal editor Undo.

## Development

```powershell
./gradlew.bat runIde
./gradlew.bat verifyPlugin
```

`runIde` launches an isolated IntelliJ IDEA sandbox. `verifyPlugin` checks binary compatibility against IntelliJ IDEA, PyCharm, and WebStorm 2026.3. The verifier uses bundled IDE dependencies without Marketplace lookups; Gradle still downloads the IDE distributions when needed. Override the SDK and verification version together with `-PplatformVersion=2026.3.x` when testing a later patch. A verifier pass establishes binary compatibility, not a visual or functional check in every IDE.

Use `examples/modern.reg` and `examples/legacy.reg` to inspect colors and completion in light and dark themes. The tests cover parser recovery, completion insertion, lexer restarts, incremental highlighting, UTF-8, and BOM-marked UTF-16LE file loading. CI runs tests and packaging on Windows and Linux and compatibility verification on Linux.

The IDE controls file encoding. BOM-marked UTF-16 files use its standard detection; select the appropriate encoding through the IDE for legacy files without a BOM. The plugin does not guess an ANSI code page or change encoding settings.

## Boundaries

The plugin checks file syntax, not whether an import succeeds, a key exists, or a value has the intended effect. It does not import files, launch Registry Editor, or provide refactoring. Compatibility is declared from platform branch 263 with no upper bound; later versions still require verification.

## References and license

Implemented independently using the [IntelliJ Platform language APIs](https://plugins.jetbrains.com/docs/intellij/custom-language-support.html). The [VS Code REG extension](https://github.com/mihai-vlc/reg-vscode) was consulted as a feature reference; its implementation and assets are not included.

Licensed under [Apache License 2.0](LICENSE).
