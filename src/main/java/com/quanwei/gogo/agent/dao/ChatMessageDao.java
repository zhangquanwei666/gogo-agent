package com.quanwei.gogo.agent.dao;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quanwei.gogo.agent.entity.ChatMessage;
import com.quanwei.gogo.agent.mapper.ChatMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * chat_message 数据访问层。
 * 约定：所有对 chat_message 的数据库操作都收口在这个类，service 不直接碰 mapper。
 * 实体标了 @TableLogic，这里所有查询会自动带上 deleted = 0。
 */
@Repository
public class ChatMessageDao {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    /** 新增消息，messageId 由 MP 的雪花算法生成，调用方不用设置 */
    public int insert(ChatMessage message) {
        return chatMessageMapper.insert(message);
    }

    /** 批量新增，一问一答通常成对写入 */
    public int insertBatch(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ChatMessage message : messages) {
            count += chatMessageMapper.insert(message);
        }
        return count;
    }

    /** 按消息 ID 查询 */
    public ChatMessage selectByMessageId(String messageId) {
        if (!StringUtils.hasText(messageId)) {
            return null;
        }
        return chatMessageMapper.selectById(messageId);
    }

    /** 查某个会话的全部消息，按创建时间正序，也就是对话本身的顺序 */
    public List<ChatMessage> selectByConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return List.of();
        }
        return chatMessageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getCreatedTime));
    }

    /** 更新反馈，feedback 传 null 表示撤销反馈 */
    public int updateFeedback(String messageId, String feedback, LocalDateTime feedbackTime) {
        return chatMessageMapper.update(null, Wrappers.<ChatMessage>lambdaUpdate()
                .eq(ChatMessage::getMessageId, messageId)
                // set 的第一个参数是条件，这里恒为 true，保证 null 也能被写进去
                .set(true, ChatMessage::getFeedback, feedback)
                .set(true, ChatMessage::getFeedbackTime, feedbackTime));
    }

    /** 逻辑删除整个会话的消息，删会话时连带调用 */
    public int deleteByConversationId(String conversationId) {
        return chatMessageMapper.delete(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId));
    }
}
