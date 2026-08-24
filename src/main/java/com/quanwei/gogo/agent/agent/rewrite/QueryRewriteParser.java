package com.quanwei.gogo.agent.agent.rewrite;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.quanwei.gogo.agent.agent.core.LlmJsonUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 把改写智能体的模型输出解析成 {@link QueryRewriteResult}。
 *
 * <p>解析失败一律退回原文，绝不抛异常 —— 改写是锦上添花，
 * 它挂了最坏情况是下游拿到一句没补全的问题，而不是整轮对话失败。
 */
@Slf4j
public final class QueryRewriteParser {

    /** 改写结果长度超过原文这个倍数，判定为模型自己扩写跑偏，退回原文 */
    private static final int MAX_EXPAND_RATIO = 5;

    private QueryRewriteParser() {
    }

    /**
     * @param modelOutput   模型原始输出
     * @param originalQuery 用户原始问题，解析失败时的兜底值
     */
    public static QueryRewriteResult parse(String modelOutput, String originalQuery) {
        String json = LlmJsonUtils.extractJsonObject(modelOutput);
        if (json == null) {
            log.warn("[REWRITE] 输出里没有 JSON，退回原文。原始输出：{}", abbreviate(modelOutput));
            return QueryRewriteResult.unchanged(originalQuery, "模型输出不是 JSON，未改写");
        }

        JSONObject obj;
        try {
            obj = JSON.parseObject(json);
        } catch (Exception e) {
            log.warn("[REWRITE] JSON 解析失败，退回原文。原因：{}", e.getMessage());
            return QueryRewriteResult.unchanged(originalQuery, "改写结果解析失败，未改写");
        }
        if (obj == null) {
            return QueryRewriteResult.unchanged(originalQuery, "改写结果为空，未改写");
        }

        String rewritten = trimToNull(obj.getString("rewritten_question"));
        if (rewritten == null) {
            return QueryRewriteResult.unchanged(originalQuery, "模型未给出改写结果，保持原问题");
        }

        // 长度失控说明模型没在做指代消解，而是自己发挥去了。这种「改写」交给下游只会误导识别
        if (originalQuery != null && rewritten.length() > originalQuery.length() * MAX_EXPAND_RATIO) {
            log.warn("[REWRITE] 改写结果过长（{} -> {}），判定为模型扩写，退回原文",
                    originalQuery.length(), rewritten.length());
            return QueryRewriteResult.unchanged(originalQuery, "改写结果异常膨胀，退回原问题");
        }

        return QueryRewriteResult.of(originalQuery,
                rewritten,
                obj.getString("step_back_question"),
                obj.getBooleanValue("related"),
                obj.getString("reason"));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
