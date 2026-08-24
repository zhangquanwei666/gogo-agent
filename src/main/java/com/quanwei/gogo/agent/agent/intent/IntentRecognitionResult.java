package com.quanwei.gogo.agent.agent.intent;

import com.quanwei.gogo.agent.agent.enums.IntentCategory;
import com.quanwei.gogo.agent.agent.enums.IntentLevelEnum;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 意图识别的最终产出，L1 / L2 / L3 三层共用。
 *
 * <p><b>三层同构是这个类存在的意义</b>：{@link #toJsonMap()} 产出的结构与
 * prompt/intent-recognition-agent-system.md 里 L3 模型输出的 JSON 完全一致 ——
 * {@code intents[] / primary_intent / multi_intent / overall_reason}。
 * 规则命中、向量命中、模型兜底三条路径下游拿到的是同一个形状，
 * 主智能体只需要一套解析逻辑；否则每加一层就要在下游多写一个分支。
 *
 * <p>{@code source} 记录最终由哪一级给出结论，落进 chat_message.extra 之后
 * 能直接统计各级命中率：L1/L2 占比高说明规则和样本覆盖得好，
 * L3 居高不下就该回头补 intent_seed.yml。
 *
 * <p>置信度只存档位不存小数：L3 模型只会给 high/medium/low，逼它报小数只会得到一个编出来的数。
 * L1/L2 确实有真实分数，但那是命中强度不是置信度，单独放在 {@link #score} 里供排障，
 * 不混进判定字段。
 */
@Getter
@ToString
public class IntentRecognitionResult {

    /** 由哪一级给出的结论 */
    private final IntentLevelEnum source;

    /**
     * 识别出的意图列表，按执行先后顺序排列。
     * 单意图时只有一项；多意图时第一项通常就是 {@link #primary}，但不强制 —— 最紧迫的未必排最前。
     */
    private final List<IntentItem> intents;

    /** 主意图，最核心或最紧迫的那个 */
    private final IntentCategory primary;

    /** 是否复合意图 */
    private final boolean multiIntent;

    /** 整体判定理由，中文。多意图时说明各意图之间的关系 */
    private final String overallReason;

    /**
     * L1/L2 命中时附带的命中得分（L2 是向量相似度），便于排障；L1 和 L3 为 null。
     *
     * <p>刻意用包装类型：null 表示「这一级本来就没有分数」，
     * 用 0.0 表示的话就和「算出来真的是 0 分」分不开了。
     */
    private final Double score;

    public IntentRecognitionResult(IntentLevelEnum source,
                                   List<IntentItem> intents,
                                   IntentCategory primary,
                                   boolean multiIntent,
                                   String overallReason,
                                   Double score) {
        this.source = source;
        this.intents = intents == null ? List.of() : List.copyOf(intents);
        this.primary = primary == null ? IntentCategory.UNKNOWN : primary;
        this.multiIntent = multiIntent;
        this.overallReason = overallReason == null ? "" : overallReason;
        this.score = score;
    }

    /** 单意图命中，L1/L2/L3 都可能走这条 */
    public static IntentRecognitionResult single(IntentLevelEnum source,
                                                 IntentCategory intent,
                                                 Confidence confidence,
                                                 String reason,
                                                 Double score) {
        IntentCategory resolved = intent == null ? IntentCategory.UNKNOWN : intent;
        return new IntentRecognitionResult(source,
                List.of(new IntentItem(resolved, confidence, reason)),
                resolved,
                false,
                reason,
                score);
    }

    /**
     * 复合意图命中，只可能来自 L3 —— L1 只能返回单意图，L2 的向量检索也只返回一个最相似样本，
     * 两级遇到复合句的正确反应是弃权下沉，而不是硬拆。
     *
     * @param items         各意图，按执行先后排列
     * @param primary       最核心或最紧迫的意图
     * @param overallReason 整体理由，须说明各意图之间的关系
     */
    public static IntentRecognitionResult multi(List<IntentItem> items,
                                                IntentCategory primary,
                                                String overallReason) {
        boolean multi = items != null && items.size() > 1;
        return new IntentRecognitionResult(IntentLevelEnum.L3, items, primary, multi, overallReason, null);
    }

    /**
     * 兜底：连 L3 也判不出来，或者 L3 调用/解析失败。路由到 masterAgent 去追问。
     *
     * <p>层级记 L3 而不是单独的兜底档 —— 业务上 L3 就是兜底那一级，
     * 「它判成了 unknown」和「它没能给出结果」对下游是同一件事：都得让用户再说一遍。
     * 真要区分是判不出还是调用失败，看 {@code overall_reason} 就行。
     */
    public static IntentRecognitionResult unknown(String reason) {
        return new IntentRecognitionResult(IntentLevelEnum.L3,
                List.of(new IntentItem(IntentCategory.UNKNOWN, Confidence.LOW, reason)),
                IntentCategory.UNKNOWN,
                false,
                reason,
                null);
    }

    /** 主意图的 code，与提示词映射表取值一致 */
    public String getPrimaryIntent() {
        return primary.getCode();
    }

    /** 主意图的目标子智能体 bean 名，路由直接拿它去 getBean */
    public String getTargetAgent() {
        return primary.getTargetAgent();
    }

    /**
     * 转成给下游和落库用的 Map，与 L3 提示词约定的 JSON 结构逐字段对齐。
     *
     * <p>用 snake_case 而不是 Java 的 camelCase：这份结构会进 chat_message.extra，
     * 也会被前端读，跟提示词里约定的字段保持一致，省掉一层心智转换。
     *
     * <p>用 LinkedHashMap 而不是 Map.of：后者迭代顺序不确定，落库和日志的 JSON 字段顺序
     * 每次都可能变，diff 起来很难看；而且 Map.of 遇到 null value 直接抛 NPE，
     * 这里的每个字段都得先兜底才敢往里塞，不如换个容器省事。
     *
     * <p>{@code source} 和 {@code score} 是 L3 输出里没有的两个字段，属于我们自己的排障信息，
     * 附在后面不影响下游按同一套 schema 解析。
     */
    public Map<String, Object> toJsonMap() {
        Map<String, Object> map = new LinkedHashMap<>();

        List<Map<String, Object>> intentList = new ArrayList<>(intents.size());
        for (IntentItem item : intents) {
            intentList.add(item.toMap());
        }
        map.put("intents", intentList);
        map.put("primary_intent", getPrimaryIntent());
        map.put("target_agent", getTargetAgent());
        map.put("multi_intent", multiIntent);
        map.put("overall_reason", overallReason);
        map.put("source", source == null ? "" : source.getCode());
        map.put("score", score);
        return map;
    }

    /**
     * 置信度档位，取值与提示词里 L3 输出的 {@code high|medium|low} 一致。
     */
    public enum Confidence {

        /** 语义明确、措辞标准，可直接执行 */
        HIGH,

        /** 能判断但存在歧义，下游应考虑先确认再执行 */
        MEDIUM,

        /** 勉强猜测，由 masterAgent 向用户确认或追问 */
        LOW;

        /** 落库和进提示词时的取值，小写 */
        public String wireValue() {
            return name().toLowerCase();
        }

        /** 解析模型输出的档位。认不出一律当 LOW —— 宁可让下游多确认一次，也不要凭空拔高 */
        public static Confidence fromWire(String value) {
            if (value == null) {
                return LOW;
            }
            return switch (value.trim().toLowerCase()) {
                case "high" -> HIGH;
                case "medium" -> MEDIUM;
                default -> LOW;
            };
        }

        /**
         * 把 L2 的相似度归成档位。
         * 边界跟 {@code agent.intent.l2.threshold} 是两回事：那个决定「认不认」，这个决定「认得有多稳」。
         */
        public static Confidence fromScore(double score) {
            if (score >= 0.90D) {
                return HIGH;
            }
            if (score >= 0.80D) {
                return MEDIUM;
            }
            return LOW;
        }
    }

    /**
     * 单个意图项，对应 L3 输出里 {@code intents[]} 的一个元素。
     */
    @Getter
    @ToString
    public static class IntentItem {

        /** 意图类别 */
        private final IntentCategory intent;

        /** 置信度档位 */
        private final Confidence confidence;

        /** 分类理由，中文。会在思考面板向用户展示，不允许出现 bean 名、工具名、内部字段名 */
        private final String reason;

        /**
         * @param confidence 传 null 按 MEDIUM 处理 —— 缺省不该被当成高置信直接执行，
         *                   也不该低到触发追问，落在中间让下游自己决定
         */
        public IntentItem(IntentCategory intent, Confidence confidence, String reason) {
            this.intent = intent == null ? IntentCategory.UNKNOWN : intent;
            this.confidence = confidence == null ? Confidence.MEDIUM : confidence;
            this.reason = reason == null ? "" : reason;
        }

        /** 意图 code */
        public String getIntentCode() {
            return intent.getCode();
        }

        /**
         * 目标子智能体 bean 名。
         *
         * <p>从枚举派生，<b>不采信 L3 输出里的 target_agent 字段</b>：
         * 那是模型填的，拼成 itineraryPlanagent 这种大小写错误下游 getBean 会直接抛异常。
         * 映射关系在 {@link IntentCategory} 里是权威的，模型那个字段只用来对账。
         */
        public String getTargetAgent() {
            return intent.getTargetAgent();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("intent", getIntentCode());
            map.put("target_agent", getTargetAgent());
            map.put("confidence", confidence.wireValue());
            map.put("reason", reason);
            return map;
        }
    }
}
