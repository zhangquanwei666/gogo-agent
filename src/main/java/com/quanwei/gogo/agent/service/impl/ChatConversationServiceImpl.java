package com.quanwei.gogo.agent.service.impl;

import com.quanwei.gogo.agent.bo.ChatConversationBO;
import com.quanwei.gogo.agent.common.ErrorCodeEnum;
import com.quanwei.gogo.agent.dao.ChatConversationDao;
import com.quanwei.gogo.agent.dao.ChatMessageDao;
import com.quanwei.gogo.agent.entity.ChatConversation;
import com.quanwei.gogo.agent.exception.BizException;
import com.quanwei.gogo.agent.service.ChatConversationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ChatConversationServiceImpl implements ChatConversationService {

    /** 默认标题，跟 DDL 里的 DEFAULT '新对话' 保持一致 */
    private static final String DEFAULT_TITLE = "新对话";

    @Autowired
    private ChatConversationDao chatConversationDao;

    @Autowired
    private ChatMessageDao chatMessageDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createConversation(String userId, String title) {
        if (!StringUtils.hasText(userId)) {
            throw new BizException(ErrorCodeEnum.USER_ID_EMPTY);
        }

        ChatConversation conversation = new ChatConversation();
        conversation.setUserId(userId);
        conversation.setTitle(StringUtils.hasText(title) ? title : DEFAULT_TITLE);
        conversation.setDeleted(0);
        // conversationId 由 IdType.ASSIGN_ID 雪花生成
        // createdTime / updatedTime 由 MyMetaObjectHandler 填充

        chatConversationDao.insert(conversation);
        return conversation.getConversationId();
    }

    @Override
    public List<ChatConversationBO> listByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BizException(ErrorCodeEnum.USER_ID_EMPTY);
        }
        // 转成 BO 再出去，实体不越过 service 边界
        return chatConversationDao.selectByUserId(userId).stream()
                .map(c -> new ChatConversationBO(c.getConversationId(),
                        c.getUserId(),
                        c.getTitle(),
                        c.getCreatedTime(),
                        c.getUpdatedTime()))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean renameTitle(String conversationId, String userId, String title) {
        if (!StringUtils.hasText(title)) {
            throw new BizException(ErrorCodeEnum.PARAM_INVALID, "标题不能为空");
        }
        // 先做归属校验，查不到直接抛 404
        requireOwned(conversationId, userId);
        return chatConversationDao.updateTitle(conversationId, userId, title) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeConversation(String conversationId, String userId) {
        // 先做归属校验，避免删掉别人的会话
        requireOwned(conversationId, userId);

        // 消息和会话一起逻辑删除，两步在同一个事务里
        chatMessageDao.deleteByConversationId(conversationId);
        return chatConversationDao.deleteByIdAndUserId(conversationId, userId) > 0;
    }

    /**
     * 归属校验：会话必须存在且属于该用户，否则抛异常。
     * 原来这段逻辑是对外的 getByIdAndUserId，接口上撤掉后收成私有方法，
     * renameTitle / removeConversation 仍然要靠它防越权。
     * 会话不存在和不属于该用户抛同一个错误码，避免被拿来探测别人的会话 ID 是否有效。
     */
    private void requireOwned(String conversationId, String userId) {
        if (!StringUtils.hasText(conversationId)) {
            throw new BizException(ErrorCodeEnum.CONVERSATION_ID_EMPTY);
        }
        if (!StringUtils.hasText(userId)) {
            throw new BizException(ErrorCodeEnum.USER_ID_EMPTY);
        }
        if (chatConversationDao.selectByIdAndUserId(conversationId, userId) == null) {
            throw new BizException(ErrorCodeEnum.CONVERSATION_NOT_FOUND);
        }
    }
}
