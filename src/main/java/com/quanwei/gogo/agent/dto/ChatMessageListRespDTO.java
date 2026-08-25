package com.quanwei.gogo.agent.dto;

import com.quanwei.gogo.agent.common.BaseResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * 某个会话下全部消息的返回值。
 */
@Getter
@Setter
@ToString(callSuper = true)
public class ChatMessageListRespDTO extends BaseResponse {

    /** 回显会话 ID。响应体自带主语，前端不用回头对照自己发的是哪条，排障看日志时也省一次翻找 */
    private String conversationId;

    /** 消息总数 */
    private Integer total;

    /** 消息列表，按时间正序，也就是对话本身的顺序 */
    private List<ChatMessageDTO> messages;
}
