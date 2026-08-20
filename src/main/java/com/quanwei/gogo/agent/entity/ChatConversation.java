package com.quanwei.gogo.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话会话表 chat_conversation
 */
@Getter
@Setter
@ToString
@TableName("chat_conversation")
public class ChatConversation implements Serializable {

    /**
     * 主键，雪花算法生成。
     * IdType.ASSIGN_ID 由 MP 的 DefaultIdentifierGenerator 生成 Long 型雪花 ID，
     * 字段声明为 String，MP 会自动转成数字字符串写进 VARCHAR(64) 列。
     */
    @TableId(value = "conversation_id", type = IdType.ASSIGN_ID)
    private String conversationId;

    /** 归属用户，对应 user_account.user_id */
    @TableField("user_id")
    private String userId;

    /** 会话标题，库里默认「新对话」 */
    @TableField("title")
    private String title;

    /** 创建时间，由 MetaObjectHandler 自动填充 */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 最后更新时间，由 MetaObjectHandler 自动填充 */
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /**
     * 逻辑删除标志。
     * 标了 @TableLogic 之后，delete 会变成 update deleted=1，
     * 所有查询也会自动带上 deleted=0 的条件。
     */
    @TableLogic(value = "0", delval = "1")
    @TableField("deleted")
    private Integer deleted;
}
