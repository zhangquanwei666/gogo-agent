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
 * 对话消息表 chat_message
 */
@Getter
@Setter
@ToString
@TableName("chat_message")
public class ChatMessage implements Serializable {

    /** 主键，雪花算法生成，同 ChatConversation */
    @TableId(value = "message_id", type = IdType.ASSIGN_ID)
    private String messageId;

    /** 所属会话，对应 chat_conversation.conversation_id */
    @TableField("conversation_id")
    private String conversationId;

    /** 角色，取值见 MessageRoleEnum：user / agent / system */
    @TableField("role")
    private String role;

    /** 消息内容，库里是 TEXT */
    @TableField("content")
    private String content;

    /** Agent 名称，role=agent 时才有值 */
    @TableField("agent_name")
    private String agentName;

    /**
     * 扩展信息，库里是 JSON 列。
     * 这里按原始 JSON 字符串存取，不挂 TypeHandler ——
     * 项目里同时存在 Jackson 2 和 Jackson 3，交给业务层自己决定用哪个序列化更可控。
     */
    @TableField("extra")
    private String extra;

    /** 用户反馈，取值见 FeedbackEnum，null 表示未反馈 */
    @TableField("feedback")
    private String feedback;

    /** 反馈时间 */
    @TableField("feedback_time")
    private LocalDateTime feedbackTime;

    /** 创建时间，由 MetaObjectHandler 自动填充 */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 逻辑删除标志 */
    @TableLogic(value = "0", delval = "1")
    @TableField("deleted")
    private Integer deleted;
}
