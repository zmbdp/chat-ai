package com.zmbdp.chat.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.zmbdp.chat.service.domain.dto.ChatSendReqDTO;
import com.zmbdp.chat.service.domain.dto.ChatSessionDTO;
import com.zmbdp.chat.service.domain.entity.ChatSession;
import com.zmbdp.chat.service.mapper.ChatSessionMapper;
import com.zmbdp.chat.service.service.IChatSessionService;
import com.zmbdp.common.cache.utils.CacheUtil;
import com.zmbdp.common.core.utils.BeanCopyUtil;
import com.zmbdp.common.redis.service.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 聊天会话服务实现类
 *
 * @author 稚名不带撇
 */
@Slf4j
@Service
public class ChatSessionServiceImpl implements IChatSessionService {

    /**
     * 聊天元信息缓存前缀
     */
    private static final String CHAT_CACHE_KEY_PREFIX = "chat:meta:";

    /**
     * 聊天列表缓存前缀
     */
    private static final String CHAT_LIST_CACHE_KEY_PREFIX = "chat:list:";

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
     * redis 服务
     */
    @Autowired
    private RedisService redisService;

    /**
     * caffeine 缓存
     */
    @Autowired
    private Cache<String, Object> caffeineCache;

    /**
     * 插入或更新聊天会话
     * 处理流程：
     * 1. 删除当前聊天元信息缓存和聊天列表缓存
     * 2. 查询当前聊天会话是否存在
     * 3. 不存在则插入，存在且标题为空则更新标题
     * 4. 回填聊天元信息缓存和聊天列表缓存
     *
     * @param chatSendReqDTO 消息发送参数
     * @return 插入或更新结果
     */
    @Override
    public Boolean insertOrUpdate(ChatSendReqDTO chatSendReqDTO) {
        // 先获取用户 id 和聊天 id，用于后续缓存删除和数据库操作
        Long userId = chatSendReqDTO.getUserId();
        String chatId = chatSendReqDTO.getChatId();

        // 先删除缓存，避免读到旧的聊天元信息和聊天列表
        clearChatMetaCache(chatId, userId);
        clearChatListCache(userId);

        // 查询当前聊天会话，判断是插入新会话还是补写标题
        ChatSessionDTO chatSessionDTO = getByChatId(chatId, userId);
        ChatSession chatSession = new ChatSession();
        if (chatSessionDTO == null || chatSessionDTO.getId() == null || chatSessionDTO.getUserId() == null) {
            // 没查到记录，说明是新会话，直接插入
            chatSession.setId(chatId);
            chatSession.setUserId(userId);
            chatSession.setTitle(chatSendReqDTO.getTitle());
            if (chatSessionMapper.insert(chatSession) <= 0) {
                return false;
            }
            chatSessionDTO = new ChatSessionDTO(chatSession.getId(), chatSession.getUserId(), chatSession.getTitle());
        } else if (chatSessionDTO.getTitle() == null || chatSessionDTO.getTitle().isBlank()) {
            // 已存在但标题为空，则补写标题
            chatSession.setId(chatId);
            chatSession.setUserId(userId);
            chatSession.setTitle(chatSendReqDTO.getTitle());
            if (chatSessionMapper.updateById(chatSession) <= 0) {
                return false;
            }
            chatSessionDTO = new ChatSessionDTO(chatSession.getId(), chatSession.getUserId(), chatSession.getTitle());
        }

        // 更新聊天元信息缓存和聊天列表缓存，确保后续读取直接命中缓存
        CacheUtil.setL2Cache(redisService, buildChatCacheKey(userId, chatId), chatSessionDTO, caffeineCache, CACHE_TIMEOUT, TimeUnit.MINUTES);
        CacheUtil.setL2Cache(redisService, buildChatListCacheKey(userId), buildChatInfoList(userId), caffeineCache, CACHE_TIMEOUT, TimeUnit.MINUTES);
        return true;
    }

    /**
     * 根据 chatId 查询聊天会话
     * 查询顺序：先缓存，后数据库，最后回填缓存。
     *
     * @param chatId 聊天 id
     * @param userId 用户 id
     * @return 聊天会话
     */
    @Override
    public ChatSessionDTO getByChatId(String chatId, Long userId) {
        // 先查缓存，命中则直接返回
        String cacheKey = buildChatCacheKey(userId, chatId);
        ChatSessionDTO chatSessionDTO = CacheUtil.getL2Cache(redisService, cacheKey, ChatSessionDTO.class, caffeineCache);
        if (chatSessionDTO != null) {
            return chatSessionDTO;
        }

        // 缓存没有命中时，再查询数据库
        ChatSession chatSession = chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getId, chatId)
                .eq(ChatSession::getUserId, userId)
                .last("limit 1"));
        if (chatSession != null) {
            // 数据库查到后转换成 DTO，并回填缓存
            chatSessionDTO = BeanCopyUtil.copyProperties(chatSession, ChatSessionDTO.class);
            CacheUtil.setL2Cache(redisService, cacheKey, chatSessionDTO, caffeineCache, CACHE_TIMEOUT, TimeUnit.MINUTES);
        }
        return chatSessionDTO;
    }

    /**
     * 构建聊天列表
     * 从数据库查询当前用户所有聊天会话，并转换成前端返回需要的 DTO 列表。
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
}