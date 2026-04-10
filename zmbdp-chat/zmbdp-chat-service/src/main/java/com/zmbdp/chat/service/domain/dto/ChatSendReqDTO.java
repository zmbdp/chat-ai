package com.zmbdp.chat.service.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息发送参数
 *
 * @author 稚名不带撇
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSendReqDTO {

    /**
     * 聊天 id
     */
    @NotNull(message = "聊天id不能为空")
    private String chatId;

    /**
     * 会话标题
     */
    @NotNull(message = "标题不能为空")
    private String title;

    /**
     * 用户 id
     */
    @NotNull(message = "用户id不能为空")
    private Long userId;
}