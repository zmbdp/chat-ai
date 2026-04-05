package com.zmbdp.chat.service.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zmbdp.common.domain.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 聊天消息表
 *
 * @author 稚名不带撇
 */
@Data
@TableName("chat_message")
@EqualsAndHashCode(callSuper = true)
public class ChatMessage extends BaseDO {

    /**
     * 聊天 id
     */
    @TableField("chat_id")
    private String chatId;

    /**
     * 用户 id
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 消息角色
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;
}