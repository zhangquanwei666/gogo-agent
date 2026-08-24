package com.quanwei.gogo.agent.agent.enums;

import lombok.Getter;

/**
 * 意图识别的命中层级，记录一次识别最终由哪一级给出结论。
 *
 * <p>这个字段会落进 chat_message.extra，上线后能直接统计各级命中率：
 * L1/L2 占比越高说明规则和样本覆盖得越好，延迟和成本也越低；
 * L3 占比居高不下，就该回头补 L1 关键词或 L2 样本了。
 *
 * <p>没有独立的兜底档：业务上 L3 就是兜底那一级，前面几级全部弃权后必然落到它，
 * 连它也判不出来时给出的 UNKNOWN 同样是 L3 的产出。再多一个 FALLBACK 只会让
 * 「谁给的结论」这件事有两种表示法，统计命中率时还得先把两者合并。
 */
@Getter
public enum IntentLevelEnum {

    /** L0 只做前置分流，不产出意图，不会作为最终层级出现 */
    L0("L0", "前置分流：连词与长度判定"),

    /** L1 关键词命中，约 1ms */
    L1("L1", "关键词分类器"),

    /** L2 向量检索命中，约 50ms（含一次 embedding 调用） */
    L2("L2", "向量相似度分类器"),

    /** L3 大模型，约 800ms，负责复杂意图和多意图，同时是整条链路的兜底 */
    L3("L3", "大模型分类器"),
    ;

    private final String code;

    private final String desc;

    IntentLevelEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
