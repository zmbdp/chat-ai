package com.zmbdp.chat.service.domain.vo;

import lombok.Data;

@Data
public class ChatSessionVO {
    private String chatId;
    private String title;

    public ChatSessionVO(String chatId, String title) {
        this.chatId = chatId;
        this.title = title == null ? "无标题" : title.length() >= 15 ? title.substring(0, 15) : title;
    }
}