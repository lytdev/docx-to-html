package cn.p4u.smart.parser;

import cn.p4u.smart.model.StyleDef;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

public final class StylesParser {

    private static final Logger LOG = Logger.getLogger(StylesParser.class.getName());
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    private StylesParser() {}

    public static Map<String, StyleDef> parse(Path stylesFile) {
        if (!java.nio.file.Files.exists(stylesFile)) {
            LOG.warning("Styles file not found: " + stylesFile);
            return Map.of();
        }
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(new java.io.StringReader("")));
            var doc = builder.parse(stylesFile.toFile());
            var styles = new HashMap<String, StyleDef>();
            var styleNodes = doc.getElementsByTagNameNS(W, "style");
            for (int i = 0; i < styleNodes.getLength(); i++) {
                var el = (Element) styleNodes.item(i);
                String styleId = el.getAttributeNS(W, "styleId");
                String name = getText(el, "name", "val");
                String basedOn = getText(el, "basedOn", "val");
                var runProps = parseProps(el, "rPr");
                var paraProps = parseProps(el, "pPr");
                styles.put(styleId, new StyleDef(styleId, name, basedOn, runProps, paraProps));
            }
            return Collections.unmodifiableMap(styles);
        } catch (Exception e) {
            LOG.warning("Failed to parse styles: " + e.getMessage());
            return Map.of();
        }
    }

    private static String getText(Element parent, String tagName, String attr) {
        var nodes = parent.getElementsByTagNameNS(W, tagName);
        if (nodes.getLength() > 0) {
            return ((Element) nodes.item(0)).getAttributeNS(W, attr);
        }
        return null;
    }

    private static Map<String, String> parseProps(Element parent, String propName) {
        var nodes = parent.getElementsByTagNameNS(W, propName);
        if (nodes.getLength() == 0) return Map.of();
        var props = new HashMap<String, String>();
        var children = ((Element) nodes.item(0)).getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element ce) {
                String localName = ce.getLocalName();
                String val = ce.getAttributeNS(W, "val");
                props.put(localName, val.isEmpty() ? "true" : val);
            }
        }
        return Collections.unmodifiableMap(props);
    }
}
