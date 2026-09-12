package cn.p4u.dth.renderer;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Dimension2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.poi.common.usermodel.fonts.FontCharset;
import org.apache.poi.common.usermodel.fonts.FontInfo;
import org.apache.poi.hwmf.draw.HwmfGraphics;
import org.apache.poi.hwmf.record.HwmfText.WmfExtTextOutOptions;
import org.apache.poi.hwmf.usermodel.HwmfPicture;
import org.apache.poi.sl.draw.DrawFontManagerDefault;
import org.apache.poi.sl.draw.Drawable;

/** Linux/libwmf 中文兼容分支：读取原 WMF，不把 UTF-8 写回传统代码页记录。 */
final class LinuxWmfRasterizer {
  private static final int MAX_SOURCE_BYTES = 32 * 1024 * 1024;
  private static final long MAX_PIXELS = 32_000_000;

  private LinuxWmfRasterizer() {}

  /** null 表示非中文 WMF，交回 ImageMagick；中文 WMF 失败则抛异常，不静默生成乱码。 */
  static Boolean rasterizeChineseWmf(Path source, Path target, int width, int height)
      throws IOException {
    if (Files.size(source) > MAX_SOURCE_BYTES) {
      throw new IOException("Metafile exceeds Linux WMF renderer size limit");
    }
    byte[] bytes = Files.readAllBytes(source);
    if (!hasChineseFont(bytes)) return null;
    HwmfPicture picture = new HwmfPicture(new ByteArrayInputStream(bytes));
    Dimension2D size = picture.getSize();
    double ratio = size.getWidth() / size.getHeight();
    double w = width > 0 ? width * 4.0
        : height > 0 ? height * 4.0 * ratio : size.getWidth() * 300 / 72;
    double h = height > 0 ? height * 4.0
        : width > 0 ? width * 4.0 / ratio : size.getHeight() * 300 / 72;
    // 与 magick -resize 一致，在给定框中保持原始宽高比。
    if (width > 0 && height > 0) {
      double scale = Math.min(w / size.getWidth(), h / size.getHeight());
      w = size.getWidth() * scale;
      h = size.getHeight() * scale;
    }
    if (!Double.isFinite(w) || !Double.isFinite(h) || w <= 0 || h <= 0
        || Math.ceil(w) * Math.ceil(h) > MAX_PIXELS) {
      throw new IOException("Invalid or excessive WMF raster dimensions");
    }
    BufferedImage image = new BufferedImage((int) Math.ceil(w), (int) Math.ceil(h),
        BufferedImage.TYPE_INT_ARGB);
    Graphics2D ctx = image.createGraphics();
    try {
      ctx.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      ctx.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      ctx.setRenderingHint(Drawable.FONT_HANDLER, new ChineseFontManager());
      draw(picture, ctx, image.getWidth(), image.getHeight());
      return ImageIO.write(image, "png", target.toFile());
    } finally {
      ctx.dispose();
      image.flush();
    }
  }

  static boolean hasChineseFont(byte[] bytes) throws IOException {
    if (bytes.length < 18) return false;
    ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    int start = b.getInt(0) == 0x9AC6CDD7 ? 22 : 0;
    if (bytes.length < start + 18 || b.getShort(start + 2) != 9
        || (b.getShort(start) != 1 && b.getShort(start) != 2)) return false;
    long end = start + Integer.toUnsignedLong(b.getInt(start + 6)) * 2;
    if (end != bytes.length) throw new IOException("Invalid WMF file length");
    boolean chinese = false;
    boolean unsupportedText = false;
    for (int p = start + 18; p <= bytes.length - 6;) {
      long len = Integer.toUnsignedLong(b.getInt(p)) * 2;
      if (len < 6 || len > bytes.length - p) throw new IOException("Invalid WMF record length");
      int type = Short.toUnsignedInt(b.getShort(p + 4));
      if (type == 0) {
        if (len != 6 || p + len != end) throw new IOException("Invalid WMF EOF");
        if (chinese && unsupportedText) {
          throw new IOException("Linux WMF renderer does not support glyph-index/PDY text records");
        }
        return chinese;
      }
      if (type == 0x0A32) {
        if (len < 14) throw new IOException("Truncated WMF ExtTextOut");
        unsupportedText |= (Short.toUnsignedInt(b.getShort(p + 12)) & ~6) != 0;
      }
      if (type == 0x02FB && len >= 56) {
        int charset = bytes[p + 19] & 255;
        chinese |= charset == 134 || charset == 136;
      }
      p += (int) len;
    }
    throw new IOException("Missing WMF EOF");
  }

  static HwmfGraphics draw(HwmfPicture picture, Graphics2D ctx, int width, int height) {
    Rectangle2D bounds = picture.getInnnerBounds();
    if (bounds == null) bounds = picture.getBounds();
    if (bounds.isEmpty()) throw new IllegalArgumentException("Empty WMF bounds");
    ctx.scale(width / bounds.getWidth(), height / bounds.getHeight());
    ctx.translate(-bounds.getX(), -bounds.getY());
    HwmfGraphics graphics = new CodePageGraphics(ctx, bounds);
    graphics.getProperties().setViewportOrg(bounds.getX(), bounds.getY());
    graphics.getProperties().setViewportExt(bounds.getWidth(), bounds.getHeight());
    picture.getRecords().forEach(record -> record.draw(graphics));
    return graphics;
  }

  /** POI 5.5.1 的 GB2312 映射较窄；Windows charset 134 应按 CP936/GBK 解码。 */
  private static final class CodePageGraphics extends HwmfGraphics {
    CodePageGraphics(Graphics2D ctx, Rectangle2D bounds) { super(ctx, bounds); }

