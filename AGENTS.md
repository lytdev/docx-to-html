# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Build & Test Commands

**Prerequisite:** Maven must run on JDK 21. Do not rely on the shell's default `java`; verify with `mvn -version` before building. The repository's Windows build script expects the JDK at `C:\DevRepo\jdk\dragonwell-21.0.6.0.6+7-GA` (Git Bash form shown below):
```bash
export JAVA_HOME=/c/DevRepo/jdk/dragonwell-21.0.6.0.6+7-GA
```

- **Build:** `mvn compile`
- **All tests:** `mvn test`
- **Single test class:** `mvn test -Dtest=DocumentParserTest`
- **Run Swing GUI:** `mvn exec:java -Dexec.mainClass="cn.p4u.smart.gui.GuiRunner"`
- **Build Windows app image:** `build-exe.bat` (or `mvn -Pnative package` with JDK 21 configured)

On PowerShell, set the environment variable with:

```powershell
$env:JAVA_HOME = 'C:\DevRepo\jdk\dragonwell-21.0.6.0.6+7-GA'
```

## JDK 21

Source and target are set to `21` in pom.xml. Modern Java features — streams, lambdas, records, sealed types, `var`, text blocks, pattern matching — are all available. Previously, `Jdk8Helpers` backported JDK 9/11 APIs for JDK 8 compatibility; it has been removed. Use the standard `Files.writeString()`, `Files.readString()`, `OutputStream.nullOutputStream()`, and `InputStream.transferTo()` directly.

## Architecture

Three-phase pipeline where each stage produces a standalone, testable output:

```
.docx → DocxExtractor → DocumentParser → HtmlRenderer → HTML
          (unzip)         (XML→model)      (model→HTML)
```

**High-level entry point:** `DocxConverter.convert(InputStream, ConversionConfig)` chains all three stages and returns the HTML string. Convenience overloads accept an `ImageUriResolver` directly or use the default Base64 resolver. `ConversionConfig.tmpDir()` selects a temporary-directory root; each extraction uses a unique `docx2html-*` child. The converter consumes but does not close the caller-owned stream. `ExtractedDocx` makes the generated child AutoCloseable, so the pipeline cleans it on success and failure. Prefer `ConversionConfig.builder()` when several optional settings are needed.

**Refactoring patterns:** `DocxConverter` and `WmfConverter` are facades; `ConversionConfig.Builder` handles optional parameters; `ImageUriResolver` and `WmfRasterizer` are strategies; `WmfRasterizerFactory` selects WMF implementations; `MathHtmlRenderer` is an ordered responsibility chain; `SecureXmlDocuments` centralizes secure DOM construction. See `DESIGN_PATTERNS.md` for the beginner-oriented guide.

**Style resolution (DocumentParser):** Two-phase at parse time — first `mergeWithParents()` walks `basedOn` chains, then `resolveThemeInStyles()` replaces all theme references (fonts, colors) with concrete values. `docDefaults` are injected as a synthetic base style for all root styles. The renderer receives fully-resolved styles — it never looks up themes or style chains.

**Property inheritance from styles (DocumentParser):** When inline `w:pPr`/`w:rPr` omits a property, the parser falls back to the style referenced by `w:pStyle`/`w:rStyle`.

- Paragraph alignment, outline level, and indentation use inline values first, then the resolved paragraph style.
- Run booleans (bold, italic, underline, strike) use the inline value when the element is present; otherwise `resolveBoolPropFromStyles()` checks the run style and then the paragraph style.
- Fonts use `resolveInheritedFontSpec()`, walking run style → paragraph style → `docDefaults`.

**Border model:** Under `border-collapse:collapse`, CSS conflict resolution causes table-level borders to override cell-level `border:none`. Therefore `StyleMapper.tableStyle()` outputs `border: none` on `<table>` — all border widths/colors are on cells only. `DocumentParser.parseTableCell()` assigns borders by position: edge cells get table outer borders (`isTopRow→tblTop`, etc.), interior cells get `insideH`/`insideV`. Cell-level `tcBorders` can override any side; `val="nil"` or `val="none"` explicitly suppresses it to `BorderSpec.NONE`. For `rowspan>1` cells, vMerge continue rows' `tcBorders` are merged in reverse order.

**Model tree:** Marker interfaces `ContentBlock` and `ParagraphElement` — no visitor pattern, renderer uses `instanceof` dispatch. Model classes are `final` value-style containers, but many constructors retain collection references directly. Do not assume defensive copies or unmodifiable collections; preserve current API behavior unless immutability is an explicit change.

