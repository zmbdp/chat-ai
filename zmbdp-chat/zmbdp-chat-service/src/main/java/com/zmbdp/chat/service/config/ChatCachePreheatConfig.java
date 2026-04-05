package com.zmbdp.chat.service.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.zmbdp.chat.service.domain.dto.ChatMessageDTO;
import com.zmbdp.chat.service.domain.dto.ChatSessionDTO;
import com.zmbdp.chat.service.domain.entity.ChatMessage;
import com.zmbdp.chat.service.domain.entity.ChatSession;
import com.zmbdp.chat.service.mapper.ChatMessageMapper;
import com.zmbdp.chat.service.mapper.ChatSessionMapper;
import com.zmbdp.common.cache.utils.CacheUtil;
import com.zmbdp.common.redis.service.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 聊天缓存预热
 *
 * @author 稚名不带撇
 */
@Slf4j
@Component
public class ChatCachePreheatConfig {

    /**
     * 聊天元信息缓存前缀
     */
    private static final String CHAT_CACHE_KEY_PREFIX = "chat:meta:";

    /**
     * 聊天列表缓存前缀
     */
    private static final String CHAT_LIST_CACHE_KEY_PREFIX = "chat:list:";

    /**
     * 聊天历史缓存前缀
     */
    private static final String CHAT_HISTORY_CACHE_KEY_PREFIX = "chat:history:";

    /**
     * 缓存时间（分钟）
     */
    private static final long CACHE_TIMEOUT = 30L;

    /**
     * 聊天会话 mapper
     */
    @Autowired
    private ChatSessionMapper chatSessionMapper;

    /**
     * 聊天消息 mapper
     */
    @Autowired
    private ChatMessageMapper chatMessageMapper;

    /**
     * redis 服务
     */
    @Autowired
    private RedisService redisService;

    /**
     * caffeine 一级缓存
     */
    @Autowired
    private Cache<String, Object> caffeineCache;

    /**
     * 应用启动完成后预热聊天相关缓存
     * 预热范围包含：
     * 1. 每个用户的聊天列表缓存
     * 2. 每个会话的聊天元信息缓存
     * 3. 每个会话的聊天消息历史缓存
     */
    @EventListener(ApplicationReadyEvent.class)
    public void preheatChatCache() {
        // 查询所有会话，如果没有会话则直接结束预热
        List<ChatSession> chatSessions = chatSessionMapper.selectList(null);
        if (chatSessions == null || chatSessions.isEmpty()) {
            log.info("聊天缓存预热完成, 当前无会话数据");
            return;
        }

        // 查询所有消息，并分别按用户、按会话维度分组，方便后续批量回填缓存
        List<ChatMessage> chatMessages = chatMessageMapper.selectList(null);
        Map<Long, List<ChatSession>> sessionMap = chatSessions.stream()
                .collect(Collectors.groupingBy(ChatSession::getUserId));
        Map<String, List<ChatMessageDTO>> messageMap = chatMessages.stream()
                .collect(Collectors.groupingBy(
                        message -> buildHistoryCacheKey(message.getUserId(), message.getChatId()),
                        Collectors.mapping(message -> new ChatMessageDTO(message.getRole(), message.getContent()), Collectors.toList())
                ));

        // 按用户维度预热聊天列表缓存，再逐条预热聊天元信息缓存和聊天历史缓存
        for (Map.Entry<Long, List<ChatSession>> entry : sessionMap.entrySet()) {
            Long userId = entry.getKey();
            List<ChatSessionDTO> chatSessionDTOS = entry.getValue().stream()
                    .map(chatSession -> new ChatSessionDTO(chatSession.getId(), chatSession.getUserId(), chatSession.getTitle()))
                    .collect(Collectors.toList());

            // 预热当前用户的聊天列表缓存
            CacheUtil.setL2Cache(redisService, buildChatListCacheKey(userId), chatSessionDTOS, caffeineCache, CACHE_TIMEOUT, TimeUnit.MINUTES);

            for (ChatSession chatSession : entry.getValue()) {
                // 预热当前会话的聊天元信息缓存
                ChatSessionDTO chatSessionDTO = new ChatSessionDTO(chatSession.getId(), chatSession.getUserId(), chatSession.getTitle());
                CacheUtil.setL2Cache(redisService, buildChatCacheKey(userId, chatSession.getId()), chatSessionDTO, caffeineCache, CACHE_TIMEOUT, TimeUnit.MINUTES);

                // 预热当前会话的聊天历史缓存
                List<ChatMessageDTO> history = messageMap.get(buildHistoryCacheKey(userId, chatSession.getId()));
                if (history != null) {
                    CacheUtil.setL2Cache(redisService, buildHistoryCacheKey(userId, chatSession.getId()), history, caffeineCache, CACHE_TIMEOUT, TimeUnit.MINUTES);
                }
            }
        }

        log.info("聊天缓存预热完成, 会话数:{}, 消息数:{}", chatSessions.size(), chatMessages.size());
    }

    /**
     * 构建聊天元信息缓存 key
     *
     * @param userId 用户 id
     * @param chatId 聊天 id
     * @return 缓存 key
     */
    private String buildChatCacheKey(Long userId, String chatId) {
        return CHAT_CACHE_KEY_PREFIX + userId + ":" + chatId;
    }

    /**
     * 构建聊天列表缓存 key
     *
     * @param userId 用户 id
     * @return 缓存 key
     */
    private String buildChatListCacheKey(Long userId) {
        return CHAT_LIST_CACHE_KEY_PREFIX + userId;
    }

    /**
     * 构建聊天历史缓存 key
     *
     * @param userId 用户 id
     * @param chatId 聊天 id
     * @return 缓存 key
     */
    private String buildHistoryCacheKey(Long userId, String chatId) {
        return CHAT_HISTORY_CACHE_KEY_PREFIX + userId + ":" + chatId;
    }
}
