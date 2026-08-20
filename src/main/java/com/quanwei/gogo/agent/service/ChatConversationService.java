package com.quanwei.gogo.agent.service;

import com.quanwei.gogo.agent.bo.ChatConversationBO;

import java.util.List;

/**
 * 对话会话业务处理。只做业务编排和校验，数据存取全部委托给 ChatConversationDao。
 */
public interface ChatConversationService {

    /**
     * 创建会话，conversationId 由雪花算法生成
     *
     * @param title 标题，传空则用库里的默认值「新对话」
     * @return 生成的 conversationId
     */
    String createConversation(String userId, String title);

    /** 查用户的全部会话，最近更新的排前面 */
    List<ChatConversationBO> listByUserId(String userId);

    /** 重命名会话标题 */
    boolean renameTitle(String conversationId, String userId, String title);

    /** 逻辑删除会话，连同会话下的所有消息一起删 */
    boolean removeConversation(String conversationId, String userId);
}
