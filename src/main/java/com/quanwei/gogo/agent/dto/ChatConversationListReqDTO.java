package com.quanwei.gogo.agent.dto;

import com.quanwei.gogo.agent.common.BaseRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 查询用户会话列表的入参。
 * 刻意不含 userId —— 查谁的会话由 token 决定，不由调用方说了算。
 * 目前只继承了 BaseRequest 的 traceId，留作后续加筛选条件（标题、时间范围等）的位置。
 */
@Getter
@Setter
@ToString(callSuper = true)
public class ChatConversationListReqDTO extends BaseRequest {
}
