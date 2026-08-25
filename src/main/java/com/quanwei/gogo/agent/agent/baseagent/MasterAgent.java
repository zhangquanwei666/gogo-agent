package com.quanwei.gogo.agent.agent.baseagent;

import com.quanwei.gogo.agent.agent.core.AgentContext;
import com.quanwei.gogo.agent.agent.core.MasterAgentContextHolder;
import com.quanwei.gogo.agent.agent.enums.AgentNameEnum;
import com.quanwei.gogo.agent.agent.prompt.PromptLoader;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.util.StringUtils;

/**
 * 主智能体：流水线的终点，负责根据改写结果和意图识别结果调度子智能体，并把结果整合给用户。
 *
 * <p><b>本类是工厂不是智能体。</b>真正的智能体是 {@link ReActAgent} —— 框架的现成实现，
 * 自带「思考 → 调工具 → 看结果 → 再思考」的循环。另两个智能体（改写、意图识别）
 * 继承 {@code AgentBase} 自己写 {@code doCall}，是因为它们只需要一次模型调用、不调工具；
 * 主智能体要调工具，那套循环没必要重写一遍。
 *
 * <p>入参由 {@code AgentPipelineService.buildMasterInput} 组装，形态已经定死：
 * <pre>
 * SYSTEM  问题改写结果：…
 * SYSTEM  意图识别结果：{ intents[] / primary_intent / multi_intent / overall_reason }
 * USER    用户的原始输入
 * </pre>
 * 意图那条给的是 {@code toJsonMap()} 的产出，本类不需要知道它是规则、向量还是模型给的。
 *
 * <p><b>当前 toolkit 是空的</b>，所以 {@code master-agent-system.md} 里那些
 * 「调用 {@code itinerary_manage_agent}」「调用 {@code ask_user}」的指令还落不了地 ——
 * 子智能体和交互工具都没实现。现阶段只有 greeting / unknown 两类意图答得正常，
 * 其余意图模型会用自然语言描述它「打算调用」什么。工具补齐的位置见
 * {@link #buildToolkit()}。
 */
@Slf4j
@Configuration("masterAgentConfiguration")
public class MasterAgent {

    /** 提示词文件名，对应 resources/prompt/ 下的文件，要带扩展名 */
    private static final String SYSTEM_PROMPT_NAME = "master-agent-system.md";

    /**
     * ReAct 循环的最大轮次。
     * 一轮 = 一次模型调用 + 一次工具执行。给 10 是留给「路由子智能体 → 拿结果 → 再路由」
     * 这类多步编排的余量；到顶了框架会停下来把当前结果返回，不会无限转下去。
     */
    private static final int MAX_ITERATIONS = 10;

    /** 主 Agent 工具执行超时（分钟） */
    private static final int TOOL_TIMEOUT_MINUTES = 15;

    /**
     * 强模型。
     *
     * <p>{@code @Qualifier} 不能省：容器里有 fast / strong / strongWithThinking / stable
     * 四个 Model 候选，其中 strongModel 标了 {@code @Primary}。这里正好要的就是它，
     * 省掉限定符当前也能跑对 —— 但那是「碰巧对」，哪天 @Primary 挪到别的档位上，
     * 这里会静默改用另一个模型且不报任何错。写死限定符，让它对得有据可依。
     */
    @Autowired
    @Qualifier("strongModel")
    private Model strongModel;

    @Autowired
    @Qualifier("stableModel")
    private Model stableModel;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private PromptLoader promptLoader;

    /**
     * 造一个主智能体。
     *
     * <p>{@code prototype} 是硬要求，不是优化：ReActAgent 内部有中断标志、迭代计数这类可变状态，
     * 单例会让并发请求互相干扰 —— A 请求点了中断，B 请求跟着一起停。
     *
     * <p>上下文从 ThreadLocal 取，由 {@code AgentPipelineService} 在调 {@code getBean} 之前
     * 投影到当前线程。为什么要绕这一道、以及那三条使用约束，见
     * {@link MasterAgentContextHolder} 的类注释。
     *
     * <p>取不到上下文时降级成空对象而不是抛异常：{@code IntentCategory.GREETING} 和
     * {@code UNKNOWN} 都把 targetAgent 绑成了 masterAgent，将来按意图直接路由过来时
     * 未必会经过流水线那段投影代码。少了上下文只是没有会话标识，用户的问题本身仍在入参消息里，
     * 答得出来 —— 为这个把整轮对话搞挂不值得。但要打 WARN：走流水线时它一定有值，
     * 没值就说明投影那段漏了，得能看见。
     */
    @Bean(name = "masterAgent")
    @Scope("prototype")
    public ReActAgent build() {
        AgentContext context = MasterAgentContextHolder.get();
        if (context == null) {
            log.warn("[MASTER] 当前线程没有 AgentContext 投影，以空上下文构造。"
                    + "若调用来自 AgentPipelineService，说明 set/clear 那段漏了");
            context = new AgentContext();
        }

        // loadStatic 而不是 load：只注入日期和星期（一天变一次），不注入实时时分秒。
        // 日级别稳定的前缀才吃得到百炼的隐式缓存，而这段系统提示词每轮都要带，
        // 命中缓存省下的是实打实的钱。
        // 注：当前 master-agent-system.md 里没有 {{current_date}} 占位符，所以这一步暂时不注入任何东西
        String sysPrompt = promptLoader.loadStatic(SYSTEM_PROMPT_NAME);

        ReActAgent agent  = ReActAgent.builder()
                .name(AgentNameEnum.MASTER.getCode())
                .description(AgentNameEnum.MASTER.getDesc())
                .sysPrompt(sysPrompt)
                .model(strongModel)
                .toolkit(buildToolkit())
                .maxIters(MAX_ITERATIONS)
                .build();

        log.info("[MASTER] 构造主智能体，traceId={} conversationId={} 工具数={}",
                context.getTraceId(), context.getConversationId(), 0);

        return agent;
    }

    /**
     * 工具集。
     *
     * <p><b>当前是空的</b>，提示词里写的那些工具都还没实现。补齐的顺序建议：
     * <ol>
     *   <li>{@code ask_user} —— 主智能体自己处理 greeting / unknown 时就要用，不依赖任何子智能体；</li>
     *   <li>子智能体路由工具 —— 注意提示词里用的是 {@code itinerary_manage_agent} 这种下划线名，
     *       而 {@code IntentCategory.targetAgent} 里存的是 {@code itineraryManageAgent} 驼峰 bean 名，
     *       两边得有个映射，直接拿提示词里的名字去 getBean 会抛；</li>
     *   <li>长期记忆的 {@code record_to_memory} / {@code retrieve_from_memory} —— 这两个不用自己写，
     *       给 builder 配上 {@code longTermMemory} 和
     *       {@code longTermMemoryMode(LongTermMemoryMode.AGENT_CONTROL)} 框架会自动注册。
     *       但当前依赖里只有 {@code Mem0LongTermMemory}，提示词写的百炼记忆没有现成实现。</li>
     * </ol>
     */
    private Toolkit buildToolkit() {
        return new Toolkit();
    }
}
