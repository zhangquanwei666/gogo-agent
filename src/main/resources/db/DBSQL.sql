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
