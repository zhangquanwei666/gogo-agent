CREATE TABLE IF NOT EXISTS `user_account`
(
    `user_id`      VARCHAR(64)  NOT NULL COMMENT '用户唯一标识，业务主键',
    `username`     VARCHAR(64)  NOT NULL COMMENT '登录账号',
    `email`        VARCHAR(128) DEFAULT NULL COMMENT '邮箱，可用于登录',
    `password`     VARCHAR(128) NOT NULL COMMENT '登录密码',
    `real_name`    VARCHAR(64)  DEFAULT NULL COMMENT '用户真实姓名',
    `role`         VARCHAR(16)  DEFAULT NULL COMMENT '角色',
    `created_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
    `modify_time`  DATETIME     DEFAULT NULL COMMENT '最后变更时间',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '用户登录账号表';


CREATE TABLE IF NOT EXISTS `chat_conversation`
(
    `conversation_id` VARCHAR(64)  NOT NULL COMMENT '会话ID，雪花算法生成',
    `user_id`         VARCHAR(64)  NOT NULL COMMENT '用户ID',
    `title`           VARCHAR(256) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    `created_time`    DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updated_time`    DATETIME     DEFAULT NULL COMMENT '最后更新时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标志：0 未删除 / 1 已删除',
    PRIMARY KEY (`conversation_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_updated_time` (`updated_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '对话会话';

CREATE TABLE IF NOT EXISTS `chat_message`
(
    `message_id`      VARCHAR(64)  NOT NULL COMMENT '消息ID，雪花算法生成',
    `conversation_id` VARCHAR(64)  NOT NULL COMMENT '会话ID',
    `role`            VARCHAR(32)  NOT NULL COMMENT '角色：user/agent/system',
    `content`         TEXT         DEFAULT NULL COMMENT '消息内容',
    `agent_name`      VARCHAR(128) DEFAULT NULL COMMENT 'Agent名称（role=agent 时）',
    `extra`           JSON         DEFAULT NULL COMMENT '扩展信息（进度快照/推荐问题等）',
    `feedback`        VARCHAR(16)  DEFAULT NULL COMMENT '用户反馈：LIKE 点赞 / DISLIKE 点踩 / NULL 未反馈',
    `feedback_time`   DATETIME     DEFAULT NULL COMMENT '反馈时间',
    `created_time`    DATETIME     DEFAULT NULL COMMENT '创建时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标志：0 未删除 / 1 已删除',
    PRIMARY KEY (`message_id`),
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_created_time` (`created_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '对话消息';
