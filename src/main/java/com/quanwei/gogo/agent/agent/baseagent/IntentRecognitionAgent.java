package com.quanwei.gogo.agent.agent.baseagent;

import com.alibaba.fastjson2.JSON;
import com.quanwei.gogo.agent.agent.enums.AgentNameEnum;
import com.quanwei.gogo.agent.agent.intent.IntentRecognitionResult;
import com.quanwei.gogo.agent.agent.intent.IntentRecognitionRouter;
import com.quanwei.gogo.agent.agent.prompt.PromptLoader;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.interruption.InterruptContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 意图识别智能体。
 *
 * <p>识别分四级，L0 到 L2 由 {@link IntentRecognitionRouter} 承担（不调模型的快路径），
 * 快路径没给出可信结论时本类再调 L3 大模型兜底。
 *
 * <p>这么切分是因为 L3 需要模型实例和提示词 —— 那是智能体自己的资源，
 * 放进路由器会让路由器被迫依赖模型，也就没法脱离 Spring 上下文单测了。
 *
 * <p>逐级下沉的意义不是准确率（单看准确率 L3 最高），而是让高频的简单问题
 * 不用为长尾的复杂问题买单：「我的报销进度」这种话没必要花 800ms 和一次模型调用。
 */
@Slf4j
@Component("intentRecognitionAgent")
@Scope("prototype")
public class IntentRecognitionAgent extends AgentBase {

    private static final String SYSTEM_PROMPT_NAME = "intent-recognition-agent-system.md";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final Model stableModel;

    private final IntentRecognitionRouter router;

    /** 系统提示词，构造时读一次 */
    private final String sysPrompt;

    /**
     * @param stableModel 意图分类是典型的简单任务
     */
    public IntentRecognitionAgent(@Qualifier("stableModel") Model stableModel,
                                  PromptLoader promptLoader,
                                  IntentRecognitionRouter router) {
        super(AgentNameEnum.INTENT_RECOGNITION.getCode(), AgentNameEnum.INTENT_RECOGNITION.getDesc());
        this.stableModel = stableModel;
        this.router = router;
        // loadStatic 而不是 load：存进 final 字段长期持有，实时时分秒会冻在构造那一刻；
        // 日级别稳定的前缀也才吃得到百炼的隐式缓存
        this.sysPrompt = promptLoader.loadStatic(SYSTEM_PROMPT_NAME);
    }

    /**
     * 识别主流程。入参取最后一条用户消息的文本
     */
    @Override
    protected Mono<Msg> doCall(List<Msg> msgs) {
        String question = extractLastUserText(msgs);

        if (question != null && !question.isBlank()) {
            Optional<IntentRecognitionResult> hit = router.route(question);
            if (hit.isPresent()) {
                IntentRecognitionResult result = hit.get();
                log.debug("[INTENT_RECOGNITION] 命中快路径，source={} intent={} cost_layer_skip=true", result.getSource().getCode(), result.getPrimaryIntent());
                Msg response = Msg.builder()
                        .name(AgentNameEnum.INTENT_RECOGNITION.getCode())
                        .role(MsgRole.ASSISTANT)
                        // 走 toJsonMap 而不是直接序列化对象：前者产出的是提示词约定的 snake_case schema，
                        // 直接序列化会得到 primaryIntent/overallReason 这类 camelCase 字段，下游就得认两套
                        .content(TextBlock.builder().text(JSON.toJSONString(result.toJsonMap())).build())
                        .build();
                return Mono.just(response);
            }
        }

        // L1,L2未命中，走L3兜底
        return callL3(msgs);
    }

    /** 被中断时的处理 */
    @Override
    protected Mono<Msg> handleInterrupt(InterruptContext interruptContext, Msg... msgs) {
        return Mono.just(Msg.builder()
                .name(AgentNameEnum.INTENT_RECOGNITION.getCode())
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text("终止意图识别").build())
                .generateReason(GenerateReason.INTERRUPTED)
                .build());
    }

    /**
     * L3：调模型做复杂意图和多意图识别。
     *
     * <p>公开出来是给 {@code AgentPipelineService} 用的：流水线自己按
     * 「原始问题走快路径 → 未命中才改写 → 改写后再走一次快路径 → 还不行才 L3」的顺序驱动，
     * 需要能单独触发 L3 而不重复跑一遍前面几级（重复跑 L2 会白花一次 embedding 调用）。
     *
     * <p>{@link #doCall(List)} 仍然是「快路径 + L3」的完整流程，单独用这个智能体时走那条。
     */
    public Mono<Msg> callL3(List<Msg> msgs) {
        ArrayList<Msg> messages = new ArrayList<>();
        messages.add(Msg.builder()
                .role(MsgRole.SYSTEM)
                .name("system")
                .content(TextBlock.builder().text(sysPrompt).build())
                .build());
        if (msgs != null) {
            // 这里曾经写成 messages.addAll(messages) —— 自己加自己，
            // 结果是系统提示词进去两遍、用户问题一条没进，模型在没有问题的情况下硬编一个意图
            messages.addAll(msgs);
        }

        return Mono.fromCallable(() -> stableModel.stream(messages, null, null).collectList().block())
                .map(responses -> Msg.builder()
                        .name(AgentNameEnum.INTENT_RECOGNITION.getCode())
                        .role(MsgRole.ASSISTANT)
                        .content(TextBlock.builder().text(extractText(responses)).build())
                        .build()
                )
                .timeout(TIMEOUT);
    }

    /** 取最后一条 user 消息的文本；没有 user 消息就退而取最后一条 */
    private static String extractLastUserText(List<Msg> msgs) {
        if (msgs == null || msgs.isEmpty()) {
            return "";
        }
        for (int i = msgs.size() - 1; i >= 0; i--) {
            Msg msg = msgs.get(i);
            if (msg.getRole() == MsgRole.USER) {
                return textOf(msg);
            }
        }
        return textOf(msgs.get(msgs.size() - 1));
    }

    private static String textOf(Msg msg) {
        List<ContentBlock> blocks = msg.getContent();
        if (blocks == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (block instanceof TextBlock textBlock && textBlock.getText() != null) {
                sb.append(textBlock.getText());
            }
        }
        return sb.toString().trim();
    }

    private static String extractText(List<ChatResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatResponse response : responses) {
            List<ContentBlock> blocks = response.getContent();
            if (blocks == null) {
                continue;
            }
            for (ContentBlock block : blocks) {
                if (block instanceof TextBlock textBlock) {
                    sb.append(textBlock.getText());
                }
            }
        }
        return sb.toString();
    }
}
