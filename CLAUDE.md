# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

**Prerequisite:** JDK 21 must be used to run Maven. The system default Java is JDK 21, so always set `JAVA_HOME`:
```bash
export JAVA_HOME=/c/DevRepo/jdk/dragonwell-21.0.6.0.6+7-GA
```

- **Build:** `mvn compile`
- **All tests:** `mvn test`
- **Single test class:** `mvn test -Dtest=DocumentParserTest`

## JDK 21

Source and target are set to `21` in pom.xml. Modern Java features — streams, lambdas, records, sealed types, `var`, text blocks, pattern matching — are all available. Previously, `Jdk8Helpers` backported JDK 9/11 APIs for JDK 8 compatibility; it has been removed. Use the standard `Files.writeString()`, `Files.readString()`, `OutputStream.nullOutputStream()`, and `InputStream.transferTo()` directly.

## Architecture

Three-phase pipeline where each stage produces a standalone, testable output:

```
.docx → DocxExtractor → DocumentParser → HtmlRenderer → HTML
          (unzip)         (XML→model)      (model→HTML)
```

**High-level entry point:** `DocxConverter.convert(InputStream, ConversionConfig)` chains all three stages and returns the HTML string. `ConversionConfig.tmpDir()` selects the temporary-directory root, and `ExtractedDocx` automatically cleans the unique generated child. Prefer `ConversionConfig.builder()` when several optional settings are needed.

**Style resolution (DocumentParser):** Two-phase at parse time — first `mergeWithParents()` walks `basedOn` chains, then `resolveThemeInStyles()` replaces all theme references (fonts, colors) with concrete values. `docDefaults` are injected as a synthetic base style for all root styles. The renderer receives fully-resolved styles — it never looks up themes or style chains.

**Property inheritance from styles (DocumentParser):** When inline `w:pPr`/`w:rPr` omits a property, the parser falls back to the style referenced by `w:pStyle`/`w:rStyle`.

- **Paragraph properties (alignment, outlineLvl, indentation):** `parseParagraph()` checks inline `w:pPr` first; if `null`, reads from `styleDef.paragraphProps()` (e.g., `jc` → alignment) or `styleDef.rawParaAttrs()` (e.g., `ind` → indentation). The style's `outlineLvl()` field is used directly since it's already merged in `mergeWithParents()`.
- **Run boolean properties (bold, italic, underline, strike):** `parseRun()` and `parseRunWithText()` use `hasElement()` to detect whether the element exists in inline `w:rPr`. If present, use `isBoolPropEnabled()` to evaluate its `w:val`; if absent, call `resolveBoolPropFromStyles()` which walks run style chain → paragraph style chain → returns `false`. This ensures `<w:b w:val="0"/>` (explicitly off) overrides any style-level bold.
- **Font properties:** Already inherited via `resolveInheritedFontSpec()`, which walks run style chain → paragraph style chain → docDefaults.

**Border model:** Under `border-collapse:collapse`, CSS conflict resolution causes table-level borders to override cell-level `border:none`. Therefore `StyleMapper.tableStyle()` outputs `border: none` on `<table>` — all border widths/colors are on cells only. `DocumentParser.parseTableCell()` assigns borders by position: edge cells get table outer borders (`isTopRow→tblTop`, etc.), interior cells get `insideH`/`insideV`. Cell-level `tcBorders` can override any side; `val="nil"` or `val="none"` explicitly suppresses it to `BorderSpec.NONE`. For `rowspan>1` cells, vMerge continue rows' `tcBorders` are merged in reverse order.

**Model tree:** Marker interfaces `ContentBlock` and `ParagraphElement` — no visitor pattern, renderer uses `instanceof` dispatch. All model classes are `final` with immutable fields and `Collections.unmodifiableList()` wrapping.

