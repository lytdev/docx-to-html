package cn.p4u.dth.renderer;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.poi.common.usermodel.fonts.FontInfo;
import org.apache.poi.hwmf.usermodel.HwmfPicture;
import org.apache.poi.sl.draw.DrawFontManagerDefault;
import org.apache.poi.sl.draw.Drawable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinuxWmfRasterizerTest {
  @TempDir Path temp;

  @Test
  void rendersMathTypeFormulaWithDisjointTextAndContinuousChineseSubscripts() throws Exception {
    byte[] bytes;
    try (var resource = getClass().getResourceAsStream("mathtype-chinese-dx.wmf")) {
      assertNotNull(resource);
      bytes = resource.readAllBytes();
    }
    Path source = temp.resolve("formula.wmf");
    Files.write(source, bytes);
    assertTrue(LinuxWmfRasterizer.hasChineseFont(bytes));
    assertTrue(drawnText(bytes).getFirst().endsWith("货货货货客客客客客"));
    BufferedImage canvas = new BufferedImage(1784, 183, BufferedImage.TYPE_INT_ARGB);
    Graphics2D context = canvas.createGraphics();
    try {
      var state = LinuxWmfRasterizer.draw(
          new HwmfPicture(new ByteArrayInputStream(bytes)), context, 1784, 183);
      assertEquals(0x153 + 0x3a2 + 0x378 + 0x1bc, state.getProperties().getLocation().getX(), 0.001);
      assertEquals(0x331, state.getProperties().getLocation().getY(), 0.001);
    } finally {
      context.dispose();
    }
    Path png = temp.resolve("formula.png");
    assertEquals(Boolean.TRUE, LinuxWmfRasterizer.rasterizeChineseWmf(source, png, 446, 46));
    assertArrayEquals(bytes, Files.readAllBytes(source));
    BufferedImage preview = ImageIO.read(png.toFile());
    assertNotNull(preview);
    assertEquals(1784, preview.getWidth());
    assertEquals(183, preview.getHeight());
    assertTrue(inkIn(preview, 350, 110, 410, 150) > 50);
    assertTrue(inkIn(preview, 860, 55, 920, 100) > 50);
    String previewPath = System.getProperty("docx2html.test.formulaPreview");
    if (previewPath == null) return;
    BufferedImage white = new BufferedImage(preview.getWidth(), preview.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = white.createGraphics();
    graphics.setColor(java.awt.Color.WHITE);
    graphics.fillRect(0, 0, white.getWidth(), white.getHeight());
    graphics.drawImage(preview, 0, 0, null);
    graphics.dispose();
    ImageIO.write(white, "png", Path.of(previewPath).toFile());
  }

  private static int inkIn(BufferedImage image, int left, int top, int right, int bottom) {
    int count = 0;
    for (int y = top; y < bottom; y++) {
      for (int x = left; x < right; x++) {
        if ((image.getRGB(x, y) >>> 24) > 64) count++;
      }
    }
    return count;
  }

  @Test
  void windowsAndMacNeverUseLinuxCompatibility() {
    assertFalse(ImageMagickWmfRasterizer.useLinuxWmfRenderer("Windows 11"));
    assertFalse(ImageMagickWmfRasterizer.useLinuxWmfRenderer("Windows Server 2022"));
    assertFalse(ImageMagickWmfRasterizer.useLinuxWmfRenderer("Mac OS X"));
    assertTrue(ImageMagickWmfRasterizer.useLinuxWmfRenderer("Linux"));
  }

  @Test
  void wmfFileToPngTest() throws IOException {
    Path source = Path.of("E:/_tmp/wmf/image16.wmf");
    Path target = Path.of("E:/_tmp/wmf/image16.png");
    LinuxWmfRasterizer.rasterizeChineseWmf(source, target, 875, 384);
  }

  @Test
  void decodesGbKExtensionsForTextOutAndExtTextOut() throws Exception {
    String text = "中文囍x";
    assertFalse(Charset.forName("GB2312").newEncoder().canEncode(text));
    for (boolean extended : List.of(false, true)) {
      assertEquals(List.of(text), drawnText(wmf(text, "GBK", 134, extended)));
    }
  }

  @Test
  void decodesBig5AndLeavesWesternTextInItsOwnCodePage() throws Exception {
    assertEquals(List.of("繁體中文"), drawnText(wmf("繁體中文", "Big5", 136, true)));
    assertEquals(List.of("café"), drawnText(wmf("café", "windows-1252", 0, false)));
  }

  @Test
  void combinesDbcsByteSpacing() {
    assertEquals(
        List.of(20, 25, 10),
        LinuxWmfRasterizer.characterAdvances(
            "中文x", Charset.forName("GBK"), List.of(20, 0, 25, 0, 10)));
    assertThrows(
        IllegalArgumentException.class,
        () -> LinuxWmfRasterizer.characterAdvances("中文", Charset.forName("GBK"), List.of(10)));
  }

  @Test
  void createsTransparentPngWithoutChangingOriginalWmf() throws Exception {
    byte[] original = wmf("中文x", "GBK", 134, true);
    Path source = temp.resolve("formula.wmf"), target = temp.resolve("formula.png");
    Files.write(source, original);
    assertEquals(Boolean.TRUE, LinuxWmfRasterizer.rasterizeChineseWmf(source, target, 200, 80));
    assertArrayEquals(original, Files.readAllBytes(source));
    BufferedImage image = ImageIO.read(target.toFile());
    assertNotNull(image);
    assertEquals(800, image.getWidth());
    assertEquals(320, image.getHeight());
    assertTrue(image.getColorModel().hasAlpha());
    int visible = 0, transparent = 0;
    for (int y = 0; y < image.getHeight(); y++)
      for (int x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) >>> 24) == 0) transparent++;
        else visible++;
      }
    assertTrue(visible > 100);
    assertTrue(transparent > visible);
  }

  @Test
  void nonChineseAndNonWmfStayOnImageMagickPathAndMalformedWmfIsRejected() throws Exception {
    assertFalse(LinuxWmfRasterizer.hasChineseFont(new byte[88]));
    assertFalse(LinuxWmfRasterizer.hasChineseFont(wmf("text", "windows-1252", 0, false)));
    byte[] bytes = wmf("中文", "GBK", 134, true);
    assertThrows(
        java.io.IOException.class,
        () -> LinuxWmfRasterizer.hasChineseFont(Arrays.copyOf(bytes, bytes.length - 2)));
  }

  private List<String> drawnText(byte[] bytes) throws Exception {
    List<String> captured = new ArrayList<>();
    BufferedImage image = new BufferedImage(800, 320, BufferedImage.TYPE_INT_ARGB);
    Graphics2D ctx = image.createGraphics();
    try {
      ctx.setRenderingHint(
          Drawable.FONT_HANDLER,
          new DrawFontManagerDefault() {
            @Override
            public String mapFontCharset(Graphics2D g, FontInfo font, String text) {
              captured.add(text);
              return text;
            }
          });
      LinuxWmfRasterizer.draw(new HwmfPicture(new ByteArrayInputStream(bytes)), ctx, 800, 320);
    } finally {
      ctx.dispose();
    }
    return List.of(String.join("", captured));
  }

  private byte[] wmf(String text, String charset, int charsetId, boolean extended)
      throws Exception {
    ByteArrayOutputStream records = new ByteArrayOutputStream();
    records.write(record(0x020B, 0, 0)); // window origin
    records.write(record(0x020C, 80, 200));
    records.write(record(0x0102, 1)); // transparent background
    ByteBuffer font = buffer(56);
    font.putInt(28).putShort((short) 0x02FB).putShort((short) -20);
    font.putShort(14, (short) 400);
    font.put(19, (byte) charsetId);
    font.position(24).put("SimSun".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    records.write(font.array());
    records.write(record(0x012D, 0));
    records.write(record(0x001E)); // SaveDC/RestoreDC must preserve selected font.
    records.write(record(0x0127, -1));
    byte[] chars = text.getBytes(Charset.forName(charset));
    ByteBuffer output = buffer((extended ? 14 + chars.length * 2 : 12) + ((chars.length + 1) & ~1));
    output.putInt(output.capacity() / 2).putShort((short) (extended ? 0x0A32 : 0x0521));
    if (extended) output.putShort((short) 10).putShort((short) 10);
    output.putShort((short) chars.length);
    if (extended) output.putShort((short) 0);
    output.put(chars);
    if ((chars.length & 1) != 0) output.put((byte) 0);
    if (extended) {
      for (int cp : text.codePoints().toArray()) {
        int n = new String(Character.toChars(cp)).getBytes(Charset.forName(charset)).length;
        output.putShort((short) 22);
        for (int i = 1; i < n; i++) output.putShort((short) 0);
      }
    } else output.putShort((short) 10).putShort((short) 10);
    records.write(output.array());
    records.write(record(0));
    ByteBuffer result = buffer(18 + records.size());
    result
        .putShort((short) 1)
        .putShort((short) 9)
        .putShort((short) 0x300)
        .putInt(result.capacity() / 2)
        .putShort((short) 1)
        .putInt(Math.max(28, output.capacity() / 2))
        .putShort((short) 0)
        .put(records.toByteArray());
    return result.array();
  }

  private byte[] record(int function, int... args) {
    ByteBuffer b = buffer(6 + args.length * 2);
    b.putInt(b.capacity() / 2).putShort((short) function);
    for (int arg : args) b.putShort((short) arg);
    return b.array();
  }

  private ByteBuffer buffer(int length) {
    return ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
  }
}
