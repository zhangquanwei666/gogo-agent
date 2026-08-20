package com.quanwei.gogo.agent.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * created_time / modify_time 自动填充，业务代码不用手动 set
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createdTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "modifyTime", LocalDateTime.class, now);
        // chat_conversation 用的是 updated_time，字段名跟 user_account 不一样，两个都填
        this.strictInsertFill(metaObject, "updatedTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictUpdateFill(metaObject, "modifyTime", LocalDateTime.class, now);
        this.strictUpdateFill(metaObject, "updatedTime", LocalDateTime.class, now);
    }
}
