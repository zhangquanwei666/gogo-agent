package com.quanwei.gogo.agent.bo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * 持久化用户消息的入参
 */
@Getter
@Setter
@ToString
public class ChatHistorySaveBO implements Serializable {

    /** 当前登录用户，由 controller 从 token 取，不接受前端传 */
    private String userId;

    /**
     * 会话 ID，对应前端的 sessionId。
     */
    private String conversationId;

    /** 用户这一轮的输入，必填 */
    private String content;
}
