package cn.p4u.smart.parser;

import cn.p4u.smart.DocxConversionException;
import cn.p4u.smart.model.*;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

public final class DocumentParser {

    private static final Logger LOG = Logger.getLogger(DocumentParser.class.getName());

    private static final String W  = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String R  = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String M  = "http://schemas.openxmlformats.org/officeDocument/2006/math";
    private static final String WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
    private static final String A  = "http://schemas.openxmlformats.org/drawingml/2006/main";

    private final Map<String, RelsParser.Rel> rels;
    private final Map<String, StyleDef> styles;

    private DocumentParser(Path extractedDir) {
        this.rels = RelsParser.parse(extractedDir.resolve("word/_rels/document.xml.rels"));
        this.styles = StylesParser.parse(extractedDir.resolve("word/styles.xml"));
    }

    public static DocumentModel parse(Path extractedDir) {
        var parser = new DocumentParser(extractedDir);
        var docFile = extractedDir.resolve("word/document.xml");
        if (!Files.exists(docFile)) {
            throw new DocxConversionException("document.xml not found in extracted directory");
        }
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(new java.io.StringReader("")));
            var doc = builder.parse(docFile.toFile());

            var bodyNodes = doc.getElementsByTagNameNS(W, "body");
            if (bodyNodes.getLength() == 0) {
                return new DocumentModel(parser.styles, List.of());
            }
            var body = (Element) bodyNodes.item(0);
            var contentBlocks = new ArrayList<ContentBlock>();

