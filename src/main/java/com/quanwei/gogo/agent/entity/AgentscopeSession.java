package com.quanwei.gogo.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AgentScope 会话状态表 agentscope_session。
 */
@Getter
@Setter
@ToString(exclude = "stateData")
@TableName("agentscope_session")
public class AgentscopeSession implements Serializable {

    /** 会话标识，对应 AgentScope 侧的 sessionId */
    @TableField("session_id")
    private String sessionId;

    /** 状态分类标识，比如 memory / agent 名字，由 AgentScope 决定 */
    @TableField("state_key")
    private String stateKey;

    /** 同一个 state_key 下的序号，从 0 开始，读回来要按它正序还原 */
    @TableField("item_index")
    private Integer itemIndex;

    /** 状态内容，库里是 LONGTEXT，一般是序列化后的 JSON */
    @TableField("state_data")
    private String stateData;

    /** 创建时间，由 MetaObjectHandler 自动填充 */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 最后更新时间，由 MetaObjectHandler 自动填充 */
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
