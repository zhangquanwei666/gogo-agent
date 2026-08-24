package com.quanwei.gogo.agent.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 智能体注册表：按 bean 名从容器里取智能体。
 *
 * <p>为什么不直接注入智能体：
 * <ol>
 *   <li>智能体都是 {@code @Scope("prototype")}。直接注入拿到的是同一个实例，
 *       而 AgentBase 内部有中断标志这类可变状态，并发请求共用会互相干扰 ——
 *       每次调用都必须从容器里取新的；</li>
 *   <li><b>路由天然是按名字的</b>。{@link com.quanwei.gogo.agent.agent.enums.IntentCategory}
 *       里每个意图绑定的 {@code targetAgent} 就是 bean 名，识别完直接拿它来取子智能体。
 *       编译期不可能知道会取到哪个，只能按名字查。</li>
 * </ol>
 *
 * <p>取不到就抛 —— bean 名写错属于配置错误，早失败比在运行时静默降级好查得多。
 * 需要「取不到就降级」的调用方先用 {@link #contains(String)} 判一下。
 */
@Slf4j
@Component
public class AgentRegistry {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 按 bean 名取智能体，每次都是新实例（prototype 作用域）。
     *
     * @throws BeansException bean 不存在或类型不符
     */
    public <T> T getAgent(String beanName, Class<T> type) {
        return applicationContext.getBean(beanName, type);
    }

    /** bean 是否存在。给「意图给了个不存在的 target_agent」这类场景做前置判断 */
    public boolean contains(String beanName) {
        return beanName != null && !beanName.isBlank() && applicationContext.containsBean(beanName);
    }
}
