package com.quanwei.gogo.agent.dto;

import com.quanwei.gogo.agent.common.BaseResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 发送消息的返回值。
 */
@Getter
@Setter
@ToString(callSuper = true)
public class ChatResponse extends BaseResponse {

    /** 会话 ID，原样回显。前端首次发送时用它确认后端建的是哪个会话 */
    private String sessionId;

    /** 智能体回复落库后的消息 ID，前端点赞点踩要用 */
    private String messageId;

    /** 智能体的回复正文 */
    private String reply;

    /** 回复来自哪个智能体，落 chat_message.agent_name 的那个值 */
    private String agentName;

    /**
     * 本轮识别到的主意图 code，如 {@code approval_query}。
     * 给前端做埋点和「思考过程」展示用；识别不出来时是 {@code unknown}
     */
    private String intent;

    /**
     * 意图由哪一级给出：L1 / L2 / L3。
     * 前端不需要它，但接口调试期放着很值 —— 一眼能看出这句话有没有走到大模型
     */
    private String intentSource;

    /** 本轮是否被用户中断 */
    private Boolean interrupted;
}
