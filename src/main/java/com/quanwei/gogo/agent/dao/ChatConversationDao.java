package com.quanwei.gogo.agent.dao;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quanwei.gogo.agent.entity.ChatConversation;
import com.quanwei.gogo.agent.mapper.ChatConversationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * chat_conversation 数据访问层。
 * 约定：所有对 chat_conversation 的数据库操作都收口在这个类，service 不直接碰 mapper。
 * 实体标了 @TableLogic，这里所有查询会自动带上 deleted = 0，删除也会自动变成逻辑删除。
 */
@Repository
public class ChatConversationDao {

    @Autowired
    private ChatConversationMapper chatConversationMapper;

    /** 新增会话，conversationId 由 MP 的雪花算法生成，调用方不用设置 */
    public int insert(ChatConversation conversation) {
        return chatConversationMapper.insert(conversation);
    }

    /**
     * 按会话 ID 加用户 ID 查询。
     * 多带一个 user_id 条件是为了做归属校验，避免越权访问别人的会话。
     */
    public ChatConversation selectByIdAndUserId(String conversationId, String userId) {
        if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(userId)) {
            return null;
        }
        return chatConversationMapper.selectOne(Wrappers.<ChatConversation>lambdaQuery()
                .eq(ChatConversation::getConversationId, conversationId)
                .eq(ChatConversation::getUserId, userId));
    }

    /** 查某个用户的全部会话，最近更新的排前面 */
    public List<ChatConversation> selectByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        return chatConversationMapper.selectList(Wrappers.<ChatConversation>lambdaQuery()
                .eq(ChatConversation::getUserId, userId)
                .orderByDesc(ChatConversation::getUpdatedTime));
    }

    /** 改标题，带 user_id 条件做归属校验 */
    public int updateTitle(String conversationId, String userId, String title) {
        return chatConversationMapper.update(null, Wrappers.<ChatConversation>lambdaUpdate()
                .eq(ChatConversation::getConversationId, conversationId)
                .eq(ChatConversation::getUserId, userId)
                .set(ChatConversation::getTitle, title)
                // 这里是条件更新，走不到 MetaObjectHandler 的自动填充，得手动设
                .set(ChatConversation::getUpdatedTime, LocalDateTime.now()));
    }

    /** 刷新最后更新时间，会话里新增消息时调用，让它在列表里冒泡到最前 */
    public int touch(String conversationId) {
        return chatConversationMapper.update(null, Wrappers.<ChatConversation>lambdaUpdate()
                .eq(ChatConversation::getConversationId, conversationId)
                .set(ChatConversation::getUpdatedTime, LocalDateTime.now()));
    }

    /** 逻辑删除，实际执行的是 update deleted = 1 */
    public int deleteByIdAndUserId(String conversationId, String userId) {
        return chatConversationMapper.delete(Wrappers.<ChatConversation>lambdaQuery()
                .eq(ChatConversation::getConversationId, conversationId)
                .eq(ChatConversation::getUserId, userId));
    }
}