```
DocumentModel
  ├── styles: Map<String, StyleDef>
  ├── numberingFormats: Map<String, String>   (numId → numFmt)
  ├── theme: ThemeDef
  └── content: List<ContentBlock>
        ├── ParagraphBlock (styleId, alignment, outlineLvl, indentation, numId, ilvl, elements: List<ParagraphElement>)
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

**Font-family construction (StyleMapper):** When the Latin and East-Asian fonts differ, `HtmlRenderer.renderTextRun()` splits text into CJK and non-CJK segments by Unicode range. CJK segments use East-Asian font first (`font-family: '宋体', 'Times New Roman'`); non-CJK segments use Latin font first (`font-family: 'Times New Roman', '宋体'`). This matches Word's per-Unicode-range font selection. If only one font exists, it is used directly. Duplicates omitted.

**Image handling (three classes):**
- `DocumentParser` resolves references via `RelsParser`, produces `ImageElement`
- `ImageHandler` converts to base64 data-URI or copies to output dir (with path-traversal validation)
- `WmfConverter` converts WMF/EMF to PNG according to `ConversionConfig.wmfStrategy()`: `AUTO`, `IMAGEMAGICK`, `POWERSHELL`, or `NONE`. `ConversionConfig.imageMagickPath()` selects the ImageMagick executable per conversion; the legacy `docx2html.imagemagick.path` system property remains a fallback.

## Critical DOM Traversal Pattern

`DocumentParser.nextSibling()` finds the **next direct child** element sibling (skipping text nodes), while `getElementsByTagNameNS` searches **all descendants**. Use `firstChild`/`firstChildNS`/`nextSibling` when you need a direct child (e.g., `w:tcPr` under `w:tc`), otherwise nested elements of the same name will be incorrectly matched (e.g., a `wps:wsp` inside `wpg:wgp`).

When processing inline content, a single `w:r` can contain mixed `w:t`, `w:drawing`, `w:pict`, `m:oMath`, and `mc:AlternateContent`. The `extractRunContent` method splits these into separate `ParagraphElement` objects preserving document order.

Drawing priority: raster image (`a:blip`) > shape group (`wpg:wgp`) > single shape (`wps:wsp`). AlternateContent: tries `mc:Choice` (DrawingML) first, falls back to `mc:Fallback` (VML).

## OOXML Boolean Property Handling

OOXML boolean properties (`w:b`, `w:i`, `w:strike`, `w:u`) follow a specific convention:
- Element **present without** `w:val` → **enabled** (the element's existence implies "on")
- `w:val="1"` or `w:val="true"` → **enabled**
- `w:val="0"` or `w:val="false"` or `w:val="off"` → **disabled** (explicitly off)
- For `w:u` specifically: `w:val="none"` or `w:val="nil"` → **disabled** (no underline)

**`isBoolPropEnabled(Element parent, String ns, String localName)`** — Use this helper to correctly evaluate OOXML boolean properties. It reads the element's `w:val` attribute and applies the rules above. Do NOT use `hasElement()` alone — it ignores `w:val` and would treat `<w:b w:val="0"/>` as bold=true.

**`resolveBoolPropFromStyles(String runStyleId, String paraStyleId, String propName)`** — Use when a boolean property is absent from inline `w:rPr`. Walks run style basedOn chain then paragraph style basedOn chain. Returns `false` only if neither chain defines the property with a truthy value.

**`parseIndentationFromAttrs(Map<String, String> attrs)`** — Variant of `parseIndentation(Element)` that takes an attribute map (from style's `rawParaAttrs`), used when indentation is inherited from a paragraph style rather than inline `w:ind`.

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

One direct runtime dependency:
- **aliyun-sdk-oss 3.17.4** — Aliyun OSS image resolver implementation

One test dependency:
- **JUnit Jupiter 5.11.4**

## Testing

Tests use `TestDocxBuilder` (in `src/test/java/cn/p4u/smart/util/`) to programmatically construct valid .docx ZIP files. It implements `AutoCloseable` and cleans up the `.docx` temp file on close. The extracted directory is cleaned up by `DocxExtractor.cleanup()` in test `finally` blocks. Supports `addContentTypes()`, `addRels()`, `addDocument()`, `addDocumentRels()`, `addStyles()`, `addTheme()`, and `addMedia()`.

## Packaging (Windows exe)

The `native` profile uses jpackage (bundled with JDK 21) to create a self-contained Windows application with an embedded JRE.

**Prerequisite:** JDK 21 with jpackage. Use the same JAVA_HOME as for building:
```bash
export JAVA_HOME=/c/DevRepo/jdk/dragonwell-21.0.6.0.6+7-GA
```

```bash
mvn -Pnative package
```

Output: `target/dist/docx2html/` directory containing `docx2html.exe` and bundled runtime.