    @Override
    public void drawString(byte[] text, int length, Point2D reference, Dimension2D scale,
        Rectangle2D clip, WmfExtTextOutOptions opts, List<Integer> dx, boolean unicode) {
      FontCharset fc = getProperties().getFont().getCharset();
      if (!unicode && isChinese(fc)) {
        Charset cs = Charset.forName(fc == FontCharset.GB2312 ? "GBK" : "Big5");
        try {
          if (length < 0 || length > text.length) {
            throw new IllegalArgumentException("Invalid WMF text byte count");
          }
          String decoded = cs.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(text, 0, length)).toString();
          List<Integer> charDx = characterAdvances(decoded, cs, dx);
          drawPositionedText(decoded.getBytes(StandardCharsets.UTF_16LE), decoded.length(),
              reference, scale, clip, opts, charDx, true);
          return;
        } catch (IOException e) {
          throw new IllegalArgumentException("Invalid WMF " + cs.name() + " text", e);
        }
      }
      drawPositionedText(text, length, reference, scale, clip, opts, dx, unicode);
    }

    private void drawPositionedText(byte[] text, int length, Point2D reference,
        Dimension2D scale, Rectangle2D clip, WmfExtTextOutOptions opts,
        List<Integer> advances, boolean unicode) {
      if (advances == null || advances.isEmpty()) {
        super.drawString(text, length, reference, scale, clip, opts, advances, unicode);
        return;
      }
      int byteWidth = unicode ? 2 : 1;
      if (advances.size() != length || text.length < length * byteWidth) {
        throw new IllegalArgumentException("Invalid WMF character spacing");
      }
      Point2D location = reference.distance(0, 0) == 0
          ? getProperties().getLocation() : reference;
      double originX = location.getX();
      double originY = location.getY();
      double angle = Math.toRadians(-getProperties().getFont().getEscapement() / 10.0);
      int totalAdvance = 0;
      for (int value : advances) totalAdvance = Math.addExact(totalAdvance, value);
      var alignment = getProperties().getTextAlignLatin();
      double offset = switch (alignment) {
        case RIGHT -> totalAdvance;
        case CENTER -> totalAdvance / 2.0;
        default -> 0;
      };
      originX -= offset * Math.cos(angle);
      originY -= offset * Math.sin(angle);
      int advance = 0;
      getProperties().setTextAlignLatin(org.apache.poi.hwmf.record.HwmfText.HwmfTextAlignment.LEFT);
      try {
        for (int index = 0; index < length; index++) {
          Point2D position = new Point2D.Double(originX + advance * Math.cos(angle),
              originY + advance * Math.sin(angle));
          getProperties().setLocation(position.getX(), position.getY());
          byte[] character = java.util.Arrays.copyOfRange(text, index * byteWidth,
              (index + 1) * byteWidth);
          super.drawString(character, 1, position, scale, clip, opts, null, unicode);
          advance = Math.addExact(advance, advances.get(index));
        }
      } finally {
        getProperties().setTextAlignLatin(alignment);
      }
      getProperties().setLocation(originX + advance * Math.cos(angle),
          originY + advance * Math.sin(angle));
    }
  }

  /** GDI 窄文本的 Dx 按字节计数，DBCS 字符的两个位移需相加。 */
  static List<Integer> characterAdvances(String text, Charset charset, List<Integer> dx) {
    if (dx == null || dx.isEmpty()) return dx;
    if (dx.size() != text.getBytes(charset).length) {
      throw new IllegalArgumentException("Invalid WMF DBCS spacing array");
    }
    List<Integer> result = new ArrayList<>();
    int index = 0;
    for (int cp : text.codePoints().toArray()) {
      int length = new String(Character.toChars(cp)).getBytes(charset).length;
      int advance = 0;
      for (int i = 0; i < length; i++) advance = Math.addExact(advance, dx.get(index++));
      result.add(advance);
    }
    return result;
  }

  private static boolean isChinese(FontCharset charset) {
    return charset == FontCharset.GB2312 || charset == FontCharset.CHINESEBIG5;
  }

  private static final class ChineseFontManager extends DrawFontManagerDefault {
    @Override
    public FontInfo getMappedFont(Graphics2D ctx, FontInfo font) {
      if (!isChinese(font.getCharset())) return super.getMappedFont(ctx, font);
      String configured = System.getProperty("docx2html.wmf.cjkFont");
      String name = configured == null || configured.isBlank() ? font.getTypeface() : configured;
      // Java 逻辑字体通过 fontconfig 提供 Linux CJK 字体回退。
      if ((configured == null || configured.isBlank())
          && new Font(name, Font.PLAIN, 12).canDisplayUpTo("中文") != -1) name = Font.DIALOG;
      String selected = name;
      return new FontInfo() {
        @Override public String getTypeface() { return selected; }
        @Override public FontCharset getCharset() { return font.getCharset(); }
      };
    }

    @Override
    public String mapFontCharset(Graphics2D ctx, FontInfo font, String text) {
      if (isChinese(font.getCharset())
          && new Font(font.getTypeface(), Font.PLAIN, 12).canDisplayUpTo(text) != -1) {
        throw new IllegalArgumentException("WMF Chinese glyphs unavailable in " + font.getTypeface()
            + "; install CJK fonts/fontconfig and restart Java, or set docx2html.wmf.cjkFont");
      }
      return super.mapFontCharset(ctx, font, text);
    }
  }
}
