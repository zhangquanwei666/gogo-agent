package com.quanwei.gogo.agent.dto;

import com.quanwei.gogo.agent.common.BaseRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 查询某个会话下全部消息的入参。
 * 和 {@link ChatConversationListReqDTO} 一样刻意不含 userId —— 查谁的会话由 token 决定。
 * 会话归属校验在 ChatHistoryService.listMessages 里做，传别人的 conversationId 会拿到 403。
 */
@Getter
@Setter
@ToString(callSuper = true)
public class ChatMessageListReqDTO extends BaseRequest {

    /** 要查的会话 ID，前端叫 sessionId。必填 */
    private String conversationId;
}
