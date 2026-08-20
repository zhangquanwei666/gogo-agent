package com.quanwei.gogo.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quanwei.gogo.agent.entity.ChatConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * chat_conversation 表 mapper，约定见 UserAccountMapper。
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {
}
