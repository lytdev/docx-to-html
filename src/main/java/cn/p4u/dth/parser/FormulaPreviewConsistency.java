package cn.p4u.dth.parser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 检测旧公式的可编辑数据与 WMF 预览是否明显冲突。
 *
 * <p>部分文件只更新了预览，却保留另一条公式的 Equation Native 流。
 * 此处只比较常规字体绘制的数字：若图片出现 LaTeX 中完全不存在的数字，
 * 就不能把该 LaTeX 附到图片上。绘制顺序不等于阅读顺序，因此不能比较文本顺序。
 *
 * <p>这不是 OCR，也不是完整的公式等价性证明；没有可读文字、未知格式或特殊字体
 * 都不能据此判定冲突。所有 WMF 数据仅用于读取，绝不执行其中的绘图命令。
 */
final class FormulaPreviewConsistency {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final Set<String> STANDARD_FONTS = Set.of(
            "times new roman", "arial", "calibri", "cambria", "cambria math",
            "courier new", "tahoma", "verdana", "segoe ui");

    private FormulaPreviewConsistency() {}

    static boolean conflicts(Path preview, String latex) {
        if (preview == null || latex.isEmpty()) return false;
        try {
            if (Files.size(preview) > MAX_BYTES) return false;
            try (var input = Files.newInputStream(preview)) {
                byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) return false;
                Set<Character> visible = wmfDigits(bytes);
                return !digits(latex).containsAll(visible);
            }
        } catch (IOException | IllegalArgumentException e) {
            // 无法核对，不等于内容冲突；保持原来的提取行为。
            return false;
        }
    }

    private static Set<Character> digits(String text) {
        var result = new HashSet<Character>();
        for (char c : Normalizer.normalize(text, Normalizer.Form.NFKC).toCharArray())
            if (c >= '0' && c <= '9') result.add(c);
        return result;
    }

    /** 根据微软 MS-WMF 的记录长度读取，不能在二进制中盲搜数字字节。 */
    private static Set<Character> wmfDigits(byte[] bytes) {
        var data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        require(bytes.length >= 18);
        int start = data.getInt(0) == 0x9AC6CDD7 ? 22 : 0;
        require(bytes.length >= start + 18);
        require(word(data, start) == 1 || word(data, start) == 2);
        require(word(data, start + 2) == 9);
        long endLong = start + Integer.toUnsignedLong(data.getInt(start + 6)) * 2;
        require(endLong >= start + 18 && endLong <= bytes.length);
        int end = (int) endLong;
        // null 表示空闲句柄；0 表示非字体，1 表示未知字体，2 表示可核对的标准字体。
        Integer[] objects = new Integer[word(data, start + 10)];
        int selectedFont = 1;
        var result = new HashSet<Character>();
        for (int pos = start + 18; pos < end;) {
            require(end - pos >= 6);
            long length = Integer.toUnsignedLong(data.getInt(pos)) * 2;
            require(length >= 6 && length <= end - pos);
            int next = pos + (int) length;
            int type = word(data, pos + 4);
            if (type == 0) return result;
            switch (type) {
                case 0x02FB: { // CREATEFONTINDIRECT：LOGFONT 中的 charset 和字体名称。
                    require(length >= 56);
                    int charset = bytes[pos + 19] & 255;
                    int nameEnd = pos + 24;
                    while (nameEnd < pos + 56 && bytes[nameEnd] != 0) nameEnd++;
                    String name = new String(bytes, pos + 24, nameEnd - pos - 24,
                            StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
                    allocate(objects, charset <= 1 && STANDARD_FONTS.contains(name) ? 2 : 1);
                    break;
                }
                // 创建图形对象也会占用句柄，不能把笔/刷子的句柄误当成字体。
                case 0x02FA, 0x02FC, 0x00F7, 0x01F9, 0x0142, 0x06FF, 0x06FE, 0x02FD, 0x02F8:
                    allocate(objects, 0);
                    break;
                case 0x012D: {
                    require(length >= 8);
                    int handle = word(data, pos + 6);
                    require(handle < objects.length && objects[handle] != null);
                    if (objects[handle] != 0) selectedFont = objects[handle];
                    break;
                }
                case 0x01F0: {
                    require(length >= 8);
                    int handle = word(data, pos + 6);
                    require(handle < objects.length);
                    objects[handle] = null;
                    break;
                }
                case 0x001E, 0x0127:
                    // 暂不解释保存/恢复绘图上下文，避免对字体状态作不可靠的假设。
                    return Set.of();
                case 0x0A32: { // EXTTEXTOUT：Y、X、字符数、选项、可选矩形、字符。
                    require(length >= 14);
                    int count = word(data, pos + 10), options = word(data, pos + 12);
                    int text = pos + 14 + ((options & 6) != 0 ? 8 : 0);
                    require(count <= 32767 && text <= next && count <= next - text);
                    if (selectedFont == 2 && (options & ~6) == 0)
                        collect(bytes, text, count, result);
                    break;
                }
                case 0x0521: { // TEXTOUT：字符数、字符（补齐到偶数字节）、Y、X。
                    require(length >= 12);
                    int count = word(data, pos + 6);
                    require(((count + 1) & ~1) <= next - pos - 12);
                    if (selectedFont == 2) collect(bytes, pos + 8, count, result);
                    break;
                }
                default:
                    break;
            }
            pos = next;
        }
        throw new IllegalArgumentException("WMF EOF missing");
    }

    private static void collect(byte[] bytes, int start, int count, Set<Character> result) {
        for (int i = start; i < start + count; i++)
            if (bytes[i] >= '0' && bytes[i] <= '9') result.add((char) bytes[i]);
    }

    private static void allocate(Integer[] objects, int kind) {
        for (int i = 0; i < objects.length; i++) {
            if (objects[i] == null) { objects[i] = kind; return; }
        }
        throw new IllegalArgumentException("Invalid WMF object table");
    }

    private static int word(ByteBuffer data, int pos) { return Short.toUnsignedInt(data.getShort(pos)); }
    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Unsupported or malformed WMF");
    }
}
