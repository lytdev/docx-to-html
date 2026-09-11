package cn.p4u.dth.parser;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FormulaPreviewConsistencyTest {
    @TempDir Path temp;

    @Test void rejectsDigitsAbsentFromEditableFormula() throws Exception {
        assertTrue(conflicts(wmf("5753.62", false, false, "Times New Roman"), "t^{2}+t^{3}+t^{4}"));
        assertFalse(conflicts(wmf("32", false, false, "Times New Roman"), "t^{2}+t^{3}"));
        assertFalse(conflicts(wmf("2", false, false, "Times New Roman"), "x²"));
    }

    @Test void supportsTextOutAndPlaceableHeaders() throws Exception {
        assertTrue(conflicts(wmf("89", true, true, "Arial"), "x_0"));
        assertFalse(conflicts(wmf("0", true, true, "Arial"), "x_0"));
    }

    @Test void doesNotGuessUnknownFormatsFontsOrMissingFiles() throws Exception {
        assertFalse(conflicts(new byte[] {1, 2, 3}, "x"));
        assertFalse(conflicts(wmf("89", false, false, "Unknown Math Font"), "x"));
        assertFalse(conflicts(wmf("", false, false, "Arial"), "x_0"));
        assertFalse(FormulaPreviewConsistency.conflicts(temp.resolve("missing"), "x"));
    }

    @Test void ignoresPaddingAndGlyphIndices() throws Exception {
        byte[] bytes = wmf("0", false, false, "Arial");
        // WMF 的奇数字符串有一个对齐字节，这不是可见数字。
        bytes[18 + 16 + 56 + 8 + 14 + 1] = '9';
        assertFalse(conflicts(bytes, "x_0"));
        // ETO_GLYPH_INDEX 中的值是字形编号，不是普通字符串。
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                .putShort(18 + 16 + 56 + 8 + 12, (short) 0x10);
        assertFalse(conflicts(bytes, "x"));
    }

    @Test void discardsPartialEvidenceWhenMetafileIsMalformed() throws Exception {
        byte[] bytes = wmf("89", false, false, "Arial");
        // 删除末尾 EOF，不应拿残缺解析结果判定公式错误。
        assertFalse(conflicts(java.util.Arrays.copyOf(bytes, bytes.length - 6), "x"));
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(18, Integer.MAX_VALUE);
        assertFalse(conflicts(bytes, "x"));
    }

    private boolean conflicts(byte[] preview, String latex) throws Exception {
        Path path = temp.resolve("preview.wmf");
        Files.write(path, preview);
        return FormulaPreviewConsistency.conflicts(path, latex);
    }

    /** 构造真实布局的 WMF 记录，不依赖操作系统图形库。 */
    static byte[] wmf(String text, boolean textOut, boolean placeable, String font) throws Exception {
        var records = new ByteArrayOutputStream();
        ByteBuffer pen = buffer(16);
        pen.putInt(8).putShort((short) 0x02FA);
        records.write(pen.array()); // 句柄 0：验证字体不是简单地取最后创建的对象。
        ByteBuffer logFont = buffer(56);
        logFont.putInt(28).putShort((short) 0x02FB);
        logFont.position(24);
        logFont.put(font.getBytes(StandardCharsets.US_ASCII));
        records.write(logFont.array()); // 句柄 1。
        records.write(buffer(8).putInt(4).putShort((short) 0x012D).putShort((short) 1).array());
        byte[] chars = text.getBytes(StandardCharsets.US_ASCII);
        int padded = (chars.length + 1) & ~1;
        ByteBuffer output = buffer((textOut ? 12 : 14) + padded);
        output.putInt(output.capacity() / 2).putShort((short) (textOut ? 0x0521 : 0x0A32));
        if (!textOut) output.putInt(0);
        output.putShort((short) chars.length);
        if (!textOut) output.putShort((short) 0);
        output.put(chars);
        records.write(output.array());
        records.write(buffer(6).putInt(3).putShort((short) 0).array());
        int offset = placeable ? 22 : 0;
        ByteBuffer result = buffer(offset + 18 + records.size());
        if (placeable) { result.putInt(0x9AC6CDD7); result.position(offset); }
        result.putShort((short) 1).putShort((short) 9).putShort((short) 0x300)
                .putInt((18 + records.size()) / 2).putShort((short) 2)
                .putInt(Math.max(28, output.capacity() / 2)).putShort((short) 0);
        result.put(records.toByteArray());
        return result.array();
    }

    private static ByteBuffer buffer(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }
}
