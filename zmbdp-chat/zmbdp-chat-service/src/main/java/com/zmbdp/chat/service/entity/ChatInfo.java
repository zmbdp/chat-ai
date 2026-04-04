package com.zmbdp.chat.service.entity;

import lombok.Data;

@Data
public class ChatInfo {
    private String chatId;
    private String title;

    public ChatInfo(String chatId, String title) {
        this.chatId = chatId;
        this.title = title == null ? "无标题" : title.length() >= 15 ? title.substring(0, 15) : title;
    }
}