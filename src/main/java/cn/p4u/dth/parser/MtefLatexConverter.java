package cn.p4u.dth.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 Equation Editor / MathType 的 MTEF 3、5 结构转换为 LaTeX。
 * 这不是图片 OCR：上下标、分数等来自可编辑对象中的模板和槽位。
 * 未支持的记录、符号或损坏的数据会整体失败，由调用方继续使用原预览图片。
 */
final class MtefLatexConverter {
    private final byte[] data;
    private final int version;
    private int pos;
    private int depth;
    private int records;

    private MtefLatexConverter(byte[] data) {
        this.data = data;
        version = read();
        require(version == 3 || version == 5);
        skip(4);
        if (version == 5) {
            string();
            read(); // 行内/独立公式选项不影响此处的结构提取。
        }
    }

    static String convert(byte[] data) {
        var parser = new MtefLatexConverter(data);
        String result = join(parser.list());
        // 容器流允许末尾有零填充，但不能静默丢弃尚未解析的记录。
        while (parser.pos < data.length) require(parser.read() == 0);
        return result;
    }

    /** 保留空 LINE 槽位；否则只含下标的模板会被误当成上标。 */
    private List<Node> list() {
        require(++depth <= 128);
        var nodes = new ArrayList<Node>();
        while (true) {
            require(++records <= 100_000);
            int tag = read();
            int type = version == 3 ? tag & 15 : tag;
            if (type == 0) break;
            int flags = version == 3 ? tag >>> 4 : (type <= 6 ? read() : 0);
            if (type <= 6 && (flags & 8) != 0) nudge();
            Node node = record(type, flags);
            if (node != null) nodes.add(node);
        }
        depth--;
        return nodes;
    }

    private Node record(int type, int flags) {
        switch (type) {
            case 1:
                if ((flags & 4) != 0) skip(2);
                if ((flags & 2) != 0) rulerRecord();
                return new Node("line", 0, 0, (flags & 1) != 0 ? List.of() : list());
            case 2: {
                int face = version == 3 ? read() - 128 : signed();
                require(version == 3 || (flags & 32) == 0);
                int code = word();
                if (version == 5) {
                    if ((flags & 4) != 0) read();
                    if ((flags & 16) != 0) word();
                }
                boolean embellished = (flags & (version == 3 ? 2 : 1)) != 0;
                return new Node(version == 3 ? "char3" : "char", code, face,
                        embellished ? list() : List.of());
            }
            case 3: {
                int selector = read(), variation = read();
                if (version == 5 && (variation & 128) != 0)
                    variation = (variation & 127) | (read() << 8);
                read(); // 模板对齐/伸缩选项，图片仍保留原始排版。
                return new Node(version == 3 ? "template3" : "template5", selector, variation, list());
            }
            case 4:
                skip(2);
                if ((flags & 2) != 0) rulerRecord();
                return new Node("pile", 0, 0, list());
            case 5: {
                skip(3);
                int rows = read(), cols = read();
                require(rows > 0 && cols > 0);
                // 分隔线目前不转换，避免生成与原公式不同的矩阵。
                for (int n = (rows + 4) / 4 + (cols + 4) / 4; n > 0; n--)
                    require(read() == 0);
                var cells = list();
                require(cells.size() == rows * cols);
                return new Node("matrix", rows, cols, cells);
            }
            case 6: {
                int embell = read();
                // Equation Editor 3 的修饰符数据有独立的零结束字节，
                // 不能把它误当成外层 CHAR/LINE 的 END（真实样本含 06 11 00 00）。
                if (version == 3) require(read() == 0);
                return new Node("embell", embell, 0, List.of());
            }
            case 7:
                ruler();
                break;
            case 8:
                if (version == 3) { skip(2); string(); }
                else { unsigned(); read(); }
                break;
            case 9:
                int size = read();
                skip(size == 101 ? 2 : size == 100 ? 3 : 1);
                break;
            case 10, 11, 12, 13, 14:
                break;
            case 15:
                require(version == 5);
                unsigned();
                break;
            case 16: {
                require(version == 5);
                int options = read();
                skip((options & 1) != 0 ? 8 : 6);
                if ((options & 4) != 0) string();
                break;
            }
            case 17:
                require(version == 5);
                unsigned(); string();
                break;
            case 18:
                require(version == 5);
                read(); dimensions(); dimensions();
                int count = read();
                for (int i = 0; i < count; i++) if (unsigned() != 0) read();
                break;
            case 19:
                require(version == 5);
                string();
                break;
            default:
                throw new IllegalArgumentException("Unsupported MTEF record: " + type);
        }
        return null;
    }

