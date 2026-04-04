package com.zmbdp.chat.service.repository;

import com.zmbdp.chat.service.entity.ChatInfo;

import java.util.List;

public interface ChatHistoryRepository {

    /**
     * 保存会话
     *
     * @param chatId 会话 id
     * @param title  会话标题
     */
    void save(String chatId, String title);

    /**
     * 根据会话 id 删除会话
     *
     * @param chatId 会话 id
     */
    void clearByChatId(String chatId);

    /**
     * 获取所有会话
     *
     * @return 会话列表
     */
    List<ChatInfo> getChats();
}