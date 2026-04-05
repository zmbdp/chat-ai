package com.zmbdp.chat.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zmbdp.chat.service.domain.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息表 Mapper
 *
 * @author 稚名不带撇
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
