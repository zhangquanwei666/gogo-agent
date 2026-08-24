package com.quanwei.gogo.agent.agent.intent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.quanwei.gogo.agent.agent.core.LlmJsonUtils;
import com.quanwei.gogo.agent.agent.enums.IntentCategory;
import com.quanwei.gogo.agent.agent.enums.IntentLevelEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 把意图识别的 JSON 解析成 {@link IntentRecognitionResult}。
 *
 * <p>因为三层的产出是同构的，这一个解析器同时吃两种输入：
 * L3 模型按提示词吐出的 JSON，和 L1/L2 命中后 {@code toJsonMap()} 序列化出来的 JSON。
 * 字段名一致，区别只是后者多带 {@code source} 和 {@code score}。
 *
 * <p><b>target_agent 字段读进来只用于对账，不用于路由</b>：那是模型填的，
 * 拼错一个字母下游 getBean 就炸。真正的映射以 {@link IntentCategory} 为准。
 */
@Slf4j
public final class IntentResultParser {

    private IntentResultParser() {
    }

    /**
     * @param modelOutput   模型或快路径产出的 JSON 文本
     * @param defaultSource JSON 里没有 source 字段时按哪一级记账（L3 输出里没有这个字段）
     * @return 解析不出来时返回 {@link IntentRecognitionResult#unknown(String)}，不抛异常
     */
    public static IntentRecognitionResult parse(String modelOutput, IntentLevelEnum defaultSource) {
        String json = LlmJsonUtils.extractJsonObject(modelOutput);
        if (json == null) {
            log.warn("[INTENT_PARSE] 输出里没有 JSON。原始输出：{}", abbreviate(modelOutput));
            return IntentRecognitionResult.unknown("意图识别输出不是 JSON，无法解析");
        }

        JSONObject obj;
        try {
            obj = JSON.parseObject(json);
        } catch (Exception e) {
            log.warn("[INTENT_PARSE] JSON 解析失败：{}", e.getMessage());
            return IntentRecognitionResult.unknown("意图识别结果解析失败");
        }
        if (obj == null) {
            return IntentRecognitionResult.unknown("意图识别结果为空");
        }

        List<IntentRecognitionResult.IntentItem> items = parseItems(obj.getJSONArray("intents"));

        // 主意图优先取 primary_intent；模型漏填时退而用列表第一项 ——
        // 列表非空却没给主意图，多半是模型偷懒，没必要为此把整条结果作废
        IntentCategory primary = IntentCategory.of(obj.getString("primary_intent"));
        if (primary == IntentCategory.UNKNOWN && !items.isEmpty()) {
            primary = items.get(0).getIntent();
        }
        if (primary == IntentCategory.UNKNOWN && items.isEmpty()) {
            return IntentRecognitionResult.unknown(defaultString(obj.getString("overall_reason"),
                    "模型未给出任何可用意图"));
        }
        if (items.isEmpty()) {
            items = List.of(new IntentRecognitionResult.IntentItem(
                    primary, IntentRecognitionResult.Confidence.MEDIUM, obj.getString("overall_reason")));
        }

        IntentLevelEnum source = resolveSource(obj.getString("source"), defaultSource);

        // multi_intent 不采信模型自报，按 items 实际条数算 —— 模型说是多意图却只给一条的情况并不少见
        boolean multiIntent = items.size() > 1;

        return new IntentRecognitionResult(source,
                items,
                primary,
                multiIntent,
                obj.getString("overall_reason"),
                obj.getDouble("score"));
    }

    private static List<IntentRecognitionResult.IntentItem> parseItems(JSONArray array) {
        List<IntentRecognitionResult.IntentItem> items = new ArrayList<>();
        if (array == null) {
            return items;
        }
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (item == null) {
                continue;
            }
            IntentCategory category = IntentCategory.of(item.getString("intent"));
            // 认不出的意图码直接丢掉，不要塞成 UNKNOWN —— 多意图里混进一个 UNKNOWN，
            // 下游会拿它去问 masterAgent，等于给用户凭空多一次追问
            if (category == IntentCategory.UNKNOWN && !isExplicitUnknown(item.getString("intent"))) {
                log.warn("[INTENT_PARSE] 未知意图码 {}，已忽略该项", item.getString("intent"));
                continue;
            }
            items.add(new IntentRecognitionResult.IntentItem(
                    category,
                    IntentRecognitionResult.Confidence.fromWire(item.getString("confidence")),
                    item.getString("reason")));
        }
        return items;
    }

    /** 区分「模型真的填了 unknown」和「填了个我们不认识的码」 */
    private static boolean isExplicitUnknown(String code) {
        return code != null && IntentCategory.UNKNOWN.getCode().equalsIgnoreCase(code.trim());
    }

    private static IntentLevelEnum resolveSource(String code, IntentLevelEnum fallback) {
        if (code == null || code.isBlank()) {
            return fallback;
        }
        for (IntentLevelEnum level : IntentLevelEnum.values()) {
            if (level.getCode().equalsIgnoreCase(code.trim())) {
                return level;
            }
        }
        return fallback;
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
