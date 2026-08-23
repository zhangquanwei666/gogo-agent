package com.quanwei.gogo.agent.agent.rewrite;

/**
 * 问题改写的产出，对应提示词约定的那个 JSON。
 *
 * @param originalQuery     用户的原始输入
 * @param rewrittenQuery    改写后的问题，没改写时等于原文。下游一律用这个字段
 * @param stepBackQuestion  退一步的通用问题，只用于辅助理解意图，不给下游当输入；不需要时为空串
 * @param related           模型判定本轮问题是否与历史相关
 * @param rewritten         改写后的问题是否真的和原文不同，由改写结果算出来，不是模型自报的
 * @param reason            改写思路或没改写的原因，中文，便于排查
 */
public record QueryRewriteResult(String originalQuery,
                                 String rewrittenQuery,
                                 String stepBackQuestion,
                                 boolean related,
                                 boolean rewritten,
                                 String reason) {

    /** 没改写，原样返回。跳过、解析失败、降级都走这个 */
    public static QueryRewriteResult unchanged(String query, String reason) {
        return new QueryRewriteResult(query, query, "", false, false, reason);
    }

    /**
     * 按模型输出构造。
     * rewritten 不采信模型自报，直接比对文本 —— 模型说改了但内容没变的情况并不少见。
     */
    public static QueryRewriteResult of(String originalQuery,
                                        String rewrittenQuery,
                                        String stepBackQuestion,
                                        boolean related,
                                        String reason) {
        boolean changed = rewrittenQuery != null && !rewrittenQuery.equals(originalQuery);
        return new QueryRewriteResult(originalQuery,
                changed ? rewrittenQuery : originalQuery,
                stepBackQuestion == null ? "" : stepBackQuestion,
                related,
                changed,
                reason);
    }
}
