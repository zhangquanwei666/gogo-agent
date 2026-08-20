package com.quanwei.gogo.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.quanwei.gogo.agent.bo.ChatConversationBO;
import com.quanwei.gogo.agent.dto.ChatConversationDTO;
import com.quanwei.gogo.agent.dto.ChatConversationListReqDTO;
import com.quanwei.gogo.agent.dto.ChatConversationListRespDTO;
import com.quanwei.gogo.agent.service.ChatConversationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对话相关接口
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private ChatConversationService chatConversationService;

    /**
     * 查询当前登录用户的全部会话记录，按最后更新时间倒序。
     *
     * <p>userId 取自 token，不接受外部传入 —— 否则任何人拿到别人的 userId 就能读到别人的会话。
     * 请求体可以不传，留着是为了 traceId 和后续的筛选条件。
     */
    @PostMapping("/conversation/list")
    public ChatConversationListRespDTO listConversation(
            @RequestBody(required = false) ChatConversationListReqDTO request) {

        String userId = StpUtil.getLoginIdAsString();

        List<ChatConversationBO> conversationList = chatConversationService.listByUserId(userId);

        List<ChatConversationDTO> items = conversationList.stream()
                .map(ChatController::toDTO)
                .toList();

        ChatConversationListRespDTO response = new ChatConversationListRespDTO();
        response.setTotal(items.size());
        response.setConversations(items);
        // code / msg 走 BaseResponse 的默认值 200 / success
        return response;
    }

    /** BO 转 DTO，userId 不回显 —— 就是当前登录用户自己 */
    private static ChatConversationDTO toDTO(ChatConversationBO bo) {
        ChatConversationDTO dto = new ChatConversationDTO();
        dto.setConversationId(bo.conversationId());
        dto.setTitle(bo.title());
        dto.setCreatedTime(bo.createdTime());
        dto.setUpdatedTime(bo.updatedTime());
        return dto;
    }
}
