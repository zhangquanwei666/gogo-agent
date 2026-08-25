package com.quanwei.gogo.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSON;
import com.quanwei.gogo.agent.agent.core.AgentContext;
import com.quanwei.gogo.agent.agent.enums.AgentNameEnum;
import com.quanwei.gogo.agent.agent.pipeline.AgentPipelineService;
import com.quanwei.gogo.agent.agent.pipeline.PipelineResult;
import com.quanwei.gogo.agent.bo.ChatConversationBO;
import com.quanwei.gogo.agent.bo.ChatMessageCreateBO;
import com.quanwei.gogo.agent.common.ErrorCodeEnum;
import com.quanwei.gogo.agent.common.MessageRoleEnum;
import com.quanwei.gogo.agent.dto.ChatConversationDTO;
import com.quanwei.gogo.agent.dto.ChatConversationListReqDTO;
import com.quanwei.gogo.agent.dto.ChatConversationListRespDTO;
import com.quanwei.gogo.agent.dto.ChatRequest;
import com.quanwei.gogo.agent.dto.ChatResponse;
import com.quanwei.gogo.agent.exception.BizException;
import com.quanwei.gogo.agent.service.ChatConversationService;
import com.quanwei.gogo.agent.service.ChatHistoryService;
import com.quanwei.gogo.agent.service.ChatMessageService;
import io.agentscope.core.message.Msg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 对话相关接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    /**
     * 等一轮回复的上限。
     *
     * <p>比智能体自己的超时（改写 8s、意图识别 10s）宽得多是有意的：这里是兜底，
     * 正常情况轮不到它触发。给到 90s 是因为主智能体是 ReAct 循环，最多十轮，
     * 每轮都可能有一次模型调用。真到点了说明链路某处卡死，返回错误比让前端一直转圈好。
     */
    private static final Duration REPLY_TIMEOUT = Duration.ofSeconds(90);

    /** 智能体没给出回复时的兜底文案。宁可说句人话，也不要给前端一个空串 */
    private static final String FALLBACK_REPLY = "抱歉，我这边暂时没能处理好这个问题，麻烦您换个说法再试一次。";

    @Autowired
    private ChatConversationService chatConversationService;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private AgentPipelineService agentPipelineService;

    /**
     * 发送一条消息，走完整条智能体链路后返回回复。
     *
     * <p>六步：取身份 → 校验 → 用户消息落库 → 跑流水线（改写 + 意图识别 + 主智能体）
     * → 回复落库 → 返回。
     *
     * <p><b>userId 一律取自 token</b>，请求体里没有这个字段 —— 否则拿到别人的 userId
     * 就能往别人的会话里写消息。
     *
     * <p><b>会话不存在会自动创建</b>（{@code saveUserMessage} 里做的），
     * 所以前端不需要先调一次「创建会话」接口，首次发送直接带一个新的 sessionId 即可。
     *
     * <p><b>用户消息先落库再跑智能体</b>，顺序不能反：智能体这一步耗时以秒计，
     * 中途失败、超时、用户刷新页面都可能发生，先落库至少保证「用户说过的话」不丢，
     * 下次进来还能看到自己问了什么。
     */
    @PostMapping("/send")
    public ChatResponse send(@RequestBody ChatRequest request) {

        /* 1. 身份取自 token */
        String userId = StpUtil.getLoginIdAsString();

        String sessionId = trimToNull(request.getSessionId());
        String content = trimToNull(request.getContent());
        if (sessionId == null) {
            throw new BizException(ErrorCodeEnum.CONVERSATION_ID_EMPTY);
        }
        if (content == null) {
            throw new BizException(ErrorCodeEnum.MESSAGE_CONTENT_EMPTY);
        }

        /* 3. 用户消息落库，会话不存在时顺带建会话、定标题。
              落库失败只记 warn 不中断：这一轮的回复比这条历史记录重要，
              为了存不下一句话就让用户拿不到答复不值得 */
        try {
            chatHistoryService.saveUserMessage(sessionId, userId, content);
        } catch (Exception e) {
            log.warn("[CHAT] 保存用户消息失败 sessionId={}: {}", sessionId, e.getMessage());
        }

        /* 4. 组装本轮上下文。Msg 由流水线按各智能体的入参形态拼，这里不碰框架的消息结构 */
        AgentContext context = new AgentContext();
        context.setUserId(userId);
        context.setConversationId(sessionId);
        context.setTraceId(resolveTraceId(request));
        context.setRawQuery(content);

        /* 5. 跑智能体。block 在这里是对的：MVC 本来就是一请求一线程，
              而且回复必须拿到手才能落库和返回 */
        PipelineResult result = runPipeline(context);

        /* 6. 回复落库 + 返回 */
        String reply = extractReply(result);
        String agentName = AgentNameEnum.MASTER.getCode();
        String messageId = chatMessageService.addMessage(new ChatMessageCreateBO(
                sessionId,
                MessageRoleEnum.AGENT,
                reply,
                agentName,
                // 意图结果塞进 extra：排查「为什么答成这样」时，第一眼要看的就是它判成了什么意图
                JSON.toJSONString(result.intent().toJsonMap())));

        log.info("[CHAT] 会话 {} 完成一轮，intent={} source={} 改写={} 模型调用={} 耗时={}ms",
                sessionId, result.intent().getPrimaryIntent(), result.intent().getSource().getCode(),
                result.rewriteTriggered(), result.llmCalls(), result.costMs());

        ChatResponse response = new ChatResponse();
        response.setSessionId(sessionId);
        response.setMessageId(messageId);
        response.setReply(reply);
        response.setAgentName(agentName);
        response.setIntent(result.intent().getPrimaryIntent());
        response.setIntentSource(result.intent().getSource().getCode());
        response.setInterrupted(result.interrupted());
        return response;
    }

    /**
     * 跑流水线并等结果。
     *
     * <p>流水线内部已经把各环节的异常都吞掉了，这里再兜一层是为了两种它兜不住的情况：
     * 整体超时，以及流水线自身抛出的意外异常。两者都转成 500，让全局异常处理去回。
     */
    private PipelineResult runPipeline(AgentContext context) {
        try {
            PipelineResult result = agentPipelineService.run(context).block(REPLY_TIMEOUT);
            if (result == null) {
                throw new BizException(ErrorCodeEnum.SYSTEM_ERROR, "智能体没有返回结果");
            }
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[CHAT] 会话 {} 执行失败：{}", context.getConversationId(), e.getMessage(), e);
            throw new BizException(ErrorCodeEnum.SYSTEM_ERROR, "智能体执行失败：" + e.getMessage());
        }
    }

    /**
     * 取回复正文。
     *
     * <p>主智能体没接上或调用失败时 {@code masterReply} 为空，这时用兜底文案而不是报错 ——
     * 用户消息已经落库了，会话是完整的，只是这一轮没答上来。
     */
    private String extractReply(PipelineResult result) {
        Msg masterReply = result.masterReply();
        String text = masterReply == null ? null : masterReply.getTextContent();
        if (text == null || text.isBlank()) {
            log.warn("[CHAT] 主智能体没有产出回复，返回兜底文案。intent={}",
                    result.intent().getPrimaryIntent());
            return FALLBACK_REPLY;
        }
        return text.trim();
    }

    /** 前端没带 traceId 就本地生成一个，保证这一轮的日志能串起来 */
    private String resolveTraceId(ChatRequest request) {
        String traceId = trimToNull(request.getTraceId());
        return traceId != null ? traceId : UUID.randomUUID().toString().replace("-", "");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

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
