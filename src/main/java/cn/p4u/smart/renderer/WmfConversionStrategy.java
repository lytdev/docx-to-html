package cn.p4u.smart.renderer;

/** WMF/EMF 转 PNG 时使用的转换策略。 */
public enum WmfConversionStrategy {
  /** 自动选择 ImageMagick，失败后在 Windows 上尝试 PowerShell。 */
  AUTO,

  /** 仅使用 ImageMagick，不回退到其他转换器。 */
  IMAGEMAGICK,

  /** 仅使用 Windows PowerShell 和 System.Drawing。 */
  POWERSHELL,

  /** 禁用 WMF/EMF 转换。 */
  NONE
}