    /** 延迟输出字符：模板附带的私有字体括号只用于预览，不应重复写入 LaTeX。 */
    private record Node(String kind, int value, int variant, List<Node> children) {}

    private static String join(List<Node> nodes) {
        var text = new StringBuilder();
        for (Node node : nodes) text.append(render(node));
        return text.toString();
    }

    private static String render(Node node) {
        return switch (node.kind) {
            case "line" -> join(node.children);
            case "char", "char3" -> character(node);
            case "template3" -> template(node, 3);
            case "template5" -> template(node, 5);
            case "pile" -> "\\begin{gathered}" +
                    String.join("\\\\", node.children.stream().map(MtefLatexConverter::render).toList())
                    + "\\end{gathered}";
            case "matrix" -> matrix(node);
            default -> throw new IllegalArgumentException("Unexpected MTEF node");
        };
    }

    /** 模板中 LINE 是内容槽，CHAR 是用于绘制括号/大运算符的字形。 */
    private static String template(Node node, int version) {
        List<Node> slots = node.children.stream()
                .filter(n -> n.kind.equals("line") || n.kind.equals("pile")).toList();
        require(node.children.stream().allMatch(n ->
                n.kind.equals("line") || n.kind.equals("pile") || n.kind.startsWith("char")));
        int id = node.value, variation = node.variant;
        if (id <= 7) {
            require(slots.size() == 1);
            String[] left = {"\\langle", "(", "\\{", "[", "|", "\\Vert", "\\lfloor", "\\lceil"};
            String[] right = {"\\rangle", ")", "\\}", "]", "|", "\\Vert", "\\rfloor", "\\rceil"};
            require(version == 3 ? variation <= 2 : (variation & ~3) == 0);
            boolean hasLeft = version == 3 ? variation != 2 : (variation & 1) != 0;
            boolean hasRight = version == 3 ? variation != 1 : (variation & 2) != 0;
            return "\\left" + (hasLeft ? left[id] : ".") + " " + slot(slots, 0)
                    + "\\right" + (hasRight ? right[id] : ".");
        }
        if (id == (version == 3 ? 13 : 10)) {
            require(variation <= 1 && slots.size() >= 1 && slots.size() <= 2);
            return variation == 0 ? "\\sqrt{" + slot(slots, 0) + "}"
                    : "\\sqrt[" + slot(slots, 1) + "]{" + slot(slots, 0) + "}";
        }
        if (id == (version == 3 ? 14 : 11)) {
            require(slots.size() == 2 && variation <= 1);
            return "\\frac{" + slot(slots, 0) + "}{" + slot(slots, 1) + "}";
        }
        if (version == 3 && id == 15 || version == 5 && id >= 27 && id <= 29) {
            require(slots.size() == 2);
            require(version == 3 ? variation <= 2 : variation == 0);
            return script("_", slot(slots, 0)) + script("^", slot(slots, 1));
        }
        if (id == (version == 3 ? 16 : 12) || id == (version == 3 ? 17 : 13)) {
            require(slots.size() == 1 && variation <= 1);
            String command = id == (version == 3 ? 16 : 12) ? "\\underline" : "\\overline";
            String result = command + "{" + slot(slots, 0) + "}";
            return variation == 0 ? result : command + "{" + result + "}";
        }
        if (version == 3 && id >= 29 && id <= 38 || version == 5 && id >= 16 && id <= 20) {
            require(slots.size() == 3);
            if (version == 3) require(variation <= 2);
            else require((variation & ~0x43) == 0);
            String[] operators = {"\\sum", "\\prod", "\\coprod", "\\bigcup", "\\bigcap"};
            int index = version == 3 ? (id - 29) / 2 : id - 16;
            boolean limits = version == 3 ? id % 2 == 1 : (variation & 64) != 0;
            return operators[index] + (limits ? "\\limits" : "\\nolimits")
                    + script("_", slot(slots, 2)) + script("^", slot(slots, 1))
                    + " " + slot(slots, 0);
        }
        if (id == (version == 3 ? 39 : 23)) {
            require(slots.size() == 3 && variation <= 2);
            return "\\mathop{" + slot(slots, 0) + "}\\limits"
                    + script("_", slot(slots, 1)) + script("^", slot(slots, 2));
        }
        throw new IllegalArgumentException("Unsupported MTEF template: " + id);
    }

