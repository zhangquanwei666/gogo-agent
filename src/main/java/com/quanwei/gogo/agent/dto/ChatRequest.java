package com.quanwei.gogo.agent.dto;

import com.quanwei.gogo.agent.common.BaseRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 发送消息的入参。
 *
 * <p>刻意不含 userId —— 发给谁的会话、以谁的身份发，由 token 决定，不由调用方说了算。
 * 带上的话就等于「拿到别人的 userId 就能往别人的会话里写消息」。
 */
@Getter
@Setter
@ToString(callSuper = true)
public class ChatRequest extends BaseRequest {

    /**
     * 会话 ID，对应后端的 conversationId。
     *
     * <p>由前端生成并在整个会话里保持不变：新开对话就换一个新的。
     * 后端发现库里没有这个 ID 时会自动建会话，所以前端不需要先调一次「创建会话」接口。
     */
    private String sessionId;

    /** 用户这一轮说的话 */
    private String content;
}
