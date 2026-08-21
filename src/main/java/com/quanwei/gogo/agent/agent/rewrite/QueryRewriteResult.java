package com.quanwei.gogo.agent.agent.rewrite;

/**
 * 问题改写的产出。
 *
 * @param originalQuery  用户的原始输入
 * @param rewrittenQuery 改写后的问题，未触发改写时等于原文
 * @param rewritten      是否真的发生了改写。false 表示跳过或模型判定无需改写
 * @param reason         没改写的原因，便于排查；改写了则为 null
 */
public record QueryRewriteResult(String originalQuery,
                                 String rewrittenQuery,
                                 boolean rewritten,
                                 String reason) {

    /** 未改写，原样返回 */
    public static QueryRewriteResult unchanged(String query, String reason) {
        return new QueryRewriteResult(query, query, false, reason);
    }

    /** 改写成功 */
    public static QueryRewriteResult rewritten(String original, String rewritten) {
        return new QueryRewriteResult(original, rewritten, true, null);
    }
}
