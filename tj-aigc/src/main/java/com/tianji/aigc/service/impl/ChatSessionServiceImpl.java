package com.tianji.aigc.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.aigc.config.SessionProperties;
import com.tianji.aigc.entity.ChatSession;
import com.tianji.aigc.enums.MessageTypeEnum;
import com.tianji.aigc.mapper.ChatSessionMapper;
import com.tianji.aigc.memory.MyAssistantMessage;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.service.ChatSessionService;
import com.tianji.aigc.vo.ChatSessionVO;
import com.tianji.aigc.vo.MessageVO;
import com.tianji.aigc.vo.SessionVO;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {

    private final SessionProperties sessionProperties;

    private final ChatMemory chatMemory;

    @Override
    public SessionVO createSession(Integer num) {
        var sessionVO = BeanUtil.toBean(sessionProperties, SessionVO.class);
        // 随机获取examples
        sessionVO.setExamples(RandomUtil.randomEleList(sessionProperties.getExamples(), num));

        // 随机生成sessionId
        sessionVO.setSessionId(IdUtil.fastSimpleUUID());

        // 构建持久化对象，并持久化
        Long userId = UserContext.getUser();
        var chatSession = ChatSession.builder()
                .sessionId(sessionVO.getSessionId())
                .userId(userId)
                .build();
        super.save(chatSession);

        return sessionVO;
    }

    @Override
    public List<SessionVO.Example> hotExamples(Integer num) {
        return RandomUtil.randomEleList(sessionProperties.getExamples(), num);
    }

    @Override
    public List<MessageVO> queryBySessionId(String sessionId) {
        // 将sessionId转化为对话id
        var conversationId = ChatService.getConversationId(sessionId);

        // 查询对话列表
        List<Message> messages = chatMemory.get(conversationId);

        // 转化为MessageVO列表
        return StreamUtil.of(messages)
                .filter(message -> message.getMessageType() == MessageType.USER || message.getMessageType() == MessageType.ASSISTANT)
                .map(message -> {
                    if(message instanceof MyAssistantMessage myAssistantMessage) {
                        return MessageVO.builder()
                            .type(MessageTypeEnum.valueOf(message.getMessageType().name()))
                            .content(message.getText())
                            .params(myAssistantMessage.getParams())
                            .build();
                    }
                    return MessageVO.builder()
                        .type(MessageTypeEnum.valueOf(message.getMessageType().name()))
                        .content(message.getText())
                        .build();
                })
                .toList();

    }

    @Async
    @Override
    public void update(String sessionId, String title, Long userId) {
        // 获取会话列表
        List<ChatSession> chatSessionList = super.lambdaQuery()
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getUserId, userId)
                .list();
        if (CollUtil.isEmpty(chatSessionList)) {
            return;
        }

        // 获取第一个会话对象数据
        ChatSession chatSession = chatSessionList.get(0);

        if (StrUtil.isEmpty(chatSession.getTitle()) && StrUtil.isNotEmpty(title)) {
            // 如果会话标题为空，则更新
            chatSession.setTitle(StrUtil.sub(title, 0, 100));
        }

        // 设置更新时间
        chatSession.setUpdateTime(LocalDateTime.now());

        // 更新会话数据
        super.updateById(chatSession);
    }

    @Override
    public Map<String, List<ChatSessionVO>> queryHistorySession() {
        Long userId = UserContext.getUser();
        List<ChatSession> chatSessionList = super.lambdaQuery()
                .eq(ChatSession::getUserId, userId)
                .isNotNull(ChatSession::getTitle)
                .orderByAsc(ChatSession::getUpdateTime)
                .last("LIMIT 30")
                .list();
        if (CollUtil.isEmpty(chatSessionList)) {
            return Map.of();
        }
        List<ChatSessionVO> chatSessionVOList = CollStreamUtil.toList(chatSessionList, chatSession -> ChatSessionVO.builder()
                .title(chatSession.getTitle())
                .updateTime(chatSession.getUpdateTime())
                .sessionId(chatSession.getSessionId())
                .build());

        final var TODAY = "当天";
        final var LAST_30_DAYS = "最近30天";
        final var LAST_YEAR = "最近1年";
        final var MORE_THAN_YEAR = "1年以上";

        // 获取当前时间
        LocalDate now = LocalDateTime.now().toLocalDate();

        return CollStreamUtil.groupByKey(chatSessionVOList, chatSessionVO -> {
            long days = Math.abs(ChronoUnit.DAYS.between(chatSessionVO.getUpdateTime().toLocalDate(), now));
            if (days == 0) {
                return TODAY;
            } else if (days <= 30) {
                return LAST_30_DAYS;
            } else if (days <= 365) {
                return LAST_YEAR;
            } else {
                return MORE_THAN_YEAR;
            }
        });
    }

    @Override
    public void deleteHistorySession(String sessionId) {
        // 同时删除MYSQL和chatMemory中的数据
        Long userId = UserContext.getUser();
        LambdaQueryWrapper<ChatSession> queryWrapper = Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getUserId, userId);

        // 删除MYSQL数据
        super.remove(queryWrapper);

        // 删除chatMemory数据
        String conversationId = ChatService.getConversationId(sessionId);
        chatMemory.clear(conversationId);
    }

    @Override
    public void updateTitle(String sessionId, String title) {
        Long userId = UserContext.getUser();
        super.lambdaUpdate()
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getUserId, userId)
                .set(ChatSession::getTitle, title)
                .update();
    }
}
