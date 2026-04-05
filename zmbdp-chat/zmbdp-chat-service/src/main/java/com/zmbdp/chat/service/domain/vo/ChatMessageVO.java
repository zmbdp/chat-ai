package com.zmbdp.chat.service.domain.vo;

import com.zmbdp.chat.service.domain.dto.ChatMessageDTO;
import lombok.Data;

@Data
public class ChatMessageVO {

    /**
     * 消息角色
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    public ChatMessageVO(ChatMessageDTO chatMessageDTO) {
        this.role = chatMessageDTO.getRole();
        this.content = chatMessageDTO.getContent();
    }
}