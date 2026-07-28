package com.cxg.dbb.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.cxg.dbb.utils.FileStorageUtil;
import java.io.InputStream;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.dromara.x.file.storage.core.FileInfo;

/** 图片转base64 */
public class Image2OssResolver implements ImageUriResolver {

  @Setter @Getter private Integer orgId;
  @Setter @Getter private Integer userId;
  private String bookCode;

  @Override
  public String resolve(InputStream imgInputstream, String fileName) {
    String simpleUUID = IdUtil.fastSimpleUUID();
    String extName = FileUtil.extName(fileName);
    String objName;
    if (StrUtil.isNotBlank(bookCode)) {
      objName = "image/" + bookCode + "/" + simpleUUID + "." + extName;
    } else if (orgId != null && userId != null) {
      objName = "image/" + orgId + "/" + userId + "/" + simpleUUID + "." + extName;
    } else {
      LocalDateTime now = LocalDateTime.now();
      objName =
          "image/" + now.getYear() + "/" + now.getMonthValue() + "/" + simpleUUID + "." + extName;
    }

    FileInfo uploadResult = FileStorageUtil.getInstance().upload(objName, imgInputstream);
    return uploadResult.getUrl();
  }

  public void setBookCode(String bookCode) {
    this.bookCode = bookCode;
  }
}
