package com.quanwei.gogo.agent.service.impl;

import com.quanwei.gogo.agent.bo.ChatHistorySaveBO;
import com.quanwei.gogo.agent.common.ErrorCodeEnum;
import com.quanwei.gogo.agent.common.MessageRoleEnum;
import com.quanwei.gogo.agent.dao.ChatConversationDao;
import com.quanwei.gogo.agent.dao.ChatMessageDao;
import com.quanwei.gogo.agent.entity.ChatConversation;
import com.quanwei.gogo.agent.entity.ChatMessage;
import com.quanwei.gogo.agent.exception.BizException;
import com.quanwei.gogo.agent.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ChatHistoryServiceImpl implements ChatHistoryService {

    /** 新建会话时的默认标题，也是「标题还没定型」的判断依据 */
    private static final String DEFAULT_TITLE = "新会话";

    /** 标题按用户输入截断的长度 */
    private static final int TITLE_MAX_CHARS = 24;

    @Autowired
    private ChatMessageDao chatMessageDao;

    @Autowired
    private ChatConversationDao chatConversationDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUserMessage(ChatHistorySaveBO chatHistorySaveBO) {
        String conversationId = chatHistorySaveBO.getConversationId();
        String userId = chatHistorySaveBO.getUserId();
        String content = chatHistorySaveBO.getContent();

        /* 创建会话*/
        ensureConversation(chatHistorySaveBO.getConversationId(), userId);

        /* 更新标题 */
        updateConversationTittle(conversationId,userId,content);

        /* 用户消息落库 */
        insertMessage(conversationId, content);

    }

    @Override
    public List<ChatMessage> listMessages(String conversationId, String userId) {

        /* 判断会话是否存在 */
        ChatConversation chatConversation = chatConversationDao.selectByConversationId(conversationId);
        if (chatConversation == null) {
            throw new BizException(ErrorCodeEnum.CONVERSATION_NOT_FOUND);
        }

        /* 越权校验 */
        if (!chatConversation.getUserId().equals(userId)) {
            throw new BizException(ErrorCodeEnum.CONVERSATION_FORBIDDEN);
        }

        /* 查该会话的全部消息 */
        return chatMessageDao.selectByConversationId(conversationId);
    }

    /**
     * 确保会话存在
     */
    private void ensureConversation(String conversationId, String userId) {
        if (chatConversationDao.selectByIdAndUserId(conversationId, userId) == null) {
            ChatConversation chatConversation = new ChatConversation();
            chatConversation.setConversationId(conversationId);
            chatConversation.setUserId(userId);
            chatConversation.setTitle(DEFAULT_TITLE);
            chatConversation.setDeleted(0);
            chatConversationDao.insert(chatConversation);
        }
    }

    private void updateConversationTittle(String conversationId, String userId, String content) {
        // 一旦定型就不再变
        ChatConversation chatConversation = chatConversationDao.selectByIdAndUserId(conversationId, userId);
        if (DEFAULT_TITLE.equals(chatConversation.getTitle())) {
            chatConversationDao.updateTitle(conversationId, userId, buildTitle(content));
        }
    }

    /**
     * 用户消息落库
     */
    private void insertMessage(String conversationId, String content) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setConversationId(conversationId);
        chatMessage.setRole(MessageRoleEnum.USER.getCode());
        chatMessage.setContent(content);
        chatMessage.setDeleted(0);
        chatMessageDao.insert(chatMessage);
        chatConversationDao.touch(conversationId);
    }



    /**
     * 标题处理
     */
    private String buildTitle(String content) {
        if (content.codePointCount(0, content.length()) <= TITLE_MAX_CHARS) {
            return content;
        }
        // 按码点截断
        return content.substring(0, content.offsetByCodePoints(0, TITLE_MAX_CHARS));
    }
}
