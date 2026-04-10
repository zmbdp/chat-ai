package com.zmbdp.chat.service.service;

import com.zmbdp.chat.service.domain.dto.ChatSendReqDTO;
import com.zmbdp.chat.service.domain.dto.ChatSessionDTO;

/**
 * 聊天会话服务
 *
 * @author 稚名不带撇
 */
public interface IChatSessionService {

    /**
     * 插入或更新聊天会话
     *
     * @param chatSendReqDTO 消息发送参数
     * @return 插入或更新结果
     */
    Boolean insertOrUpdate(ChatSendReqDTO chatSendReqDTO);

    /**
     * 根据 chatId 查询聊天会话
     *
     * @param chatId 聊天 id
     * @param userId 用户 id
     * @return 聊天会话
     */
    ChatSessionDTO getByChatId(String chatId, Long userId);
}