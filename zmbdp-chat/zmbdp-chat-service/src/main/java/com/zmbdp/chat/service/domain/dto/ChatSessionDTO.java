package com.zmbdp.chat.service.domain.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话信息
 *
 * @author 稚名不带撇
 */
@Data
@NoArgsConstructor
public class ChatSessionDTO {

    /**
     * 聊天 id
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

    public ChatSessionDTO(String id, Long userId, String title) {
        this.id = id;
        this.userId = userId;
        this.title = title == null ? "无标题" : title.length() >= 15 ? title.substring(0, 15) : title;
    }
}