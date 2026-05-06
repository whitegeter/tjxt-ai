package com.tianji.aigc.service.impl;

import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatClient chatClient;

    private final SystemPromptConfig systemPromptConfig;

    private final StringRedisTemplate redisTemplate;

    private static final String GENERATE_STATUS_KEY = "GENERATE_STATUS";

    private final ChatMemory chatMemory;

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        var hashOps = redisTemplate.boundHashOps(GENERATE_STATUS_KEY);
        // 获取对话id
        var conversationId = ChatService.getConversationId(sessionId);
        // 大模型输出内容的缓存器，用于在输出中断后的数据存储
        var outputBuilder = new StringBuilder();

        return this.chatClient.prompt()
                .system(promptSystem -> promptSystem
                        .text(systemPromptConfig.getChatSystemMessage().get()) // 系统提示词
                        .params(Map.of("now", DateUtils.now())) // 当前时间参数
                )
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(question)
                .stream()
                .chatResponse()
                .doFirst(() -> hashOps.put(sessionId, "true"))
                .doOnError(throwable -> hashOps.delete(sessionId))
                .doOnComplete(() -> hashOps.delete(sessionId))
                .doOnCancel(() -> {
                    // 当输出被取消时，保存输出的内容到历史记录中
                    this.saveStopHistoryRecord(conversationId, outputBuilder.toString());
                })
                .takeWhile(response -> hashOps.get(sessionId) != null)
                .map(chatResponse -> {
                    var text = chatResponse.getResult().getOutput().getText();
                    // 追加到输出内容中
                    outputBuilder.append(text);
                    return ChatEventVO.builder()
                            .eventData(text)
                            .eventType(ChatEventTypeEnum.DATA.getValue())
                            .build();
                })
                .concatWith(Flux.just(ChatEventVO.builder()
                        .eventType(ChatEventTypeEnum.STOP.getValue())
                        .build()));

    }

    /**
     * 保存停止输出的记录
     *
     * @param conversationId 会话id
     * @param content        大模型输出的内容
     */
    private void saveStopHistoryRecord(String conversationId, String content) {
        this.chatMemory.add(conversationId, new AssistantMessage(content));
    }

    @Override
    public void stop(String sessionId) {
        var hashOps = redisTemplate.boundHashOps(GENERATE_STATUS_KEY);
        hashOps.delete(sessionId);
    }
}
