# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

**Prerequisite:** JDK 21 must be used to run Maven. The system default Java is JDK 8, so always set `JAVA_HOME`:
```bash
export JAVA_HOME=/c/DevRepo/jdk/jdk-1.8
```

- **Build:** `mvn compile`
- **All tests:** `mvn test`
- **Single test class:** `mvn test -Dtest=DocumentParserTest`
- **Run CLI:** `mvn exec:java -Dexec.mainClass="cn.p4u.smart.cli.CliRunner" -Dexec.args="input.docx -o output.html"`

## JDK 8 Compatibility

Source and target are set to `1.8` in pom.xml. The codebase must compile as Java 8 — no streams, lambdas, records, sealed types, `var`, text blocks, or JDK 9+ API calls. `Jdk8Helpers` (`cn.p4u.smart.util`) backports `OutputStream.nullOutputStream()`, `InputStream.transferTo()`, `Files.writeString()`, and `Files.readString()`. Use these instead of the JDK 11+ equivalents.

## Architecture

Three-phase pipeline where each stage produces a standalone, testable output:

```
.docx → DocxExtractor → DocumentParser → HtmlRenderer → HTML
          (unzip)         (XML→model)      (model→HTML)
```

**High-level entry point:** `DocxConverter.convert(Path, ConversionConfig)` chains all three stages. The CLI (`CliRunner`) is a thin picocli wrapper around it.

**Style resolution (DocumentParser):** Two-phase at parse time — first `mergeWithParents()` walks `basedOn` chains, then `resolveThemeInStyles()` replaces all theme references (fonts, colors) with concrete values. `docDefaults` are injected as a synthetic base style for all root styles. The renderer receives fully-resolved styles — it never looks up themes or style chains.

**Border model:** Under `border-collapse:collapse`, CSS conflict resolution causes table-level borders to override cell-level `border:none`. Therefore `StyleMapper.tableStyle()` outputs `border: none` on `<table>` — all border widths/colors are on cells only. `DocumentParser.parseTableCell()` assigns borders by position: edge cells get table outer borders (`isTopRow→tblTop`, etc.), interior cells get `insideH`/`insideV`. Cell-level `tcBorders` can override any side; `val="nil"` or `val="none"` explicitly suppresses it to `BorderSpec.NONE`. For `rowspan>1` cells, vMerge continue rows' `tcBorders` are merged in reverse order.

**Model tree:** Marker interfaces `ContentBlock` and `ParagraphElement` — no visitor pattern, renderer uses `instanceof` dispatch. All model classes are `final` with immutable fields and `Collections.unmodifiableList()` wrapping.

```
DocumentModel
  ├── styles: Map<String, StyleDef>
  ├── numberingFormats: Map<String, String>   (numId → numFmt)
  ├── theme: ThemeDef
  └── content: List<ContentBlock>
        ├── ParagraphBlock (numId, ilvl, outlineLvl, elements: List<ParagraphElement>)
        │     ├── TextRun      (FontSpec, text, highlight, shading, superscript/subscript)
        │     ├── ImageElement (mediaPath, mime, width, height, wrapMode)
        │     ├── MathElement  (latex, mathml, imagePath)
        │     ├── HyperlinkElement (url, elements: List<ParagraphElement>)
        │     └── ShapeElement  (svg, width, height, children: List<ShapeElement>)  — recursive for groups
        └── TableBlock (rows, borders, insideH/V, visibility)
              └── TableRow → TableCell (paragraphs, colspan/rowspan, per-side BorderSpec, bgColor, visibility)
```

**Table merge algorithm (three-pass):**
1. Scan all `w:tc` → record grid coordinates, gridSpan, vMerge status
2. For each vMerge restart cell, scan downward counting continue cells → actual rowspan
3. Build `TableCell` objects with computed position flags (`isTopRow`, `isBottomRow`, `isLeftCol`, `isRightCol`)

## Key Namespaces (OOXML)

| Prefix | URI | Used in |
|--------|-----|---------|
| `w:` | `http://schemas.openxmlformats.org/wordprocessingml/2006/main` | DocumentParser |
| `r:` | `http://schemas.openxmlformats.org/officeDocument/2006/relationships` | DocumentParser (hyperlinks, image refs) |
| `m:` | `http://schemas.openxmlformats.org/officeDocument/2006/math` | DocumentParser, OmmlToLatexConverter |
| `wp:` | `http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing` | DocumentParser (image extents, anchors) |
| `a:` | `http://schemas.openxmlformats.org/drawingml/2006/main` | DocumentParser, ThemeParser (blips, colors, shapes) |
| `v:` | `urn:schemas-microsoft-com:vml` | DocumentParser (legacy VML shapes/imagedata) |
| `mc:` | `http://schemas.openxmlformats.org/markup-compatibility/2006` | DocumentParser (AlternateContent) |
| `wps:` | `http://schemas.microsoft.com/office/word/2010/wordprocessingShape` | DocumentParser (DrawingML shape geometry) |
| `wpg:` | `http://schemas.microsoft.com/office/word/2010/wordprocessingGroup` | DocumentParser (DrawingML group shapes) |

