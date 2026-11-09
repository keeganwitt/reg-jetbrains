# Windows Registry Language Implementation Plan

> **For agentic workers:** Execute the user-approved plan in this session. The user's explicit implementation request supersedes additional planning approval gates.

**Goal:** Provide native highlighting, completion, and syntax diagnostics for Registry files.

**Architecture:** A restartable Kotlin lexer produces tokens for the PSI parser and editor highlighter. The parser reports local syntax errors and recovers at entry boundaries. Completion uses lexer context and the original document to avoid suggesting inside data.

**Tech Stack:** Kotlin, Java 25, Gradle, IntelliJ Platform 2026.3, platform test fixtures.

**Spec:** User-approved Windows Registry language support plan in this chat, reflected in README.md and the tasks below.

## Global constraints

- Plugin ID `com.keeganwitt.reg`, name `Windows Registry`, version `0.1.0`.
- Since-build `263`, open upper compatibility bound, platform-only dependencies.
- Both Version 5.00 and REGEDIT4, cross-platform editing without Registry access.
- Preserve Apache 2.0 licensing and IDE encoding controls.

## Review focus

- Restart lexing at token boundaries inside strings and continued hex data.
- Recover to a new section or entry after an unterminated string or continuation.
- Preserve existing brackets and quotes during completion insertion.
- Keep semicolons inside strings and key paths as content.
- Load UTF-16LE BOM files through the IDE rather than manual decoding.

## Tasks

- [x] Build setup: Kotlin DSL, wrapper, Java 25, plugin descriptor, open compatibility range. Verify with `./gradlew.bat buildPlugin`.
- [x] Language: RegLanguage/RegTokens, RegLexer, RegParser, RegParserDefinition. Test valid/invalid formats, typed data, recovery, restart states, and incremental edits with RegLexerTest and RegParserTest.
- [x] Editor: RegSyntaxHighlighter, color settings, RegEditorSupport. Verify color mappings, preview syntax, file associations, encoding, and incremental highlighting in RegEditorTest.
- [x] Completion: RegCompletionContributor. Verify headers, roots, value forms, suppression, delimiter preservation, and editable data selection in RegCompletionTest.
- [x] Delivery: examples, README, Windows/Linux CI, plugin verifier for IDEA/PyCharm/WebStorm. Run tests, packaging, configuration, structure, and compatibility checks.
- [ ] Inspect the sandbox visually in light/dark themes when desktop access permits. Automated tests verify color resolution and contrast in both built-in schemes.

## Validation on 2026-11-09

- 26 tests passed on Windows with Java 25.
- `test buildPlugin verifyPlugin verifyPluginProjectConfiguration verifyPluginStructure` succeeded.
- Plugin Verifier 1.411 reported Compatible for IDEA `IU-263.7307.200`, PyCharm `PY-263.7307.206`, and WebStorm `WS-263.7307.202`.
- CI configuration is supplied; hosted Linux/Windows CI runs have not been executed in this session.
