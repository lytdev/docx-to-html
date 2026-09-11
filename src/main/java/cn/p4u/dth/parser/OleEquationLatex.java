package cn.p4u.dth.parser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

/** 读取复合文档中的 Equation Native 流；OLE 容器交给 POI，公式结构交给 MTEF 解析器。 */
final class OleEquationLatex {
    private static final Logger LOG = Logger.getLogger(OleEquationLatex.class.getName());
    private static final int MAX_BYTES = 4 * 1024 * 1024;

    private OleEquationLatex() {}

    static String extract(Path path) {
        try {
            if (Files.size(path) > MAX_BYTES) throw new IOException("OLE equation too large");
            try (var input = Files.newInputStream(path); var fs = new POIFSFileSystem(input)) {
                if (!fs.getRoot().hasEntry("Equation Native")) return "";
                try (var stream = fs.createDocumentInputStream("Equation Native")) {
                    byte[] nativeData = stream.readNBytes(MAX_BYTES + 1);
                    if (nativeData.length > MAX_BYTES || nativeData.length < 28)
                        throw new IOException("Invalid Equation Native length");
                    var header = ByteBuffer.wrap(nativeData).order(ByteOrder.LITTLE_ENDIAN);
                    int headerSize = Short.toUnsignedInt(header.getShort(0));
                    long size = Integer.toUnsignedLong(header.getInt(8));
                    if (headerSize < 28 || size < 5 || size > nativeData.length - headerSize)
                        throw new IOException("Invalid Equation Native header");
                    return MtefLatexConverter.convert(java.util.Arrays.copyOfRange(
                            nativeData, headerSize, headerSize + (int) size));
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.fine("OLE LaTeX extraction skipped: " + path.getFileName() + ": " + e.getMessage());
            return "";
        }
    }
}
