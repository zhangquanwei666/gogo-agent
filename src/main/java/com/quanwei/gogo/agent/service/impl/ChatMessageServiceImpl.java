package com.quanwei.gogo.agent.service.impl;

import com.quanwei.gogo.agent.bo.ChatMessageCreateBO;
import com.quanwei.gogo.agent.common.ErrorCodeEnum;
import com.quanwei.gogo.agent.common.FeedbackEnum;
import com.quanwei.gogo.agent.dao.ChatConversationDao;
import com.quanwei.gogo.agent.dao.ChatMessageDao;
import com.quanwei.gogo.agent.entity.ChatMessage;
import com.quanwei.gogo.agent.exception.BizException;
import com.quanwei.gogo.agent.service.ChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatMessageServiceImpl implements ChatMessageService {

    @Autowired
    private ChatMessageDao chatMessageDao;

    @Autowired
    private ChatConversationDao chatConversationDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String addMessage(ChatMessageCreateBO chatMessageCreateBO) {
        ChatMessage message = buildMessage(chatMessageCreateBO);
        chatMessageDao.insert(message);

        // 有新消息就把会话顶到列表最前
        chatConversationDao.touch(message.getConversationId());
        return message.getMessageId();
    }

    @Override
    public List<ChatMessage> listByConversationId(String conversationId) {
        requireConversationId(conversationId);
        return chatMessageDao.selectByConversationId(conversationId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean feedback(String messageId, FeedbackEnum feedback) {
        if (!StringUtils.hasText(messageId)) {
            throw new BizException(ErrorCodeEnum.MESSAGE_ID_EMPTY);
        }
        if (chatMessageDao.selectByMessageId(messageId) == null) {
            throw new BizException(ErrorCodeEnum.MESSAGE_NOT_FOUND);
        }

        // feedback 为 null 表示撤销，反馈时间跟着一起清掉
        String code = feedback == null ? null : feedback.getCode();
        LocalDateTime time = feedback == null ? null : LocalDateTime.now();
        return chatMessageDao.updateFeedback(messageId, code, time) > 0;
    }

    /** 校验入参并组装实体，messageId 和 createdTime 交给 MP 处理 */
    private ChatMessage buildMessage(ChatMessageCreateBO bo) {
        if (bo == null) {
            throw new BizException(ErrorCodeEnum.PARAM_INVALID);
        }
        requireConversationId(bo.conversationId());
        if (bo.role() == null) {
            throw new BizException(ErrorCodeEnum.MESSAGE_ROLE_INVALID);
        }
        if (!StringUtils.hasText(bo.content())) {
            throw new BizException(ErrorCodeEnum.MESSAGE_CONTENT_EMPTY);
        }

        ChatMessage message = new ChatMessage();
        message.setConversationId(bo.conversationId());
        message.setRole(bo.role().getCode());
        message.setContent(bo.content());
        message.setAgentName(bo.agentName());
        message.setExtra(bo.extra());
        message.setDeleted(0);
        // messageId 由 IdType.ASSIGN_ID 雪花生成，createdTime 由 MyMetaObjectHandler 填充
        return message;
    }

    private void requireConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            throw new BizException(ErrorCodeEnum.CONVERSATION_ID_EMPTY);
        }
    }
}
