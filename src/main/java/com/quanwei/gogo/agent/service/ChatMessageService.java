package com.quanwei.gogo.agent.service;

import com.quanwei.gogo.agent.bo.ChatMessageCreateBO;
import com.quanwei.gogo.agent.common.FeedbackEnum;
import com.quanwei.gogo.agent.entity.ChatMessage;

import java.util.List;

/**
 * 对话消息业务处理。只做业务编排和校验，数据存取全部委托给 ChatMessageDao。
 */
public interface ChatMessageService {

    /**
     * 往消息表中添加一条消息，messageId 由雪花算法生成。
     * 写入成功后会刷新所属会话的 updated_time，让会话在列表里冒泡到最前。
     *
     * @return 生成的 messageId
     */
    String addMessage(ChatMessageCreateBO chatMessageCreateBO);

    /** 查会话的全部消息，按时间正序，也就是对话本身的顺序 */
    List<ChatMessage> listByConversationId(String conversationId);

    /**
     * 提交或撤销反馈
     *
     * @param feedback 传 null 表示撤销反馈，反馈时间一并清空
     */
    boolean feedback(String messageId, FeedbackEnum feedback);
}
