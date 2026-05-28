# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

**Prerequisite:** JDK 21 must be used. The system default Java is JDK 8, so always set `JAVA_HOME`:
```bash
export JAVA_HOME=/c/DevRepo/jdk/dragonwell-21.0.6.0.6+7-GA
```

- **Build:** `mvn compile`
- **All tests:** `mvn test`
- **Single test class:** `mvn test -Dtest=DocumentParserTest`
- **Run CLI:** `mvn exec:java -Dexec.mainClass="cn.p4u.smart.cli.CliRunner" -Dexec.args="input.docx -o output.html"`

## Architecture

Three-phase pipeline where each stage produces a standalone, testable output:

```
.docx → DocxExtractor → DocumentParser → HtmlRenderer → HTML
          (unzip)         (XML→model)      (model→HTML)
```

**Data flow:**
1. `DocxExtractor` unzips the .docx to a temp directory (with zip-slip protection). Caller is responsible for cleanup via `DocxExtractor.cleanup()`.
2. `DocumentParser` reads `word/document.xml`, `word/_rels/document.xml.rels`, and `word/styles.xml` using DOM. Produces an immutable `DocumentModel` tree of sealed interfaces and records.
3. `HtmlRenderer` traverses the model tree, emitting HTML with all CSS inlined as `style` attributes.

**High-level entry point:** `DocxConverter.convert(Path, ConversionConfig)` chains all three stages. The CLI (`CliRunner`) is a thin picocli wrapper around it.

## Key Namespaces (OOXML)

These are used pervasively in `DocumentParser` and `OmmlToLatexConverter`:

| Prefix | URI | Used in |
|--------|-----|---------|
| `w:` | `http://schemas.openxmlformats.org/wordprocessingml/2006/main` | DocumentParser |
| `r:` | `http://schemas.openxmlformats.org/officeDocument/2006/relationships` | DocumentParser (hyperlinks, image refs) |
| `m:` | `http://schemas.openxmlformats.org/officeDocument/2006/math` | DocumentParser, OmmlToLatexConverter |
| `wp:` | `http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing` | DocumentParser (image extents) |
| `a:` | `http://schemas.openxmlformats.org/drawingml/2006/main` | DocumentParser (blip references) |

## Unit Conversion Rules

- **Font size:** `w:sz` is half-points → divide by 2 for pt (e.g., `val="24"` → `12pt`)
- **Border width:** `w:sz` on borders is eighths-of-a-point → divide by 8 for pt (e.g., `val="4"` → `0.5pt`)
- **Indentation/margins:** `w:left`/`w:right`/`w:firstLine` are twips → divide by 20 for pt
- **Table/cell width:** `w:w` with `w:type="pct"` → divide by 50 for CSS %; `w:type="dxa"` → divide by 20 for pt
- **Image dimensions:** `cx`/`cy` in `wp:extent` are EMUs → divide by 12700 for px (or 9525 for approximate px)
- **Color values:** `auto` means default to `#000000` (black)

## Security

All XML parsers must set `disallow-doctype-decl=true` and provide a no-op `EntityResolver` to prevent XXE attacks. This is enforced in `DocumentParser`, `StylesParser`, and `RelsParser`. `DocxExtractor` validates zip entry paths against the temp directory root to prevent zip-slip. `ImageHandler.copyToDir` validates output paths against the target directory root to prevent path traversal.

## Testing

Tests use `TestDocxBuilder` (in `src/test/java/cn/p4u/smart/util/`) to programmatically construct valid .docx ZIP files. It implements `AutoCloseable` and cleans up the `.docx` temp file on close. The extracted directory is cleaned up by `DocxExtractor.cleanup()` in test `finally` blocks.