            var children = body.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (!(child instanceof Element el)) continue;
                String localName = el.getLocalName();
                if (W.equals(el.getNamespaceURI())) {
                    switch (localName) {
                        case "p" -> contentBlocks.add(parser.parseParagraph(el));
                        case "tbl" -> contentBlocks.add(parser.parseTable(el));
                    }
                }
            }
            return new DocumentModel(parser.styles, Collections.unmodifiableList(contentBlocks));
        } catch (DocxConversionException e) {
            throw e;
        } catch (Exception e) {
            throw new DocxConversionException("Failed to parse document.xml", e);
        }
    }

    // ---- Paragraph ----

    private ParagraphBlock parseParagraph(Element pEl) {
        String styleId = null;
        String alignment = null;
        Indentation indentation = null;
        var elements = new ArrayList<ParagraphElement>();

        var children = pEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element el)) continue;
            String ns = el.getNamespaceURI();
            String localName = el.getLocalName();

            if (W.equals(ns) && "pPr".equals(localName)) {
                styleId = getAttrVal(el, W, "pStyle", "val");
                alignment = getAttrVal(el, W, "jc", "val");
                var indNodes = el.getElementsByTagNameNS(W, "ind");
                if (indNodes.getLength() > 0) {
                    indentation = parseIndentation((Element) indNodes.item(0));
                }
            } else if (W.equals(ns) && "r".equals(localName)) {
                elements.add(parseRun(el));
            } else if (W.equals(ns) && "hyperlink".equals(localName)) {
                elements.add(parseHyperlink(el));
            } else if (M.equals(ns) && ("oMath".equals(localName) || "oMathPara".equals(localName))) {
                elements.add(parseMath(el));
            } else if (W.equals(ns) && "drawing".equals(localName)) {
                var img = parseDrawing(el);
                if (img != null) elements.add(img);
            }
        }
        return new ParagraphBlock(styleId, alignment, indentation, Collections.unmodifiableList(elements));
    }

    private Indentation parseIndentation(Element indEl) {
        String left = indEl.getAttributeNS(W, "left");
        String right = indEl.getAttributeNS(W, "right");
        String firstLine = indEl.getAttributeNS(W, "firstLine");
        if (left.isEmpty()) left = null;
        if (right.isEmpty()) right = null;
        if (firstLine.isEmpty()) firstLine = null;
        return new Indentation(left, right, firstLine);
    }

    // ---- Text Run ----

    private TextRun parseRun(Element rEl) {
        String text = "";
        FontSpec font = null;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strike = false;
        String highlight = null;
        String shading = null;
        boolean superscript = false;
        boolean subscript = false;
        String runStyleId = null;

        var rPrNodes = rEl.getElementsByTagNameNS(W, "rPr");
        if (rPrNodes.getLength() > 0) {
            var rPr = (Element) rPrNodes.item(0);
            runStyleId = getAttrVal(rPr, W, "rStyle", "val");

            String fontName = null;
            String fontSize = null;
            String fontColor = null;
            var fontsNodes = rPr.getElementsByTagNameNS(W, "rFonts");
            if (fontsNodes.getLength() > 0) {
                var fontsEl = (Element) fontsNodes.item(0);
                fontName = fontsEl.getAttributeNS(W, "ascii");
                if (fontName.isEmpty()) fontName = null;
            }
            var szNodes = rPr.getElementsByTagNameNS(W, "sz");
            if (szNodes.getLength() > 0) {
                fontSize = ((Element) szNodes.item(0)).getAttributeNS(W, "val");
                if (fontSize.isEmpty()) fontSize = null;
            }
            var colorNodes = rPr.getElementsByTagNameNS(W, "color");
            if (colorNodes.getLength() > 0) {
                fontColor = ((Element) colorNodes.item(0)).getAttributeNS(W, "val");
                if (fontColor.isEmpty()) fontColor = null;
            }
            font = new FontSpec(fontName, fontSize, fontColor);

            bold = hasElement(rPr, W, "b");
            italic = hasElement(rPr, W, "i");

            var uNodes = rPr.getElementsByTagNameNS(W, "u");
            underline = uNodes.getLength() > 0;

            strike = hasElement(rPr, W, "strike");

            highlight = getAttrVal(rPr, W, "highlight", "val");
            var shdNodes = rPr.getElementsByTagNameNS(W, "shd");
            if (shdNodes.getLength() > 0) {
                shading = ((Element) shdNodes.item(0)).getAttributeNS(W, "fill");
                if (shading.isEmpty()) shading = null;
            }
            var vertNodes = rPr.getElementsByTagNameNS(W, "vertAlign");
            if (vertNodes.getLength() > 0) {
                String vertVal = ((Element) vertNodes.item(0)).getAttributeNS(W, "val");
                superscript = "superscript".equals(vertVal);
                subscript = "subscript".equals(vertVal);
            }
        }

        var tNodes = rEl.getElementsByTagNameNS(W, "t");
        if (tNodes.getLength() > 0) {
            text = tNodes.item(0).getTextContent();
        }

        return new TextRun(text, font, bold, italic, underline, strike,
                highlight, shading, superscript, subscript, runStyleId);
    }

    // ---- Hyperlink ----

    private HyperlinkElement parseHyperlink(Element hlEl) {
        String rId = hlEl.getAttributeNS(R, "id");
        String url = null;
        if (!rId.isEmpty() && rels.containsKey(rId)) {
            url = rels.get(rId).target();
        }
        var runs = new ArrayList<TextRun>();
        var children = hlEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el && W.equals(el.getNamespaceURI()) && "r".equals(el.getLocalName())) {
                runs.add(parseRun(el));
            }
        }
        return new HyperlinkElement(url, Collections.unmodifiableList(runs));
    }

    // ---- Math ----

    private MathElement parseMath(Element mathEl) {
        String latex = OmmlToLatexConverter.convert(mathEl);
        String imagePath = null;
        String mimeType = null;

        // Check for alternate content image for the formula
        var altNodes = mathEl.getElementsByTagNameNS(R, "id");
        for (int i = 0; i < altNodes.getLength(); i++) {
            String altRId = ((Attr) altNodes.item(i)).getValue();
            if (rels.containsKey(altRId)) {
                var rel = rels.get(altRId);
                imagePath = rel.target();
                mimeType = guessMimeType(imagePath);
                break;
            }
        }
        return new MathElement(latex, imagePath, mimeType);
    }

    // ---- Drawing / Image ----

    private ImageElement parseDrawing(Element drawingEl) {
        // Find a:blip with r:embed
        var blipNodes = drawingEl.getElementsByTagNameNS(A, "blip");
        String rEmbed = null;
        if (blipNodes.getLength() > 0) {
            rEmbed = ((Element) blipNodes.item(0)).getAttributeNS(R, "embed");
        }
        if (rEmbed == null || rEmbed.isEmpty()) return null;
        if (!rels.containsKey(rEmbed)) return null;

        String mediaPath = rels.get(rEmbed).target();
        String mimeType = guessMimeType(mediaPath);

        int width = 0;
        int height = 0;
        WrapMode wrapMode = WrapMode.INLINE;

        // Find extent for width/height
        var extentNodes = drawingEl.getElementsByTagNameNS(WP, "extent");
        if (extentNodes.getLength() > 0) {
            var extentEl = (Element) extentNodes.item(0);
            String cx = extentEl.getAttribute("cx");
            String cy = extentEl.getAttribute("cy");
            if (!cx.isEmpty()) width = (int) Math.round(Long.parseLong(cx) / 12700.0);
            if (!cy.isEmpty()) height = (int) Math.round(Long.parseLong(cy) / 12700.0);
        }

        // Determine wrap mode from parent element
        var parent = drawingEl.getParentNode();
        if (parent instanceof Element parentEl && W.equals(parentEl.getNamespaceURI())) {
            String parentLocal = parentEl.getLocalName();
            wrapMode = switch (parentLocal) {
                case "wrapNone" -> WrapMode.INLINE;
                case "wrapSquare" -> {
                    // Check wrapSide attribute or child element
                    var sideNodes = parentEl.getElementsByTagNameNS(WP, "wrapSquare");
                    if (sideNodes.getLength() > 0) {
                        String side = ((Element) sideNodes.item(0)).getAttribute("wrapText");
                        yield "left".equals(side) ? WrapMode.LEFT : WrapMode.RIGHT;
                    }
                    yield WrapMode.LEFT;
                }
                case "wrapTight" -> WrapMode.LEFT;
                case "wrapTopAndBottom" -> WrapMode.TOP_AND_BOTTOM;
                default -> WrapMode.INLINE;
            };
        }

        // Also check for wp:inline vs wp:anchor to distinguish inline vs floating
        var inlineNodes = drawingEl.getElementsByTagNameNS(WP, "inline");
        var anchorNodes = drawingEl.getElementsByTagNameNS(WP, "anchor");
        if (inlineNodes.getLength() > 0) {
            wrapMode = WrapMode.INLINE;
        } else if (anchorNodes.getLength() > 0) {
            if (wrapMode == WrapMode.INLINE) {
                // Default anchor to LEFT if not otherwise determined
                wrapMode = WrapMode.LEFT;
            }
            // Check for wrap elements inside anchor
            var anchor = (Element) anchorNodes.item(0);
            if (hasChildLocalName(anchor, WP, "wrapSquare")) wrapMode = WrapMode.LEFT;
            else if (hasChildLocalName(anchor, WP, "wrapTopAndBottom")) wrapMode = WrapMode.TOP_AND_BOTTOM;
            else if (hasChildLocalName(anchor, WP, "wrapTight")) wrapMode = WrapMode.LEFT;
        }

        return new ImageElement(mediaPath, mimeType, width, height, wrapMode);
    }

    // ---- Table ----

    private TableBlock parseTable(Element tblEl) {
        var rows = new ArrayList<TableRow>();

        // Table-level properties
        String tblWidth = null;
        var tblWNodes = tblEl.getElementsByTagNameNS(W, "tblW");
        if (tblWNodes.getLength() > 0) {
            var tblWEl = (Element) tblWNodes.item(0);
            String wVal = tblWEl.getAttributeNS(W, "w");
            String wType = tblWEl.getAttributeNS(W, "type");
            if ("pct".equals(wType) && !wVal.isEmpty()) {
                tblWidth = (Integer.parseInt(wVal) / 50) + "%";
            } else if ("dxa".equals(wType) && !wVal.isEmpty()) {
                tblWidth = (Integer.parseInt(wVal) / 20.0) + "pt";
            } else if (!wVal.isEmpty()) {
                tblWidth = wVal;
            }
        }

        String tblBorderWidth = null;
        String tblBorderColor = null;
        var tblBordersNodes = tblEl.getElementsByTagNameNS(W, "tblBorders");
        if (tblBordersNodes.getLength() > 0) {
            var bordersEl = (Element) tblBordersNodes.item(0);
            var topNodes = bordersEl.getElementsByTagNameNS(W, "top");
            if (topNodes.getLength() > 0) {
                tblBorderWidth = ((Element) topNodes.item(0)).getAttributeNS(W, "sz");
                tblBorderColor = ((Element) topNodes.item(0)).getAttributeNS(W, "color");
                if (tblBorderWidth.isEmpty()) tblBorderWidth = null;
                if (tblBorderColor.isEmpty()) tblBorderColor = null;
            }
        }

        // Visibility: check for tblPr > tblStyle > hidden or tblLook
        boolean visibility = true;
        var tblPrNodes = tblEl.getElementsByTagNameNS(W, "tblPr");
        if (tblPrNodes.getLength() > 0) {
            var tblPr = (Element) tblPrNodes.item(0);
            var hiddenNodes = tblPr.getElementsByTagNameNS(W, "hidden");
            if (hiddenNodes.getLength() > 0) {
                String val = ((Element) hiddenNodes.item(0)).getAttributeNS(W, "val");
                if ("1".equals(val) || "true".equals(val)) visibility = false;
            }
        }

        // Parse rows
        var trNodes = tblEl.getElementsByTagNameNS(W, "tr");
        // Only direct children of tbl
        var children = tblEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el && W.equals(el.getNamespaceURI()) && "tr".equals(el.getLocalName())) {
                rows.add(parseTableRow(el));
            }
        }

        return new TableBlock(Collections.unmodifiableList(rows),
                tblWidth, tblBorderWidth, tblBorderColor, visibility);
    }

    private TableRow parseTableRow(Element trEl) {
        String height = null;
        var trPrNodes = trEl.getElementsByTagNameNS(W, "trPr");
        if (trPrNodes.getLength() > 0) {
            var trPr = (Element) trPrNodes.item(0);
            var trHeightNodes = trPr.getElementsByTagNameNS(W, "trHeight");
            if (trHeightNodes.getLength() > 0) {
                height = ((Element) trHeightNodes.item(0)).getAttributeNS(W, "val");
                if (height.isEmpty()) height = null;
            }
        }

        var cells = new ArrayList<TableCell>();
        var children = trEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el && W.equals(el.getNamespaceURI()) && "tc".equals(el.getLocalName())) {
                cells.add(parseTableCell(el));
            }
        }
        return new TableRow(Collections.unmodifiableList(cells), height);
    }

    private TableCell parseTableCell(Element tcEl) {
        int colspan = 1;
        int rowspan = 1;
        String width = null;
        String borderWidth = null;
        String borderColor = null;
        String bgColor = null;
        boolean visibility = true;

        var tcPrNodes = tcEl.getElementsByTagNameNS(W, "tcPr");
        if (tcPrNodes.getLength() > 0) {
            var tcPr = (Element) tcPrNodes.item(0);

            // gridSpan for colspan
            var gridSpanNodes = tcPr.getElementsByTagNameNS(W, "gridSpan");
            if (gridSpanNodes.getLength() > 0) {
                String val = ((Element) gridSpanNodes.item(0)).getAttributeNS(W, "val");
                if (!val.isEmpty()) colspan = Integer.parseInt(val);
            }

            // vMerge for rowspan (simplified — proper rowspan requires scanning vertically)
            var vMergeNodes = tcPr.getElementsByTagNameNS(W, "vMerge");
            if (vMergeNodes.getLength() > 0) {
                String val = ((Element) vMergeNodes.item(0)).getAttributeNS(W, "val");
                // "restart" means row-span begins; absent val means continuation
                // For simplicity, we set rowspan=1 here; row spanning needs multi-pass
                if ("restart".equals(val)) {
                    rowspan = 1; // start of vertical merge — actual count needs multi-pass
                }
            }

            // cell width
            var tcWNodes = tcPr.getElementsByTagNameNS(W, "tcW");
            if (tcWNodes.getLength() > 0) {
                var tcWEl = (Element) tcWNodes.item(0);
                String wVal = tcWEl.getAttributeNS(W, "w");
                String wType = tcWEl.getAttributeNS(W, "type");
                if ("pct".equals(wType) && !wVal.isEmpty()) {
                    width = (Integer.parseInt(wVal) / 50) + "%";
                } else if ("dxa".equals(wType) && !wVal.isEmpty()) {
                    width = (Integer.parseInt(wVal) / 20.0) + "pt";
                } else if (!wVal.isEmpty()) {
                    width = wVal;
                }
            }

            // cell borders
            var tcBordersNodes = tcPr.getElementsByTagNameNS(W, "tcBorders");
            if (tcBordersNodes.getLength() > 0) {
                var bordersEl = (Element) tcBordersNodes.item(0);
                var topNodes = bordersEl.getElementsByTagNameNS(W, "top");
                if (topNodes.getLength() > 0) {
                    borderWidth = ((Element) topNodes.item(0)).getAttributeNS(W, "sz");
                    borderColor = ((Element) topNodes.item(0)).getAttributeNS(W, "color");
                    if (borderWidth.isEmpty()) borderWidth = null;
                    if (borderColor.isEmpty()) borderColor = null;
                }
            }

            // shading / background
            var shdNodes = tcPr.getElementsByTagNameNS(W, "shd");
            if (shdNodes.getLength() > 0) {
                bgColor = ((Element) shdNodes.item(0)).getAttributeNS(W, "fill");
                if (bgColor.isEmpty()) bgColor = null;
            }

            // visibility
            var hiddenNodes = tcPr.getElementsByTagNameNS(W, "hidden");
            if (hiddenNodes.getLength() > 0) {
                String val = ((Element) hiddenNodes.item(0)).getAttributeNS(W, "val");
                if ("1".equals(val) || "true".equals(val)) visibility = false;
            }
        }

        // Parse paragraphs inside cell
        var paragraphs = new ArrayList<ParagraphBlock>();
        var children = tcEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el && W.equals(el.getNamespaceURI()) && "p".equals(el.getLocalName())) {
                paragraphs.add(parseParagraph(el));
            }
        }

        return new TableCell(Collections.unmodifiableList(paragraphs),
                colspan, rowspan, width, borderWidth, borderColor, bgColor, visibility);
    }

    // ---- Helpers ----

    private static String getAttrVal(Element parent, String ns, String childLocalName, String attrName) {
        var nodes = parent.getElementsByTagNameNS(ns, childLocalName);
        if (nodes.getLength() > 0) {
            String val = ((Element) nodes.item(0)).getAttributeNS(ns, attrName);
            return val.isEmpty() ? null : val;
        }
        return null;
    }

    private static boolean hasElement(Element parent, String ns, String childLocalName) {
        return parent.getElementsByTagNameNS(ns, childLocalName).getLength() > 0;
    }

    private static boolean hasChildLocalName(Element parent, String ns, String localName) {
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el && ns.equals(el.getNamespaceURI()) && localName.equals(el.getLocalName())) {
                return true;
            }
        }
        return false;
    }

    private static String guessMimeType(String path) {
        if (path == null) return null;
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".tif") || lower.endsWith(".tiff")) return "image/tiff";
        if (lower.endsWith(".emf")) return "image/x-emf";
        if (lower.endsWith(".wmf")) return "image/x-wmf";
        return "application/octet-stream";
    }
}
