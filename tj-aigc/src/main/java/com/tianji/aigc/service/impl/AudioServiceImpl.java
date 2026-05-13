package com.tianji.aigc.service.impl;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.tianji.aigc.service.AudioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.audio.speech.SpeechPrompt;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioServiceImpl implements AudioService {

    private final OpenAiAudioSpeechModel openAiAudioSpeechModel;
    private final OpenAiAudioTranscriptionModel openAiAudioTranscriptionModel;

    @Override
    public ResponseBodyEmitter ttsStream(String text) {
        // 定义响应对象
        var emitter = new ResponseBodyEmitter();
        log.info("开始语音合成, 文本内容：{}", text);
        // 调用OpenAI进行文字转语音
        var speechPrompt = new SpeechPrompt(text);
        var responseStream = openAiAudioSpeechModel.stream(speechPrompt);
        // 订阅响应流并发送数据
        responseStream.subscribe(
                speechResponse -> {
                    try {
                        // 获取响应输出的数据，并发送到响应体中
                        byte[] audioBytes = speechResponse.getResult().getOutput();
                        emitter.send(audioBytes);
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                },
                emitter::completeWithError,
                emitter::complete
        );
        return emitter;
    }

    @Override
    public String stt(MultipartFile audioFile) {
        // 根据上传的音频文件获取文件资源
        Resource resource = audioFile.getResource();
        // 创建音频转文字提示
        AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(resource);
        // 调用OpenAI进行音频转文字
        AudioTranscriptionResponse response = this.openAiAudioTranscriptionModel.call(prompt);
        // 获取文本内容
        String text = response.getResult().getOutput();
        log.info("开始语音识别, 识别结果：{}", text);
        // 将繁体转换为简体
        return ZhConverterUtil.toSimple(text);
    }

}
