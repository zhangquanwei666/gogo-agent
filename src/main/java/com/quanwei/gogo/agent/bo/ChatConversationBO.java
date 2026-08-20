package com.quanwei.gogo.agent.bo;

import java.time.LocalDateTime;

/**
 * 会话信息业务对象。
 * service 对外返回它而不是 ChatConversation 实体，让实体不越过 service 边界，
 * deleted 这类纯持久化字段也不会漏到上层。
 *
 * @param conversationId 会话ID
 * @param userId         归属用户
 * @param title          会话标题
 * @param createdTime    创建时间
 * @param updatedTime    最后更新时间
 */
public record ChatConversationBO(String conversationId,
                                 String userId,
                                 String title,
                                 LocalDateTime createdTime,
                                 LocalDateTime updatedTime) {
}