```
DocumentModel
  ├── styles: Map<String, StyleDef>
  ├── numberingFormats: Map<String, String>   (numId → numFmt)
  ├── theme: ThemeDef
  └── content: List<ContentBlock>
        ├── ParagraphBlock (styleId, alignment, outlineLvl, indentation, numId, ilvl, elements)
        │     ├── TextRun      (FontSpec, text, highlight, shading, superscript/subscript)
        │     ├── ImageElement (mediaPath, mime, width, height, wrapMode, altText)
        │     ├── MathElement  (latex, mathml, imagePath)
        │     ├── HyperlinkElement (url, runs: List<TextRun>)
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

The cascade is implemented by `MathHtmlRenderer` as an ordered chain. Keep the handler order stable unless changing output behavior is explicitly requested.

**Shapes:** Inline SVG. `presetToSvgPath()` maps 15 known presets; unknown → `<rect>`. Group shapes recurse children with `chOff`/`chExt` coordinate scaling.

**Font-family construction:** When Latin and East-Asian fonts differ, `HtmlRenderer.renderTextRun()` splits text into CJK and non-CJK Unicode segments. CJK segments put the East-Asian font first; non-CJK segments put the Latin font first. Duplicates are omitted, and a single available font is used directly.

**Image handling:**
- `DocumentParser` resolves references via `RelsParser`, produces `ImageElement`
- `ImageUriResolver` is the pluggable rendering boundary; `ConversionConfig.defaults()` uses `Image2Base64Resolver`
- `Image2OssResolver` uploads images to Aliyun OSS and returns HTTPS URLs; callers that construct it own its lifecycle and must close it
- `ImageHandler` now only delegates WMF/EMF format detection to `WmfConverter`; it no longer encodes or copies images
- `WmfConverter` converts WMF/EMF to PNG according to `ConversionConfig.wmfStrategy()`: `AUTO`, `IMAGEMAGICK`, `POWERSHELL`, or `NONE`. `ConversionConfig.imageMagickPath()` selects the ImageMagick executable per conversion; the legacy `docx2html.imagemagick.path` system property remains a fallback.

Applications select image behavior by passing an `ImageUriResolver` to `DocxConverter.convert(...)` or through `ConversionConfig`. Do not restore the removed `ImageMode`, `imageMode()`, or `imageOutputDir()` APIs.

## Critical DOM Traversal Pattern

`DocumentParser.nextSibling()` finds the **next direct child** element sibling (skipping text nodes), while `getElementsByTagNameNS` searches **all descendants**. Use `firstChild`/`firstChildNS`/`nextSibling` when you need a direct child (e.g., `w:tcPr` under `w:tc`), otherwise nested elements of the same name will be incorrectly matched (e.g., a `wps:wsp` inside `wpg:wgp`).

When processing inline content, a single `w:r` can contain mixed `w:t`, `w:drawing`, `w:pict`, `m:oMath`, and `mc:AlternateContent`. The `extractRunContent` method splits these into separate `ParagraphElement` objects preserving document order.

Drawing priority: raster image (`a:blip`) > shape group (`wpg:wgp`) > single shape (`wps:wsp`). AlternateContent: tries `mc:Choice` (DrawingML) first, falls back to `mc:Fallback` (VML).

## OOXML Boolean Property Handling

For `w:b`, `w:i`, `w:strike`, and `w:u`:

- An element present without `w:val`, or with `w:val="1"`/`"true"`, is enabled.
- `w:val="0"`, `"false"`, or `"off"` is explicitly disabled.
- For underline, `w:val="none"` or `"nil"` is also disabled.

Use `isBoolPropEnabled()` to evaluate an inline property; `hasElement()` alone is insufficient because it would treat `<w:b w:val="0"/>` as enabled. Use `resolveBoolPropFromStyles()` only when the inline property is absent. `parseIndentationFromAttrs()` handles indentation inherited from a style's `rawParaAttrs`.

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

All OOXML DOM parsing must go through `SecureXmlDocuments.parse()`, which disables DOCTYPE, external entities, external DTDs, and external schemas. `DocxExtractor` validates zip entry paths against the temp directory root to prevent zip-slip.

## Dependencies

Direct runtime dependencies:
- **jsoup 1.22.1** — HTML tree processing for adjacent image captions (`FigureCaptionProcessor`)
- **aliyun-sdk-oss 3.17.4** — Aliyun OSS image resolver implementation

One test dependency:
- **JUnit Jupiter 5.11.4**

## Testing

Tests use `TestDocxBuilder` (in `src/test/java/cn/p4u/smart/util/`) to programmatically construct valid .docx ZIP files. It implements `AutoCloseable` and cleans up the `.docx` temp file on close. The extracted directory is cleaned up by `DocxExtractor.cleanup()` in test `finally` blocks. Supports `addContentTypes()`, `addRels()`, `addDocument()`, `addDocumentRels()`, `addStyles()`, `addTheme()`, and `addMedia()`.

## Packaging (Windows exe)

The `native` profile uses jpackage (bundled with JDK 21) to create a self-contained Windows application with an embedded JRE.

**Prerequisite:** JDK 21 with jpackage. Use the same `JAVA_HOME` as for building:
```bash
export JAVA_HOME=/c/DevRepo/jdk/dragonwell-21.0.6.0.6+7-GA
```

```bash
mvn -Pnative package
```

On the repository's Windows setup, prefer `build-exe.bat`; it supplies the Maven and jpackage paths explicitly. The output is `target/dist/docx2html/`, containing `docx2html.exe` and a bundled runtime. The packaged application starts `GuiRunner`; the ordinary jar is a library and has no main class.