    private static String slot(List<Node> slots, int index) {
        require(index < slots.size());
        return render(slots.get(index));
    }

    private static String script(String marker, String content) {
        return content.isEmpty() ? "" : marker + "{" + content + "}";
    }

    private static String matrix(Node node) {
        var result = new StringBuilder("\\begin{matrix}");
        for (int i = 0; i < node.children.size(); i++) {
            if (i > 0) result.append(i % node.variant == 0 ? "\\\\" : "&");
            result.append(render(node.children.get(i)));
        }
        return result.append("\\end{matrix}").toString();
    }

    private static String character(Node node) {
        // 显式符号字体的低位编码并非 Unicode，不能猜测其含义。
        require(node.variant > 0);
        int c = node.value;
        // 旧 Symbol/Greek 字体的单字节字母是字形索引，不应误输出为拉丁字母。
        if (node.kind.equals("char3") && node.variant >= 4 && node.variant <= 6)
            require(c >= 256 || !Character.isLetter(c));
        require(c >= 32 && !Character.isISOControl(c) &&
                Character.getType(c) != Character.PRIVATE_USE && !Character.isSurrogate((char) c));
        String value = switch (c) {
            case '\\' -> "\\backslash ";
            case '{', '}', '#', '$', '%', '&', '_' -> "\\" + (char) c;
            case '^' -> "\\hat{}";
            case '~' -> "\\sim ";
            case 0x2212 -> "-";
            case 0x03B1 -> "\\alpha ";
            case 0x03B2 -> "\\beta ";
            case 0x03B3 -> "\\gamma ";
            case 0x03B4 -> "\\delta ";
            case 0x03B8 -> "\\theta ";
            case 0x03BB -> "\\lambda ";
            case 0x03BC -> "\\mu ";
            case 0x03C0 -> "\\pi ";
            case 0x03C3 -> "\\sigma ";
            case 0x03C6 -> "\\phi ";
            case 0x03C9 -> "\\omega ";
            case 0x221E -> "\\infty ";
            case 0x2264 -> "\\leq ";
            case 0x2265 -> "\\geq ";
            case 0x2260 -> "\\neq ";
            case 0x00D7 -> "\\times ";
            case 0x00F7 -> "\\div ";
            case 0x00B1 -> "\\pm ";
            case 0x2192 -> "\\to ";
            default -> Character.toString(c);
        };
        if ((node.variant == 1 || node.variant == 2) && Character.isLetter(c))
            value = "\\mathrm{" + value + "}";
        if (node.variant == 7) value = "\\mathbf{" + value + "}";
        for (Node embell : node.children) {
            require(embell.kind.equals("embell"));
            value = switch (embell.value) {
                case 2 -> "\\dot{" + value + "}";
                case 3 -> "\\ddot{" + value + "}";
                case 4 -> "\\dddot{" + value + "}";
                case 5 -> value + "'";
                case 6 -> value + "''";
                case 18 -> value + "'''";
                case 8 -> "\\tilde{" + value + "}";
                case 9 -> "\\hat{" + value + "}";
                case 10 -> "\\not{" + value + "}";
                case 11 -> "\\vec{" + value + "}";
                case 12 -> "\\overleftarrow{" + value + "}";
                case 13 -> "\\overleftrightarrow{" + value + "}";
                case 17 -> "\\bar{" + value + "}";
                default -> throw new IllegalArgumentException("Unsupported embellishment");
            };
        }
        return value;
    }

    private void rulerRecord() { require(read() == 7); ruler(); }
    private void ruler() { skip(read() * 3); }
    private void nudge() { int x = read(), y = read(); if (x == 128 && y == 128) skip(4); }
    private int unsigned() { int b = read(); return b == 255 ? word() : b; }
    private int signed() { int b = read(); return b == 255 ? word() - 32768 : b - 128; }
    private void string() { while (read() != 0) { /* 零结尾的元数据字符串 */ } }
    private void dimensions() {
        int count = read();
        while (count > 0) {
            int b = read();
            if ((b >>> 4) == 15) count--;
            if (count > 0 && (b & 15) == 15) count--;
        }
    }
    private int read() { require(pos < data.length); return data[pos++] & 255; }
    private int word() { int low = read(); return low | (read() << 8); }
    private void skip(int count) { require(count >= 0 && count <= data.length - pos); pos += count; }
    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Unsupported or malformed MTEF");
    }
}
