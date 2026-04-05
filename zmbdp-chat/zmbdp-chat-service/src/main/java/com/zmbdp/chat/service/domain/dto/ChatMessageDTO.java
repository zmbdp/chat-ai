package com.zmbdp.chat.service.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天历史消息
 *
 * @author 稚名不带撇
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {

    /**
     * 消息角色
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;
}