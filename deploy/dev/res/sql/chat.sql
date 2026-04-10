use chat-ai_dev;
drop table if exists `chat_session`;
CREATE TABLE `chat_session`
(
    `id`      VARCHAR(64) NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `title`   varchar(64) NULL DEFAULT NULL COMMENT '会话标题',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COMMENT = '咨询会话表';

drop table if exists `chat_message`;
CREATE TABLE `chat_message`
(
    `id`         BIGINT(20)      NOT NULL COMMENT '主键 id',
    `chat_id`    VARCHAR(64) NOT NULL COMMENT '聊天 id',
    `user_id`    BIGINT(20)      NOT NULL COMMENT '用户 id',
    `role`       VARCHAR(20) NOT NULL COMMENT '消息角色',
    `content`    LONGTEXT    NOT NULL COMMENT '消息内容',
    `media_urls` TEXT NULL COMMENT '用户消息附带图片等地址(JSON 数组)，如 ["https://.../a.png"]',
    PRIMARY KEY (`id`),
    KEY          `idx_chat_message_chat_user` (`chat_id`, `user_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';
