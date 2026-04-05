package com.zmbdp.chat.service.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 聊天会话表
 *
 * @author 稚名不带撇
 */
@Data
@TableName("chat_session")
public class ChatSession {

    /**
     * 会话 id
     */
    private String id;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 会话标题
     */
    private String title;
}