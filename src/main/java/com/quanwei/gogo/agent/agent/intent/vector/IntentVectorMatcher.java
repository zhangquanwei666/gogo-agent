package com.quanwei.gogo.agent.agent.intent.vector;

import com.quanwei.gogo.agent.agent.enums.IntentLevelEnum;
import com.quanwei.gogo.agent.agent.intent.IntentProperties;
import com.quanwei.gogo.agent.agent.intent.IntentRecognitionResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * L2 向量相似度匹配器，约 50ms（含一次 embedding 调用）。
 *
 * <p>只负责裁决，检索交给 {@link IntentVectorStore}：
 * 「取回最像的 K 条」是数据操作，「够不够像、要不要认」是策略，
 * 分开之后调阈值不用碰检索、换向量库不用碰策略。
 *
 * <p>取 top-2 而不是 top-1，是为了识别「拿不准」：
 * 第二名属于另一个意图、而且分数咬得很紧，说明这句话在语义空间里正卡在两个意图中间
 * （多意图复合句的典型表现 —— 它的 embedding 会同时贴近多个单意图样本），
 * 这时候采信第一名纯属运气。宁可放弃 L2 交给 L3，也不要押一个五五开的结论，更不要「吞」掉另一半意图。
 *
 * <p>整级可降级：索引没建起来、检索抛异常，一律返回 empty 走 L3，不阻塞主链路。
 */
@Slf4j
@Component
public class IntentVectorMatcher {

    /** 默认相似度阈值，被 {@code agent.intent.l2.threshold} 覆盖 */
    public static final double DEFAULT_SCORE_THRESHOLD = 0.75D;

    /**
     * 默认歧义 margin：top-1 与 top-2 属于不同意图且分差小于该值时，认为输入在两个意图之间
     * 摇摆（复合句的 embedding 常同时贴近多个单意图种子样本），保守放行到 L3。
     * 被 {@code agent.intent.l2.margin} 覆盖。
     */
    public static final double DEFAULT_SCORE_MARGIN = 0.05D;

    /** Top-K 检索上限；top-1 用于命中判定，top-2 用于意图歧义（margin）校验 */
    private static final int TOP_K = 2;

    @Autowired
    private IntentVectorStore vectorStore;

    @Autowired
    private IntentProperties intentProperties;

    private double scoreThreshold = DEFAULT_SCORE_THRESHOLD;

    private double scoreMargin = DEFAULT_SCORE_MARGIN;

    /**
     * 启动时用配置里的值覆盖默认阈值。
     *
     * <p>常量只是兜底 —— 这两个数一定要上线后看真实数据调，
     * 写死在代码里等于每调一次发一次版，所以以 {@code application.yaml} 为准。
     */
    @PostConstruct
    public void init() {
        IntentProperties.L2 config = intentProperties.getL2();
        if (isValidScore(config.getThreshold())) {
            this.scoreThreshold = config.getThreshold();
        }
        if (isValidScore(config.getMargin())) {
            this.scoreMargin = config.getMargin();
        }
        log.info("[L2] 匹配器就绪：阈值 {}，margin {}", scoreThreshold, scoreMargin);
    }

    /**
     * 对输入文本执行 L2 向量检索。
     *
     * @param text 改写后的问题文本
     * @return 命中时返回结果，未命中返回 {@link Optional#empty()}
     */
    public Optional<IntentRecognitionResult> match(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        if (!intentProperties.getL2().isEnabled() || !vectorStore.isReady()) {
            return Optional.empty();
        }
        String normalized = text.trim();

        List<IntentVectorStore.Scored> hits;
        try {
            // 先取 top2，阈值和 margin 由本类自己过滤 —— 让向量库按阈值筛的话，
            // 分数低于阈值的 top2 会被吃掉，margin 就没数据可算了
            hits = vectorStore.retrieve(normalized, TOP_K);
        } catch (Exception e) {
            // 任何 embedding/检索失败都视为未命中，让 L3 兜底，不阻塞主流程
            log.warn("[L2] 向量检索失败，降级到 L3。原因：{}", e.getMessage());
            return Optional.empty();
        }

        if (hits.isEmpty()) {
            return Optional.empty();
        }

        IntentVectorStore.Scored top = hits.get(0);
        if (top.score() < scoreThreshold) {
            log.debug("[L2] 放弃：top-1 相似度 {} < 阈值 {}，放行到 L3", fmt(top.score()), scoreThreshold);
            return Optional.empty();
        }

        // margin 校验：top-1/top-2 分属不同意图且分差过小时，说明输入在两个意图间摇摆
        // （多意图复合句的典型表现），保守放行到 L3，避免「吞」掉另一半意图。
        if (hits.size() >= 2) {
            IntentVectorStore.Scored second = hits.get(1);
            double gap = top.score() - second.score();
            if (top.intent() != second.intent() && gap < scoreMargin) {
                log.info("[L2] 意图歧义：top1={}({}) 与 top2={}({}) 分差 {} < margin {}，放行到 L3",
                        top.intent().getCode(), fmt(top.score()),
                        second.intent().getCode(), fmt(second.score()),
                        fmt(gap), scoreMargin);
                return Optional.empty();
            }
        }

        log.debug("[L2] 命中 {}，相似度 {}，最近样本「{}」",
                top.intent().getCode(), fmt(top.score()), top.sampleText());

        // 判定字段给档位，原始相似度另外挂在 score 上：
        // 下游按 high/medium/low 分支，和 L1/L3 一套逻辑；排障时再看 score 具体多少。
        return Optional.of(IntentRecognitionResult.single(
                IntentLevelEnum.L2,
                top.intent(),
                IntentRecognitionResult.Confidence.fromScore(top.score()),
                "L2 向量命中：相似度=" + fmt(top.score()) + "，匹配样本「" + top.sampleText() + "」",
                top.score()));
    }

    private static boolean isValidScore(double value) {
        return value >= 0.0D && value <= 1.0D;
    }

    private static String fmt(double value) {
        return String.format("%.3f", value);
    }
}
