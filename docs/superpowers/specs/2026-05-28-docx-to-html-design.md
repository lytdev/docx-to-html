# docxToHtml4j Design Spec

## 1. Overview

A Java 21 library + CLI that converts `.docx` files to HTML with high-fidelity style preservation. All CSS is inlined as element `style` attributes.

**Input**: `.docx` file (Office Open XML)  
**Output**: HTML string/file with inline CSS

### Key Decisions

| Decision | Choice |
|----------|--------|
| Project type | Library JAR + CLI entry point |
| Java version | 21 |
| Architecture | DOM parse → intermediate model → HTML render |
| Formula display | Embedded image + `data-latex` attribute |
| Image embedding | Configurable: base64 inline or external file link |
| XML parsing | JDK javax.xml (DOM) |
| Formula conversion | fmath or snuggletex for OMML → LaTeX |

## 2. Architecture

```
.docx file
   ↓
DocxExtractor (unzip to temp directory)
   ↓
DocumentParser (DOM parse document.xml + rels + styles)
   ↓
DocumentModel (intermediate tree)
   ↓
HtmlRenderer (traverse model, emit HTML + inline CSS)
   ↓
HTML string / file
```

Three core components:

- **DocxExtractor**: Unzips docx to a system temp directory. Caller is responsible for cleanup.
- **DocumentParser**: Reads `word/document.xml`, `word/_rels/document.xml.rels`, and `word/styles.xml`. Builds a `DocumentModel`. OMML → LaTeX conversion happens here.
- **HtmlRenderer**: Traverses the `DocumentModel`, emitting HTML fragments per node. All styling is rendered as inline `style` attributes.

CLI entry point chains: `DocxExtractor → DocumentParser → HtmlRenderer`, outputs to file or stdout.

## 3. Intermediate Model

```java
// Top-level
DocumentModel
  ├── styles: Map<String, StyleDef>
  ├── content: List<ContentBlock>

// Content blocks — paragraphs and tables are top-level siblings
sealed interface ContentBlock permits ParagraphBlock, TableBlock {}

// Paragraph
ParagraphBlock
  ├── styleId: String
  ├── alignment: String
  ├── indentation: Indentation
  ├── elements: List<ParagraphElement>

// Paragraph elements
sealed interface ParagraphElement
  permits TextRun, ImageElement, MathElement, HyperlinkElement {}

TextRun
  ├── text: String
  ├── font: FontSpec
  ├── bold / italic / underline / strike: boolean
  ├── highlight / shading: String
  ├── superscript / subscript: boolean
  ├── styleId: String

ImageElement
  ├── mediaPath: String
  ├── mimeType: String
  ├── width / height: int
  ├── wrapMode: WrapMode

MathElement
  ├── latex: String
  ├── imagePath: String
  ├── mimeType: String

HyperlinkElement
  ├── url: String
  ├── runs: List<TextRun>

// Table
TableBlock
  ├── rows: List<TableRow>
  ├── width: String
  ├── borderWidth / borderColor: String
  ├── visibility: boolean

TableRow
  ├── cells: List<TableCell>
  ├── height: String

TableCell
  ├── paragraphs: List<ParagraphBlock>
  ├── colspan / rowspan: int
  ├── width: String
  ├── borderWidth / borderColor / bgColor: String
  ├── visibility: boolean

// Value objects
FontSpec(name: String, size: String, color: String)  // size e.g. "12pt"
Indentation(left: String, right: String, firstLine: String)
StyleDef(styleId, name, basedOn, runProps, paragraphProps)
WrapMode  // enum: INLINE, LEFT, RIGHT, TOP_AND_BOTTOM
```

Design notes:
- Java 21 `sealed interface` for type-safe element matching
- `ParagraphBlock` and `TableBlock` are siblings matching `w:p` / `w:tbl`
- `TableBlock` recursively contains `ParagraphBlock` for cell content
- `MathElement` holds both LaTeX and image path; renderer uses image for display, `data-latex` for metadata

## 4. DocumentParser Logic

### Phase 1: Load auxiliary files
- Parse `word/_rels/document.xml.rels` → `rId → target` map (images → `media/`, hyperlinks → external URLs)
- Parse `word/styles.xml` → `styleId → StyleDef` map

### Phase 2: Parse document.xml
- DOM load `word/document.xml`
- Iterate `w:body` direct children:
  - `w:p` → `ParagraphBlock`: iterate children to build `ParagraphElement` list
  - `w:tbl` → `TableBlock`: recursively parse rows/cells
- Within `w:p`: each `w:r` → `TextRun`, `w:hyperlink` → `HyperlinkElement`, `m:oMath` → `MathElement`, `w:drawing` → `ImageElement`

