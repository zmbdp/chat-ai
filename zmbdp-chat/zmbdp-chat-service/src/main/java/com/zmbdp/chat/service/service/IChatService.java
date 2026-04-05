package com.zmbdp.chat.service.service;

import com.zmbdp.chat.service.domain.dto.ChatSessionDTO;
import com.zmbdp.chat.service.domain.dto.ChatMessageDTO;

import java.util.List;

public interface IChatService {

    /**
     * 保存聊天会话
     *
     * @param chatId 聊天 id
     * @param title  会话标题
     */
    void save(String chatId, String title);

    /**
     * 保存一条聊天消息
     *
     * @param chatId    聊天 id
     * @param role      消息角色
     * @param content   消息内容
     * @param mediaUrls 用户消息中的图片等 URL，非用户消息或无图时传 null
     */
    void saveMessage(String chatId, String role, String content, List<String> mediaUrls);

    /**
     * 根据聊天 id 删除会话
     *
     * @param chatId 聊天 id
     */
    void deleteByChatId(String chatId);

    /**
     * 获取所有会话
     *
     * @return 会话列表
     */
    List<ChatSessionDTO> getSessionHistory();

    /**
     * 获取会话历史记录
     *
     * @param chatId 聊天 id
     * @return 历史记录
     */
    List<ChatMessageDTO> getMessageHistory(String chatId);
}
