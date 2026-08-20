package com.quanwei.gogo.agent.dto;

import com.quanwei.gogo.agent.common.BaseResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * 用户会话列表的返回值
 */
@Getter
@Setter
@ToString(callSuper = true)
public class ChatConversationListRespDTO extends BaseResponse {

    /** 会话总数 */
    private Integer total;

    /** 会话列表，按最后更新时间倒序 */
    private List<ChatConversationDTO> conversations;
}
