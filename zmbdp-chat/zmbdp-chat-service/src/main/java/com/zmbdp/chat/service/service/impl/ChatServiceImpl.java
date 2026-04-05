package com.zmbdp.chat.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import com.zmbdp.chat.service.domain.dto.ChatSessionDTO;
import com.zmbdp.chat.service.domain.dto.ChatMessageDTO;
import com.zmbdp.chat.service.domain.dto.ChatSendReqDTO;
import com.zmbdp.chat.service.domain.entity.ChatMessage;
import com.zmbdp.chat.service.domain.entity.ChatSession;
import com.zmbdp.chat.service.mapper.ChatMessageMapper;
import com.zmbdp.chat.service.mapper.ChatSessionMapper;
import com.zmbdp.chat.service.mq.sender.ChatSessionProducer;
import com.zmbdp.chat.service.service.IChatService;
import com.zmbdp.chat.service.service.IChatSessionService;
import com.zmbdp.common.cache.utils.CacheUtil;
import com.zmbdp.common.core.utils.JsonUtil;
import com.zmbdp.common.redis.service.RedisService;
import com.zmbdp.common.security.utils.JwtUtil;
import com.zmbdp.common.security.utils.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 聊天服务实现类
 *
 * @author 稚名不带撇
 */
@Slf4j
@Service
@RefreshScope
public class ChatServiceImpl implements IChatService {

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
     * 缓存过期时间（分钟）
     */
    private static final long CACHE_TIMEOUT = 30L;

    /**
     * jwt 的密钥
     */
    @Value("${jwt.token.secret}")
    private String secret;

    /**
     * 聊天会话 MQ 生产者
     */
    @Autowired
    private ChatSessionProducer chatSessionProducer;

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
     * 聊天会话服务
     */
    @Autowired
    private IChatSessionService chatSessionService;

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
     * 保存聊天会话。
     * 处理流程：
     * 1. 先查询当前聊天是否已存在且标题完整
     * 2. 如果标题已存在则直接返回，不重复覆盖
     * 3. 如果不存在或标题为空，则先删缓存再预写缓存
     * 4. 最后通过 MQ 异步持久化到数据库
     *
     * @param chatId 聊天 id
     * @param title  聊天标题
     */
    @Override
    public void save(String chatId, String title) {
        Long userId = getCurrentUserId();
        ChatSessionDTO chatSessionDTO = chatSessionService.getByChatId(chatId, userId);

        // 如果当前聊天已存在且标题完整，则不再重复覆盖标题
        if (chatSessionDTO != null && chatSessionDTO.getTitle() != null && !chatSessionDTO.getTitle().isBlank()) {
            return;
        }

        // 如果不存在或标题为空，则先清理相关缓存，避免写入旧数据
        clearChatMetaCache(chatId, userId);
        clearChatListCache(userId);

        // 先把新的聊天元信息写入缓存，提升用户首次读取速度
        String normalizedTitle = normalizeTitle(title);
        ChatSessionDTO cachedChatSession = new ChatSessionDTO();
        cachedChatSession.setId(chatId);
        cachedChatSession.setUserId(userId);
        cachedChatSession.setTitle(normalizedTitle);
        CacheUtil.setL2Cache(redisService, buildChatCacheKey(userId, chatId), cachedChatSession, caffeineCache, CACHE_TIMEOUT, TimeUnit.MINUTES);
        CacheUtil.setL2Cache(redisService, buildChatListCacheKey(userId), buildChatInfoList(userId), caffeineCache, CACHE_TIMEOUT, TimeUnit.MINUTES);

        // 再异步发送 MQ 消息，把会话信息持久化到数据库
        chatSessionProducer.sendMessage(new ChatSendReqDTO(chatId, normalizedTitle, userId));
    }

