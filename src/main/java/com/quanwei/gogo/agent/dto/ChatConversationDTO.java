package com.quanwei.gogo.agent.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话列表里的单条记录。
 * 这是列表项不是响应体，所以不继承 BaseResponse。
 */
@Getter
@Setter
@ToString
public class ChatConversationDTO implements Serializable {

    /** 会话ID */
    private String conversationId;

    /** 会话标题 */
    private String title;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 最后更新时间，列表按它倒序 */
    private LocalDateTime updatedTime;
}
