package com.quanwei.gogo.agent.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话详情里的单条消息。
 * 这是列表项不是响应体，所以不继承 BaseResponse，同 {@link ChatConversationDTO}。
 *
 * <p>不回显 conversationId：整个列表都属于同一个会话，逐条重复没有意义，
 * 会话 ID 在响应体那一层给一次就够了。
 *
 * <p>也不回显 extra 原文。那是排障用的附加信息（当前存的是意图识别结果的完整 JSON），
 * 界面上只用得到里面的 source 一项，已经抽成 {@link #intentSource}；
 * 整块吐给前端等于把存储细节暴露成接口契约，以后 extra 换结构就会连累前端。
 */
@Getter
@Setter
@ToString
public class ChatMessageDTO implements Serializable {

    /** 消息 ID，点赞点踩要用 */
    private String messageId;

    /** 角色，取值见 MessageRoleEnum：user / agent / system */
    private String role;

    /** 消息正文 */
    private String content;

    /** 回复来自哪个智能体，role=agent 时才有值 */
    private String agentName;

    /**
     * 意图由哪一级给出：L1 规则 / L2 向量 / L3 大模型。
     * 从 extra 里抽出来的，取不到就是 null —— 老数据和用户消息都没有这一项。
     */
    private String intentSource;

    /** 用户反馈，取值见 FeedbackEnum，null 表示未反馈 */
    private String feedback;

    /** 发送时间，列表按它正序 */
    private LocalDateTime createdTime;
}
