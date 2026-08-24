package com.quanwei.gogo.agent.agent.pipeline;

import com.quanwei.gogo.agent.agent.enums.IntentLevelEnum;
import com.quanwei.gogo.agent.agent.intent.IntentRecognitionResult;
import com.quanwei.gogo.agent.agent.rewrite.QueryRewriteResult;

/**
 * 一次流水线执行的产出。
 *
 * @param intent          最终意图，永不为 null；识别不出来时是 unknown，路由到 masterAgent 去追问
 * @param rewrite         改写结果。没触发改写时是 {@code unchanged(原文)}，下游不用判断改写跑没跑
 * @param rewriteTriggered 是否真的调了改写模型。用来统计「快路径省下了多少次模型调用」，
 *                        这个比例就是分级设计到底值不值的直接证据
 * @param llmCalls        本次一共调了几次模型（改写 1 次 + L3 1 次），排查成本用
 * @param costMs          整条流水线耗时
 */
public record PipelineResult(IntentRecognitionResult intent,
                             QueryRewriteResult rewrite,
                             boolean rewriteTriggered,
                             int llmCalls,
                             long costMs) {

    /** 下游该拿去干活的问题文本：改写过就是改写后的，没改写就是原文 */
    public String effectiveQuery() {
        return rewrite.rewrittenQuery();
    }

    /** 目标子智能体的 bean 名，路由直接拿它 getBean */
    public String targetAgent() {
        return intent.getTargetAgent();
    }

    /** 是否走到了 L3。用于统计各级命中率 */
    public boolean hitByLlm() {
        return intent.getSource() == IntentLevelEnum.L3;
    }
}
