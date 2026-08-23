package com.quanwei.gogo.agent.service;

import com.quanwei.gogo.agent.bo.ChatHistorySaveBO;
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
     */
    void saveUserMessage(ChatHistorySaveBO chatHistorySaveBO);

    /**
     * 查询某个会话下的全部消息，按时间正序，也就是对话本身的顺序
     */
    List<ChatMessage> listMessages(String conversationId, String userId);
}
