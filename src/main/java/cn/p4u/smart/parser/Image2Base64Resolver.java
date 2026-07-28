package com.cxg.dbb.core;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;

import java.io.InputStream;

/**
 * 图片转base64
 */
public class Image2Base64Resolver implements ImageUriResolver {
    @Override
    public String resolve(InputStream imgInputstream, String fileName) {
        String extName = FileUtil.extName(fileName);
        byte[] byteArray = IoUtil.readBytes(imgInputstream);
        if ("svg".equals(extName)) {
            extName = "svg+xml";
        }
        return "data:image/" + extName + ";base64," + Base64.encode(byteArray);
    }

}
