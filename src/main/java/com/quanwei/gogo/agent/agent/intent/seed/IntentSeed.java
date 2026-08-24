package com.quanwei.gogo.agent.agent.intent.seed;

import com.quanwei.gogo.agent.agent.enums.IntentCategory;

import java.util.List;

/**
 * 一个意图的种子数据，来自 intent_seed.yml 的一条。
 *
 * @param intent   意图
 * @param keywords L1 用。外层是「或」，内层是「与」——{@code [[报销, 发票], [发票, 开]]}
 *                 表示「同时出现报销和发票」或「同时出现发票和开」都算命中
 * @param samples  L2 用。启动时向量化建索引
 */
public record IntentSeed(IntentCategory intent,
                         List<List<String>> keywords,
                         List<String> samples) {

    public IntentSeed {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        samples = samples == null ? List.of() : List.copyOf(samples);
    }
}
