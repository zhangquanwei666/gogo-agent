package com.quanwei.gogo.agent.service;

import com.quanwei.gogo.agent.bo.ChatHistorySaveBO;

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
}
