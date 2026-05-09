package com.tianji.aigc.memory.jdbc;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tianji.aigc.entity.ChatRecord;
import com.tianji.aigc.memory.MessageUtil;
import com.tianji.aigc.memory.MyChatMemoryRepository;
import com.tianji.aigc.service.ChatRecordService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 基于JDBC的ChatMemoryRepository实现
 */
public class jdbcChatMemoryRepository implements ChatMemoryRepository, MyChatMemoryRepository {

    @Resource
    private ChatRecordService chatRecordService;

    @Override
    public List<String> findConversationIds() {
        var chatRecordList = chatRecordService.lambdaQuery()
                .select(ChatRecord::getConversationId)
                .list();
        return CollStreamUtil.toList(chatRecordList, ChatRecord::getConversationId);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        var chatRecordList = chatRecordService.lambdaQuery()
                .eq(ChatRecord::getConversationId, conversationId)
                .orderByAsc(ChatRecord::getCreateTime)
                .list();
        return CollStreamUtil.toList(chatRecordList, chatRecord -> MessageUtil.toMessage(chatRecord.getData()));
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // 删除原有数据
        deleteByConversationId(conversationId);
        // 通过对话id获取用户id
        var userId = Convert.toLong(StrUtil.subBefore(conversationId, "_", true));
        // 批量保存数据到数据库
        var chatRecordList = CollStreamUtil.toList(messages, message -> ChatRecord.builder()
                .data(MessageUtil.toJson(message))
                .conversationId(conversationId)
                .creater(userId)
                .updater(userId)
                .build());

        chatRecordService.saveBatch(chatRecordList);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        var queryWrapper = Wrappers.<ChatRecord>lambdaQuery()
                .eq(ChatRecord::getConversationId, conversationId);
        chatRecordService.remove(queryWrapper);
    }

    /**
     * 根据对话ID优化对话记录，删除最后的2条消息，因为这条消息是从路由智能体存储的，请求由后续的智能体处理
     * 为了确保历史消息的完整性，所以需要将中间转发的消息清理掉
     *
     * @param conversationId 对话的唯一标识符
     */
    @Override
    public void optimization(String conversationId) {
        // 获取该会话的所有记录，按创建时间升序排列
        var chatRecordList = chatRecordService.lambdaQuery()
                .eq(ChatRecord::getConversationId, conversationId)
                .orderByDesc(ChatRecord::getCreateTime)
                .last("LIMIT 2")
                .list();

        // 删除最后2条记录
        if (chatRecordList != null && !chatRecordList.isEmpty()) {
            var ids = CollStreamUtil.toList(chatRecordList, ChatRecord::getId);
            chatRecordService.removeByIds(ids);
        }
    }
}