## Rendering Details

**Heading resolution (HtmlRenderer priority):**
1. Paragraph's own `outlineLvl` (0-5 → h1-h6)
2. Walk style `basedOn` chain for `outlineLvl`
3. Pattern-match style ID: `"Heading1"`-`"Heading6"`, `"标题1"`-`"标题6"`, or WPS `"2"`-`"7"`
4. Pattern-match style name similarly
5. Default to `<p>`

**Hyperlinks:** Hardcoded `color: #0563C1; text-decoration: underline;` (Word default blue). Null/empty URLs render as plain text.

**Math rendering cascade:**
1. `imagePath` exists → base64 `<img>` with `data-latex` for MathJax
2. `mathml` exists → inline MathML XML
3. `latexRenderUrl` configured → online service URL
4. Fallback → `<span class="math">` with `data-latex` for frontend MathJax pickup

**Shapes:** Inline SVG. `presetToSvgPath()` maps 15 known presets; unknown → `<rect>`. Group shapes recurse children with `chOff`/`chExt` coordinate scaling.

**Font-family construction (StyleMapper):** Latin font first, East-Asian as comma-separated fallback (single-quoted). Duplicates omitted. If only East-Asian exists, it becomes primary.

**Image handling (three classes):**
- `DocumentParser` resolves references via `RelsParser`, produces `ImageElement`
- `ImageHandler` converts to base64 data-URI or copies to output dir (with path-traversal validation)
- `WmfConverter` converts WMF/EMF to PNG: tries ImageMagick (configurable via `docx2html.imagemagick.path` system property) → PowerShell+System.Drawing → none

## Critical DOM Traversal Pattern

`DocumentParser.nextSibling()` finds the **next direct child** element sibling (skipping text nodes), while `getElementsByTagNameNS` searches **all descendants**. Use `firstChild`/`firstChildNS`/`nextSibling` when you need a direct child (e.g., `w:tcPr` under `w:tc`), otherwise nested elements of the same name will be incorrectly matched (e.g., a `wps:wsp` inside `wpg:wgp`).

When processing inline content, a single `w:r` can contain mixed `w:t`, `w:drawing`, `w:pict`, `m:oMath`, and `mc:AlternateContent`. The `extractRunContent` method splits these into separate `ParagraphElement` objects preserving document order.

Drawing priority: raster image (`a:blip`) > shape group (`wpg:wgp`) > single shape (`wps:wsp`). AlternateContent: tries `mc:Choice` (DrawingML) first, falls back to `mc:Fallback` (VML).

## Unit Conversion Rules

- **Font size:** `w:sz` is half-points → divide by 2 for pt
- **Border width:** `w:sz` on borders is eighths-of-a-point → divide by 8 for pt
- **Indentation/margins:** twips → divide by 20 for pt
- **Table/cell width:** `w:type="pct"` → divide by 50 for CSS %; `w:type="dxa"` → divide by 20 for pt
- **Image/shape dimensions:** EMUs → divide by 9525 for approximate px
- **Shape stroke width:** `a:ln w` is EMUs → divide by 12700 for pt
- **VML sizes:** pt/in/cm/mm/px — converted to EMUs internally
- **Color values:** `auto` means default to `#000000` (black)

## Security

All XML parsers must set `disallow-doctype-decl=true` and provide a no-op `EntityResolver` to prevent XXE attacks. This is enforced in `DocumentParser`, `StylesParser`, `ThemeParser`, `RelsParser`, and `NumberingParser`. `DocxExtractor` validates zip entry paths against the temp directory root to prevent zip-slip. `ImageHandler.copyToDir` validates output paths against the target directory root to prevent path traversal.

## Dependencies

Three runtime dependencies (intentionally minimal — zero XML/HTML/image-processing libraries):
- **picocli 4.7.6** — CLI argument parsing
- **commons-io 2.18.0** — `FileUtils.deleteDirectory()` in cleanup

One test dependency:
- **JUnit Jupiter 5.11.4**

## Testing

Tests use `TestDocxBuilder` (in `src/test/java/cn/p4u/smart/util/`) to programmatically construct valid .docx ZIP files. It implements `AutoCloseable` and cleans up the `.docx` temp file on close. The extracted directory is cleaned up by `DocxExtractor.cleanup()` in test `finally` blocks. Supports `addContentTypes()`, `addRels()`, `addDocument()`, `addDocumentRels()`, `addStyles()`, `addTheme()`, and `addMedia()`.
