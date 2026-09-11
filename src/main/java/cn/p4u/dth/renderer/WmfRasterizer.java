package cn.p4u.dth.renderer;

import java.nio.file.Path;

/**
 * WMF/EMF 栅格化策略的统一接口（Strategy 模式）。
 *
 * <p>WmfConverter 只依赖这个接口，不关心实现背后调用的是哪个外部程序。</p>
 */
interface WmfRasterizer {
  /** 当前策略是否可以执行；禁用策略返回 false，可避免创建无用临时文件。 */
  boolean isAvailable();

  /** 把源 WMF/EMF 文件转换为 PNG 文件。 */
  boolean rasterize(Path source, Path target, int logicalWidth, int logicalHeight);
}
