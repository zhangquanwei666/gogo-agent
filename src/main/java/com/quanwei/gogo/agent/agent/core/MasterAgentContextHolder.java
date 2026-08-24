package com.quanwei.gogo.agent.agent.core;

/**
 * 把 {@link AgentContext} 投影到当前线程，供 {@code masterAgent} 的 {@code @Bean} 工厂方法读取。
 *
 * <p><b>为什么需要它：</b>MasterAgent 是 {@code @Scope("prototype")} 的，
 * 构造参数里既有容器管理的 Model / PromptLoader，也有<b>随请求变化的 AgentContext</b>。
 * 后者不是 bean，没法靠依赖注入给进去，而 {@code getBean(name)} 又不支持传业务参数
 * （{@code getBean(name, args)} 只对构造器参数全匹配的场景可用，混着注入 bean 就会失配）。
 * 于是退一步：调用方先把上下文放到当前线程，工厂方法取出来传进构造器。
 *
 * <p><b>三条使用约束，破一条就出错：</b>
 * <ol>
 *   <li><b>set → getBean → clear 必须在同一个线程上同步完成。</b>
 *       流水线跑在 Reactor 上，{@code flatMap} 里的代码在哪个线程执行是不确定的，
 *       但只要这三步之间没有异步边界，它们就一定在同一根线程上；</li>
 *   <li><b>clear 必须放 finally。</b>{@code boundedElastic} 的线程是池化复用的，
 *       漏掉一次清理，这个上下文就会被下一个请求捡到 —— 表现是「A 用户的历史串进了 B 用户的对话」，
 *       而且只在池子复用到那根线程时偶发，极难查；</li>
 *   <li><b>智能体必须在构造时把上下文快照进 final 字段，不能在 doCall 里读这个 Holder。</b>
 *       doCall 返回的 Mono 由 Reactor 调度，执行时大概率已经不在构造它的那根线程上了，
 *       那时候再读只会读到 null。</li>
 * </ol>
 *
 * <p>只给 MasterAgent 用。其余智能体的构造参数全是 bean，走正常注入，不要往这里加东西 ——
 * 隐式的线程传参多一处就多一个上面那类坑。
 */
public final class MasterAgentContextHolder {

    private static final ThreadLocal<AgentContext> HOLDER = new ThreadLocal<>();

    private MasterAgentContextHolder() {
    }

    /** 投影到当前线程。调用方有义务在 finally 里 {@link #clear()} */
    public static void set(AgentContext context) {
        HOLDER.set(context);
    }

    /** 取当前线程上的上下文，没投影过返回 null */
    public static AgentContext get() {
        return HOLDER.get();
    }

    /**
     * 清理。
     * 用 remove() 而不是 set(null)：后者会把一个 null 值继续挂在线程的 ThreadLocalMap 上，
     * 线程池场景下等于留了个不会被回收的空槽位。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
