package com.zmbdp.chat.service.mq.listener;

import com.zmbdp.chat.service.config.RabbitMqConfig;
import com.zmbdp.chat.service.domain.dto.ChatSendReqDTO;
import com.zmbdp.chat.service.service.IChatSessionService;
import com.zmbdp.common.core.utils.JsonUtil;
import com.zmbdp.common.domain.exception.ServiceException;
import com.zmbdp.common.redis.service.RedissonLockService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * 持久化聊天消息
 *
 * @author 稚名不带撇
 */
@Slf4j
@Component
@RabbitListener(bindings = {@QueueBinding(
        value = @Queue(),
        exchange = @Exchange(value = RabbitMqConfig.EXCHANGE_NAME, type = ExchangeTypes.FANOUT)
)})
public class ChatSessionConsumer {

    /**
     * 存储聊天消息分布式锁 key
     */
    private static final String LOCK_KEY = "chat:db:lock";

    /**
     * 分布式锁服务
     */
    @Autowired
    private RedissonLockService redissonLockService;

    /**
     * 消息服务
     */
    @Autowired
    private IChatSessionService messageService;

    /**
     * 监听聊天消息
     *
     * @param chatSendReqDTO 聊天消息
     */
    @RabbitHandler
    @Transactional(rollbackFor = Exception.class)
    public void process(ChatSendReqDTO chatSendReqDTO) {
        RLock lock = redissonLockService.acquire(LOCK_KEY, 0, TimeUnit.SECONDS);
        if (lock == null) {
            return;
        }
        try {
            if (!messageService.insertOrUpdate(chatSendReqDTO)) {
                throw new ServiceException("聊天消息持久化失败！");
            }
        } catch (Exception e) {
            log.error("消息持久化异常！chatSendReqDTO:{}", JsonUtil.classToJson(chatSendReqDTO), e);
            throw e;
        } finally {
            redissonLockService.releaseLock(lock);
        }
    }
}
