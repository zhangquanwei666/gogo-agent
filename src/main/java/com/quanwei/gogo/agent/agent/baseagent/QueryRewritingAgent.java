package com.quanwei.gogo.agent.agent.baseagent;

import com.quanwei.gogo.agent.agent.enums.AgentNameEnum;
import com.quanwei.gogo.agent.agent.prompt.PromptLoader;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.interruption.InterruptContext;
import io.agentscope.core.message.*;
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

/**
 * 问题改写智能体。
 *
 * <p>职责单一：把带指代、省略的追问补全成一句可以脱离上下文独立理解的问题。
 * 「那家酒店多少钱」→「上海浦东丽思卡尔顿酒店多少钱」。
 * 另外还负责错别字修正和 step-back 改写，具体规则在提示词里。
 */
@Slf4j
@Component("queryRewritingAgent")
@Scope("prototype")
public class QueryRewritingAgent extends AgentBase {

    /** 提示词文件名，对应 resources/prompt/ 下的文件，要带扩展名 */
    private static final String SYSTEM_PROMPT_NAME = "query-rewriting-agent-system.md";

    private final Model stableModel;

    /** 系统提示词。构造时读一次，之后不再变 */
    private final String sysPrompt;

    /**
     * @param stableModel  必须带 @Qualifier —— 容器里有四个 Model bean，其中 strongModel 标了
     *                     @Primary，而 Spring 解析多候选时 @Primary 的优先级高于按参数名匹配，
     *                     不写限定符会静默拿到 strongModel，不报错但用错模型
     * @param promptLoader 提示词加载器，是 Spring bean，不能静态调用
     */
    public QueryRewritingAgent(@Qualifier("stableModel") Model stableModel, PromptLoader promptLoader) {
        super(AgentNameEnum.QUERY_REWRITE.getCode(), AgentNameEnum.QUERY_REWRITE.getDesc());
        this.stableModel = stableModel;
        // 用 loadStatic 而不是 load：sysPrompt 存进 final 字段长期持有，
        // load 注入的实时时分秒会冻在构造那一刻反而不准；而且日级别稳定的前缀才吃得到百炼隐式缓存
        this.sysPrompt = promptLoader.loadStatic(SYSTEM_PROMPT_NAME);
    }

    /**
     * 改写主流程
     */
    @Override
    protected Mono<Msg> doCall(List<Msg> msgs) {

        ArrayList<Msg> messages = new ArrayList<>();
        messages.add(Msg.builder()
                .role(MsgRole.SYSTEM)
                .name("system")
                .content(TextBlock.builder().text(sysPrompt).build())
                .build()
        );

        //会话消息
        if (msgs != null) {
            messages.addAll(msgs);
        }

        return Mono.fromCallable(() -> stableModel.stream(messages, null, null).collectList().block())
                .map(chatResponses -> Msg.builder()
                        .name(AgentNameEnum.QUERY_REWRITE.getCode())
                        .role(MsgRole.ASSISTANT)
                        .content(TextBlock.builder().text(extractText(chatResponses)).build())
                        .build()
                )
                .timeout(Duration.ofSeconds(8));
    }

    /**
     * 被中断时的处理。
     */
    @Override
    protected Mono<Msg> handleInterrupt(InterruptContext interruptContext, Msg... msgs) {
        return Mono.just(Msg.builder()
                .name(AgentNameEnum.QUERY_REWRITE.getCode())
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text("终止问题改写").build())
                .generateReason(GenerateReason.INTERRUPTED)
                .build()
        );
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
