package com.zmbdp.chat.service.domain.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 聊天请求参数
 *
 * @author 稚名不带撇
 */
@Data
public class StreamReqDTO {

    /**
     * 内容
     */
    @NotNull(message = "请输入内容")
    private String prompt;

    /**
     * 会话 id
     */
    @NotNull(message = "请选择会话")
    private String chatId;

    /**
     * 图片地址
     */
    private String imageUrl;
}