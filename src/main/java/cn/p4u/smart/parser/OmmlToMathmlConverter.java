package cn.p4u.smart.parser;

import org.w3c.dom.*;

/**
 * OMML (Office Math Markup Language) �� MathML ��ת������
 * <p>
 * �� OOXML �ĵ��е� m:oMath ��ʽԪ��ת��Ϊ MathML ��ʽ��
 * MathML �ɱ��ִ������ԭ����Ⱦ��Chrome/Firefox/Safari���������ⲿ������
 */
public final class OmmlToMathmlConverter {

    private static final String M = "http://schemas.openxmlformats.org/officeDocument/2006/math";

    private OmmlToMathmlConverter() {}

    /**
     * �� OMML ��ʽԪ��ת��Ϊ MathML XML �ַ�����
     * @param mathEl m:oMath �� m:oMathPara Ԫ��
     * @return MathML XML �ַ�����������Ϊ null �򷵻ؿ��ַ���
     */
    public static String convert(Element mathEl) {
        if (mathEl == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<math xmlns=\"http://www.w3.org/1998/Math/MathML\">");
        convertNode(mathEl, sb);
        sb.append("</math>");
        return sb.toString();
    }

    private static void convertNode(Node node, StringBuilder sb) {
        if (!(node instanceof Element)) return;
        Element el = (Element) node;
        if (!M.equals(el.getNamespaceURI())) {
            NodeList children = el.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) convertNode(children.item(i), sb);
            return;
        }
        String n = el.getLocalName();
        if ("oMath".equals(n) || "oMathPara".equals(n)) { convertChildren(el, sb); return; }
        if ("r".equals(n)) {
            NodeList tNodes = el.getElementsByTagNameNS(M, "t");
            if (tNodes.getLength() > 0) {
                sb.append("<mi>").append(escapeXml(tNodes.item(0).getTextContent())).append("</mi>");
            }
            return;
        }
        if ("f".equals(n)) {
            sb.append("<mfrac><mrow>");
            Element num = findChild(el, "num"); if (num != null) convertChildren(num, sb);
            sb.append("</mrow><mrow>");
            Element den = findChild(el, "den"); if (den != null) convertChildren(den, sb);
            sb.append("</mrow></mfrac>");
            return;
        }
        if ("sSup".equals(n)) {
            sb.append("<msup>");
            if (findChild(el, "e") != null) { sb.append("<mrow>"); convertChildren(findChild(el, "e"), sb); sb.append("</mrow>"); }
            if (findChild(el, "sup") != null) { sb.append("<mrow>"); convertChildren(findChild(el, "sup"), sb); sb.append("</mrow>"); }
            sb.append("</msup>");
            return;
        }
        if ("sSub".equals(n)) {
            sb.append("<msub>");
            if (findChild(el, "e") != null) { sb.append("<mrow>"); convertChildren(findChild(el, "e"), sb); sb.append("</mrow>"); }
            if (findChild(el, "sub") != null) { sb.append("<mrow>"); convertChildren(findChild(el, "sub"), sb); sb.append("</mrow>"); }
            sb.append("</msub>");
            return;
        }
        if ("sSubSup".equals(n)) {
            sb.append("<msubsup>");
            if (findChild(el, "e") != null) { sb.append("<mrow>"); convertChildren(findChild(el, "e"), sb); sb.append("</mrow>"); }
            if (findChild(el, "sub") != null) { sb.append("<mrow>"); convertChildren(findChild(el, "sub"), sb); sb.append("</mrow>"); }
            if (findChild(el, "sup") != null) { sb.append("<mrow>"); convertChildren(findChild(el, "sup"), sb); sb.append("</mrow>"); }
            sb.append("</msubsup>");
            return;
        }
        if ("rad".equals(n)) {
            Element deg = findChild(el, "deg");
            if (deg != null) {
                sb.append("<mroot><mrow>"); if (findChild(el, "e") != null) convertChildren(findChild(el, "e"), sb);
                sb.append("</mrow><mrow>"); convertChildren(deg, sb); sb.append("</mrow></mroot>");
            } else {
                sb.append("<msqrt><mrow>"); if (findChild(el, "e") != null) convertChildren(findChild(el, "e"), sb);
                sb.append("</mrow></msqrt>");
            }
            return;
        }
        // Ĭ�ϵݹ�
        convertChildren(el, sb);
    }

    private static void convertChildren(Element parent, StringBuilder sb) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element el = (Element) child;
                if (M.equals(el.getNamespaceURI())) convertNode(el, sb);
            }
        }
    }

    private static Element findChild(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(M, localName);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
