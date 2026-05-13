package com.tianji.aigc.service;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * 文本和语音互转服务
 */
public interface AudioService {

    /**
     * 文本转语音
     * @param text
     * @return
     */
    ResponseBodyEmitter ttsStream(String text);

    /**
     * 语音转文本
     * @param audioFile
     * @return
     */
    String stt(MultipartFile audioFile);
}
