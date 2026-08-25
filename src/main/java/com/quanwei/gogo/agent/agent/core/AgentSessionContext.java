package com.quanwei.gogo.agent.agent.core;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话级上下文：一次会话内、跨轮次、跨工具共享的东西。
 *
 * <p>和 {@link AgentContext} 的分界要分清，两者都叫「上下文」但生命周期差一个数量级：
 * <pre>
 * AgentContext         一轮请求      原始问题、改写结果、本轮的执行链路
 * AgentSessionContext  整个会话      用户身份、会话标识、工具间共享的缓存
 * </pre>
 * 一轮结束 AgentContext 就该扔掉，本类要活到会话结束。
 *
 * <p><b>字段为什么这么设：</b>
 * <ul>
 *   <li>{@code userId} / {@code sessionId} 是不可变的身份，构造时就校验非空 ——
 *       持久化记忆全靠这两个键定位，为空会静默存到一个谁也找不回来的地方；</li>
 *   <li>缓存字段一律 {@code volatile} 或用 {@link ConcurrentHashMap}：工具可能并行执行
 *       （Toolkit 开 parallel 时），Hook 又在另一个线程读，不加可见性保证会读到旧值。</li>
 * </ul>
 *
 * <p><b>缓存必须能失效。</b>凡是「查出来缓存住」的字段，都要配一个 invalidate 方法，
 * 并且在对应的写操作成功后调用。少调一次的后果是：用户明明改了常驻城市，
 * 后面几轮问下来智能体还在用旧的 —— 这种错不会报异常，只会让用户觉得系统在胡说。
 */
@Getter
@Setter
@ToString(of = {"userId", "sessionId"})
public class AgentSessionContext {

    private final String userId;

    private final String sessionId;

    /**
     * 差旅政策缓存。
     * 必须以城市为键：政策按城市分层（一线/新一线/其他）而不同，
     * 用单个字段缓存会让「上海的标准」串到「西安的问题」上。
     */
    @Getter(AccessLevel.NONE)
    private final Map<String, String> travelPolicyByCity = new ConcurrentHashMap<>();

    /**
     * 用户联系人/乘机人信息（JSON）。会话内准静态，
     * 但更新联系人的工具执行成功后必须调 {@link #invalidateUserContactInfo()}
     */
    private volatile String userContactInfo;

    /**
     * 用户常驻/办公城市（JSON）。会话内准静态，
     * 但更新常驻地的工具执行成功后必须调 {@link #invalidateUserBaseLocation()}
     */
    private volatile String userBaseLocation;

    public AgentSessionContext(String userId, String sessionId) {
        Assert.hasText(userId, "userId must not be blank");
        Assert.hasText(sessionId, "sessionId must not be blank");
        this.userId = userId;
        this.sessionId = sessionId;
    }

    /**
     * 取指定城市的差旅政策缓存。
     *
     * @param city 目的城市；为 null 时返回 null，即不走缓存
     */
    public String getTravelPolicy(String city) {
        return city != null ? travelPolicyByCity.get(city.trim()) : null;
    }

    public void setTravelPolicy(String city, String travelPolicy) {
        if (city != null && travelPolicy != null) {
            travelPolicyByCity.put(city.trim(), travelPolicy);
        }
    }

    /** 政策有变时整体清掉，不做按城市清 —— 政策调整通常是全量下发的 */
    public void invalidateTravelPolicy() {
        travelPolicyByCity.clear();
    }

    /** 联系人信息被更新后失效缓存，下次查询回源数据库 */
    public void invalidateUserContactInfo() {
        this.userContactInfo = null;
    }

    /** 常驻城市被更新后失效缓存，下次查询回源数据库 */
    public void invalidateUserBaseLocation() {
        this.userBaseLocation = null;
    }
}
