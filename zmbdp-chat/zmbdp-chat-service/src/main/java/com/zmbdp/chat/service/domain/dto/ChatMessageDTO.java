package com.zmbdp.chat.service.domain.dto;

import com.zmbdp.chat.service.domain.entity.ChatMessage;
import com.zmbdp.common.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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

    /**
     * 用户消息中的图片等媒体 URL 列表（持久化后可跨重启恢复多模态上下文）
     */
    private List<String> mediaUrls;

    /**
     * 从实体组装 DTO，并解析 {@link ChatMessage#getMediaUrls()} JSON
     */
    public static ChatMessageDTO fromEntity(ChatMessage message) {
        List<String> urls = null;
        String raw = message.getMediaUrls();
        if (raw != null && !raw.isBlank()) {
            List<String> parsed = JsonUtil.jsonToList(raw, String.class);
            if (parsed != null && !parsed.isEmpty()) {
                urls = parsed;
            }
        }
        return new ChatMessageDTO(message.getRole(), message.getContent(), urls);
    }
}