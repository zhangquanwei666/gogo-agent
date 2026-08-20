package com.quanwei.gogo.agent.bo;

import com.quanwei.gogo.agent.common.MessageRoleEnum;

/**
 * 新增对话消息的业务对象。
 * 字段偏多，用对象传避免一长串位置参数写错顺序。
 *
 * @param conversationId 所属会话
 * @param role           消息角色
 * @param content        消息内容
 * @param agentName      Agent 名称，role=AGENT 时才需要
 * @param extra          扩展信息，JSON 字符串，可为 null
 */
public record ChatMessageCreateBO(String conversationId,
                                  MessageRoleEnum role,
                                  String content,
                                  String agentName,
                                  String extra) {

    /** 用户消息的快捷构造 */
    public static ChatMessageCreateBO ofUser(String conversationId, String content) {
        return new ChatMessageCreateBO(conversationId, MessageRoleEnum.USER, content, null, null);
    }

    /** Agent 消息的快捷构造 */
    public static ChatMessageCreateBO ofAgent(String conversationId, String content, String agentName) {
        return new ChatMessageCreateBO(conversationId, MessageRoleEnum.AGENT, content, agentName, null);
    }
}
