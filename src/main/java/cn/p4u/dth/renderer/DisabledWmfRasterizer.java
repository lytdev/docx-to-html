package cn.p4u.dth.renderer;

import java.nio.file.Path;

/**
 * 不执行转换的空对象（Null Object 模式）。
 *
 * <p>使用对象表示“没有可用策略”，调用方就不需要到处判断 null。</p>
 */
final class DisabledWmfRasterizer implements WmfRasterizer {
  static final DisabledWmfRasterizer INSTANCE = new DisabledWmfRasterizer();

  private DisabledWmfRasterizer() {}

  @Override
  public boolean isAvailable() {
    return false;
  }

  @Override
  public boolean rasterize(Path source, Path target, int logicalWidth, int logicalHeight) {
    return false;
  }
}
