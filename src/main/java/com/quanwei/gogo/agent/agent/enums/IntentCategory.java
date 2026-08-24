package com.quanwei.gogo.agent.agent.enums;

import lombok.Getter;

/**
 * 意图枚举，与 prompt/intent-recognition-agent-system.md 的「意图类别与目标子智能体映射」表一一对应。
 *
 * <p>三处必须同时一致，改一处就要改三处：
 * <ol>
 *   <li>本枚举的 {@code code}；</li>
 *   <li>系统提示词映射表里的意图类别（L3 模型按它取值）；</li>
 *   <li>intent_seed.yml 的 intent 字段（L1 关键词、L2 样本按它打标）。</li>
 * </ol>
 * 对不上的种子会在启动时抛异常；对不上的模型输出会被 {@link #of(String)} 静默收敛成 {@link #UNKNOWN}，
 * 表现是「某个意图永远识别不出来」—— 服务照常启动、接口照常返回，只是悄悄少了一个分类，这种故障最难查。
 *
 * <p>不再按「领域 + 动作」两层拆：路由的唯一依据是 {@code targetAgent}，
 * 多一层 domain 只是给同一个子智能体的意图再分一次组，路由用不上，反而要维护两套分类边界。
 */
@Getter
public enum IntentCategory {

    /* ---------- 差旅申请与审批：itineraryManageAgent ---------- */
    TRAVEL_APPLICATION("travel_application", "itineraryManageAgent", "提交新的差旅申请、出差审批"),
    TRAVEL_CANCEL("travel_cancel", "itineraryManageAgent", "取消出差申请或审批单"),
    TRAVEL_MODIFY("travel_modify", "itineraryManageAgent", "修改差旅申请信息"),
    APPROVAL_QUERY("approval_query", "itineraryManageAgent", "查询审批进度、状态、结果"),
    TRAVEL_ORDER_QUERY("travel_order_query", "itineraryManageAgent", "查询已有差旅单详情或状态"),

    /* ---------- 行程规划与查询：itineraryPlanAgent ---------- */
    ITINERARY_PLANNING("itinerary_planning", "itineraryPlanAgent", "规划行程、做方案"),
    FLIGHT_SEARCH("flight_search", "itineraryPlanAgent", "查航班"),
    TRAIN_SEARCH("train_search", "itineraryPlanAgent", "查火车、高铁"),
    HOTEL_SEARCH("hotel_search", "itineraryPlanAgent", "查酒店"),

    /* ---------- 预订：bookingAgent ---------- */
    BOOKING("booking", "bookingAgent", "预订、改签、取消已选方案"),

    /* ---------- 报销：reimbursementAgent ---------- */
    REIMBURSEMENT("reimbursement", "reimbursementAgent", "报销、识别发票、生成报销单"),

    /* ---------- 信息查询：infoAgent ---------- */
    POLICY_QUERY("policy_query", "infoAgent", "差旅政策、餐标、酒店标准、签证入境政策"),
    ATTRACTIONS_QUERY("attractions_query", "infoAgent", "目的地景点、旅游信息"),
    GENERAL_INFO("general_info", "infoAgent", "天气、地图、交通、目的地新闻等通用信息"),

    /* ---------- 主智能体兜底：masterAgent ---------- */
    GREETING("greeting", "masterAgent", "打招呼、寒暄"),

    /**
     * 无法明确分类或信息严重不足。
     * 必须保留这一项 —— 没有兜底值的意图体系一定会把长尾问题硬塞进某个错误分类，
     * 那比返回「没听懂」危害大得多。
     */
    UNKNOWN("unknown", "masterAgent", "无法明确分类或信息严重不足"),
    ;

    /** 对外标识，与提示词映射表、intent_seed.yml 三处一致。小写下划线，与模型输出格式对齐 */
    private final String code;

    /**
     * 目标子智能体在 Spring 容器里的 bean 名，camelCase。
     *
     * <p>直接用于 {@code context.getBean(targetAgent, ReActAgent.class)}，
     * 所以拼写必须和 bean 名严格一致，系统不做任何大小写或命名转换。
     *
     * <p>路由只认这个字段：意图类别是给人看和给模型选的，真正决定「这句话交给谁」的是它。
     * 也因此两个意图指向同一个子智能体时（如查机票和查酒店都归行程规划），
     * 一句话里同时出现它们并不构成需要拆分的多意图 —— 反正都是同一个 Agent 接手。
     */
    private final String targetAgent;

    /** 中文说明，会拼进提示词的意图清单，也用于日志 */
    private final String desc;

    IntentCategory(String code, String targetAgent, String desc) {
        this.code = code;
        this.targetAgent = targetAgent;
        this.desc = desc;
    }

    /** 按 code 查，大小写不敏感。查不到返回 UNKNOWN 而不是 null，省掉调用方的空判断 */
    public static IntentCategory of(String code) {
        if (code == null || code.isBlank()) {
            return UNKNOWN;
        }
        String trimmed = code.trim();
        for (IntentCategory intent : values()) {
            if (intent.code.equalsIgnoreCase(trimmed)) {
                return intent;
            }
        }
        return UNKNOWN;
    }
}
