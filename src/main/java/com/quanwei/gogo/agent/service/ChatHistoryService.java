package com.quanwei.gogo.agent.service;

import com.quanwei.gogo.agent.entity.ChatMessage;

import java.util.List;

/**
 * 对话历史持久化
 */
public interface ChatHistoryService {

    /**
     * 持久化用户会话
     * 1. 定位会话
     * 2. 定标题
     * 3. 写消息
     *
     * @param conversationId 会话 ID，前端叫 sessionId；库里没有时自动建会话
     * @param userId         当前登录用户，由 controller 从 token 取，不接受前端传
     * @param content        用户这一轮的输入
     */
    void saveUserMessage(String conversationId, String userId, String content);

    /**
     * 查询某个会话下的全部消息，按时间正序，也就是对话本身的顺序
     */
    List<ChatMessage> listMessages(String conversationId, String userId);
}