### Phase 3: Formula processing
- Extract OMML XML subtree from `m:oMath`
- Check `word/_rels/document.xml.rels` for formula image relationships (some docx embed rendered formula images as OLE objects)
- If image exists, record path in `MathElement.imagePath`
- Convert OMML → LaTeX using XSLT or dedicated library, store in `MathElement.latex`

### Style merging rule
docx style inheritance chain: paragraph style `w:rPr` ← character style `w:rStyle` ← run's own `w:rPr`. Later declarations override earlier ones.

## 5. HtmlRenderer Logic

### Paragraph → `<p>`
```html
<p style="text-align: center; text-indent: 2em;">
  <span style="font-family: SimSun; font-size: 12pt; color: #000000; font-weight: bold;">text</span>
</p>
```
- Paragraph-level attributes (alignment, indentation) on `<p>`
- Each `TextRun` → `<span>` with full inline style
- Adjacent `TextRun` with identical styles optionally merged into one `<span>`

### Image → `<img>`
- **Base64 mode**: `<img src="data:{mimeType};base64,{data}" style="width:...;height:...;float:left;">`
- **File link mode**: `<img src="{relativePath}" ...>`, images copied to output directory

### Math → `<img data-latex>`
```html
<img src="data:image/png;base64,..." data-latex="E=mc^2" style="vertical-align: middle;">
```
- Image for visual display (consistent rendering guarantees)
- `data-latex` attribute stores LaTeX for frontend MathJax/KaTeX upgrade

### Table → `<table>`
```html
<table style="width:100%; border:1px solid #000; border-collapse:collapse; visibility:visible;">
  <tr style="height:30px;">
    <td colspan="2" style="width:50%; border:1px solid #000; background-color:#eee; visibility:visible;">
      <p ...>cell content</p>
    </td>
  </tr>
</table>
```
- Visibility via `visibility: visible/hidden`
- `border-collapse: collapse` for merged borders

### Hyperlink → `<a>`
```html
<a href="https://..." style="color: #0563C1; text-decoration: underline;">link text</a>
```
- Default Word hyperlink blue + underline

### Font size conversion
docx `w:sz` value is in half-points → `value / 2` pt. Example: `w:sz val="24"` → `font-size: 12pt`.

## 6. Style Mapping Reference

| docx element | CSS property | Example |
|---|---|---|
| `w:rFonts w:ascii` / `w:hAnsi` | `font-family` | `"SimSun", serif` |
| `w:sz` / `w:szCs` (half-point) | `font-size` | `12pt` |
| `w:color w:val` | `color` | `#FF0000` |
| `w:b` | `font-weight` | `bold` |
| `w:i` | `font-style` | `italic` |
| `w:u w:val="single"` | `text-decoration` | `underline` |
| `w:strike` | `text-decoration` | `line-through` |
| `w:highlight w:val` | `background-color` | `yellow` → `#FFFF00` |
| `w:shd w:fill` | `background-color` | `#CCCCCC` |
| `w:vertAlign val="superscript"` | `vertical-align: super; font-size: smaller` |
| `w:vertAlign val="subscript"` | `vertical-align: sub; font-size: smaller` |

## 7. Dependencies

| Dependency | Purpose | Notes |
|---|---|---|
| JDK javax.xml | DOM/XML parsing | Built-in, no extra dep |
| `fmath` or `snuggletex` | OMML → LaTeX | Dedicated formula conversion |
| `picocli` | CLI argument parsing | Lightweight framework |
| JUnit 5 | Unit testing | Replace skeleton JUnit 3.8 |
| `commons-io` | Temp dir/file cleanup | IO utilities |

Logging: `java.util.logging` (JDK built-in) to avoid external logging framework dependency.

## 8. Error Handling

| Scenario | Behavior |
|---|---|
| Unzip / XML format error | Throw `DocxConversionException` with file path and cause |
| Missing rels file | Non-fatal: degrade image/hyperlink features, log warning |
| OMML → LaTeX failure | `MathElement.latex` = empty, image still available |
| Missing image file | Render empty `<span>` placeholder + log warning, no abort |
| Temp directory cleanup | Caller responsibility; CLI uses try-with-resources / shutdown hook |

## 9. CLI Interface

```
java -jar docxToHtml4j.jar [options] <input.docx>

Options:
  -o, --output <file>      Output HTML file (default: stdout)
  --image-mode <mode>      Image embedding: base64 (default) or link
  --image-dir <dir>        Directory for linked images (default: ./images)
  --keep-temp              Keep extracted temp directory for debugging
```

## 10. Testing Strategy

- Unit tests per component: `DocxExtractorTest`, `DocumentParserTest`, `HtmlRendererTest`
- Integration test: full pipeline `.docx → HTML`, compare against expected HTML fixture
- Test documents: hand-crafted `.docx` files covering each feature (fonts, images, formulas, tables, hyperlinks, nested structures)