    /**
     * 保存一条聊天消息。
     * 先落库，再删除对应历史缓存，保证下一次读取走最新数据。
     * 用户带图时把图片 URL 序列化进 {@code media_urls}，重启后仍可还原多模态上下文。
     *
     * @param chatId    聊天 id
     * @param role      消息角色
     * @param content   消息内容
     * @param mediaUrls 用户消息中的图片等 URL，无则 null
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMessage(String chatId, String role, String content, List<String> mediaUrls) {
        Long userId = getCurrentUserId();
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setChatId(chatId);
        chatMessage.setUserId(userId);
        chatMessage.setRole(role);
        chatMessage.setContent(content);
        if (mediaUrls != null && !mediaUrls.isEmpty()) {
            chatMessage.setMediaUrls(JsonUtil.classToJson(mediaUrls));
        }
        chatMessageMapper.insert(chatMessage);

        // 消息写入后删除历史缓存，避免旧消息残留
        clearHistoryCache(chatId, userId);
    }

    /**
     * 根据 chatId 删除聊天。
     * 同时删除聊天会话、聊天消息，以及相关缓存。
     *
     * @param chatId 聊天 id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByChatId(String chatId) {
        Long userId = getCurrentUserId();
        chatSessionMapper.delete(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getId, chatId)
                .eq(ChatSession::getUserId, userId));
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getChatId, chatId)
                .eq(ChatMessage::getUserId, userId));

        // 删除数据库后同步清理所有相关缓存
        clearChatMetaCache(chatId, userId);
        clearChatListCache(userId);
        clearHistoryCache(chatId, userId);
    }

    /**
     * 获取当前用户的聊天列表。
     * 查询顺序：先缓存，后数据库，最后回填缓存。
     *
     * @return 聊天列表
     */
    @Override
    public List<ChatSessionDTO> getSessionHistory() {
        Long userId = getCurrentUserId();
        String cacheKey = buildChatListCacheKey(userId);

        // 先查聊天列表缓存，命中则直接返回
        List<ChatSessionDTO> chats = CacheUtil.getL2Cache(redisService, cacheKey, new TypeReference<>() {
        }, caffeineCache);
        if (chats != null) {
            return chats;
        }

        // 缓存未命中时查数据库，并回填缓存
        chats = buildChatInfoList(userId);
        CacheUtil.setL2Cache(redisService, cacheKey, chats, caffeineCache, CACHE_TIMEOUT, TimeUnit.MINUTES);
        return chats;
    }

    /**
     * 获取某个聊天的历史消息。
     * 查询顺序：先缓存，后数据库，最后回填缓存。
     *
     * @param chatId 聊天 id
     * @return 历史记录
     */
    @Override
    public List<ChatMessageDTO> getMessageHistory(String chatId) {
        Long userId = getCurrentUserId();
        String cacheKey = buildHistoryCacheKey(userId, chatId);

        // 先查消息历史缓存，命中则直接返回
        List<ChatMessageDTO> history = CacheUtil.getL2Cache(redisService, cacheKey, new TypeReference<>() {
        }, caffeineCache);
        if (history != null) {
            return history;
        }

        // 缓存未命中时查数据库，并回填消息历史缓存
        history = chatMessageMapper.selectList(
                        new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getChatId, chatId)
                                .eq(ChatMessage::getUserId, userId)
                                .orderByAsc(ChatMessage::getId)
                ).stream()
                .map(ChatMessageDTO::fromEntity)
                .collect(Collectors.toList());
        CacheUtil.setL2Cache(redisService, cacheKey, history, caffeineCache, CACHE_TIMEOUT, TimeUnit.MINUTES);
        return history;
    }

    /**
     * 构建聊天列表。
     * 从数据库查询当前用户全部聊天会话，并转换成 DTO 列表。
     *
     * @param userId 用户 id
     * @return 聊天列表
     */
    private List<ChatSessionDTO> buildChatInfoList(Long userId) {
        return chatSessionMapper.selectList(
                        new LambdaQueryWrapper<ChatSession>()
                                .eq(ChatSession::getUserId, userId)
                                .orderByDesc(ChatSession::getId)
                ).stream()
                .map(chatSession -> new ChatSessionDTO(chatSession.getId(), userId, chatSession.getTitle()))
                .collect(Collectors.toList());
    }

    /**
     * 删除聊天元信息缓存
     *
     * @param chatId 聊天 id
     * @param userId 用户 id
     */
    private void clearChatMetaCache(String chatId, Long userId) {
        CacheUtil.delL2Cache(buildChatCacheKey(userId, chatId), caffeineCache, redisService);
    }

    /**
     * 删除聊天列表缓存
     *
     * @param userId 用户 id
     */
    private void clearChatListCache(Long userId) {
        CacheUtil.delL2Cache(buildChatListCacheKey(userId), caffeineCache, redisService);
    }

    /**
     * 删除聊天历史缓存
     *
     * @param chatId 聊天 id
     * @param userId 用户 id
     */
    private void clearHistoryCache(String chatId, Long userId) {
        CacheUtil.delL2Cache(buildHistoryCacheKey(userId, chatId), caffeineCache, redisService);
    }

    /**
     * 规范化标题
     *
     * @param title 原始标题
     * @return 标题
     */
    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "无标题";
        }
        return title.length() > 64 ? title.substring(0, 64) : title;
    }

    /**
     * 获取当前登录用户 id
     *
     * @return 用户 id
     */
    private Long getCurrentUserId() {
        return Long.valueOf(JwtUtil.getUserId(SecurityUtil.getToken(), secret));
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
