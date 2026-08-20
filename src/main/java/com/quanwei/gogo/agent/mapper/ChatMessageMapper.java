package com.quanwei.gogo.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quanwei.gogo.agent.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * chat_message 表 mapper，约定见 UserAccountMapper。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
