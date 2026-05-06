package com.tianji.aigc.memory;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

/**
 * 基于Redis实现的ChatMemoryRepository
 */
public class RedisChatMemoryRepository implements ChatMemoryRepository {

	public static final String DEFAULT_PREFIX = "CHAT:";

	private final String prefix;

	public RedisChatMemoryRepository() {
		this.prefix = DEFAULT_PREFIX;
	}

    public RedisChatMemoryRepository(String prefix) {
        this.prefix = prefix;
    }

	@Autowired
	private StringRedisTemplate redisTemplate;


    @Override
	public List<String> findConversationIds() {
		Set<String> keys = redisTemplate.keys(prefix + "*");
		if (null == keys) {
			return List.of();
		}
		return StreamUtil.of(keys)
				.map(key -> StrUtil.removePrefix(key, prefix))
				.toList();
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		var redisKey = getKey(conversationId);
		var listOps = redisTemplate.boundListOps(redisKey);
		// 获取列表所有数据
		var messages = listOps.range(0, -1);
		return CollStreamUtil.toList(messages, MessageUtil::toMessage);
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		var redisKey = getKey(conversationId);
		var listOps = redisTemplate.boundListOps(redisKey);
		// 清空
		deleteByConversationId(conversationId);
		// 保存
		messages.forEach(message -> listOps.rightPush(MessageUtil.toJson(message)));
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		var redisKey = getKey(conversationId);
		redisTemplate.delete(redisKey);
	}

	private String getKey(String conversationId) {
		return prefix + conversationId;
	}
}
