package com.quanwei.gogo.agent.agent.intent;


import com.quanwei.gogo.agent.agent.intent.rule.IntentRuleMatcher;
import com.quanwei.gogo.agent.agent.intent.vector.IntentVectorMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图识别的分级路由器，负责 L0 到 L2 这条不调模型的快路径。
 *
 * <pre>
 * L0  连词 + 长度判定    不产出意图，只决定要不要直接下沉 L3
 * L1  关键词分类器       ~1ms，无外部调用
 * L2  向量相似度分类器   ~50ms，一次 embedding 调用
 * </pre>
 *
 * <p>{@link #route(String)} 返回空表示快路径没给出可信结论，调用方应当走 L3。
 * L3 要模型实例和提示词，属于智能体自己的资源，不放进路由器 ——
 * 这样路由器保持无模型依赖，可以脱离 Spring 上下文单测。
 *
 * <p>每一级都可以**主动弃权**：L1 遇到并列、L2 遇到 top-2 纠缠，都返回空交给下一级，
 * 而不是硬给一个五五开的结论。分级的价值一半在提速，另一半就在这里。
 */
@Slf4j
@Component
public class IntentRecognitionRouter {

    /**
     * 连词表。
     *
     * <p>只收「能连接两个独立动作」的词，不收纯并列名词的连接词。
     * 真正的复合意图基本都会用「然后」「再」「另外」这类带时序或递进的词。
     */
    private static final Pattern CONJUNCTION_PATTERN = Pattern.compile("然后|接着|之后|再帮|再订|再查|再看|顺便|另外|此外|同时|并且|以及|还要|还得|还想|也要|也帮|一起|外加|完了|先.{0,6}再|既.{0,6}又");

    /**
     * 连词至少要出现在句中才算多意图信号，句首连词多为口语衔接词。
     */
    private static final int MULTI_INTENT_MIN_CONJUNCTION_OFFSET = 4;

    @Autowired
    private IntentProperties intentProperties;

    @Autowired
    private IntentRuleMatcher ruleMatcher;

    @Autowired
    private IntentVectorMatcher vectorMatcher;


    /**
     * 走一遍快路径。
     *
     * @param question 用户问题，通常是改写智能体的产出
     * @return 命中则返回结论；返回空表示需要 L3 兜底
     */
    public Optional<IntentRecognitionResult> route(String question) {
        if (question == null || question.isBlank()) {
            return Optional.empty();
        }
        String query = question.trim();


        //L0
        if (hashMultiIntentSignal(query)) {
            log.info("[INTENT_RECOGNITION] L0 检测到对应连词，疑似多意图，跳过L1/L2，直接进行L3多意图识别");
            return Optional.empty();
        }

        // L1
        IntentRuleMatcher.Outcome l1OutCome = ruleMatcher.evaluate(query);
        if (l1OutCome.verdict() == IntentRuleMatcher.Verdict.HIT) {
            log.info("[INTENT_RECOGNITION] L1 规则命中 {}", l1OutCome.result().getPrimaryIntent());
            return Optional.of(l1OutCome.result());
        }

        // AMBIGUOUS 是「拆不开」不是「没识别到」：L2 的向量检索同样只返回单一意图，
        // 复合句给它也是白给，所以连 L2 一起跳过，直接交给能拆多意图的 L3
        if (l1OutCome.verdict() == IntentRuleMatcher.Verdict.AMBIGUOUS) {
            log.info("[INTENT_RECOGNITION] L1 检出跨智能体多意图 {}，跳过 L2 直接进 L3",
                    l1OutCome.ambiguousCategories());
            return Optional.empty();
        }


        // L2
        Optional<IntentRecognitionResult> vectorHit;
        try {
            vectorHit = vectorMatcher.match(query);
        } catch (Exception e) {
            vectorHit = Optional.empty();
            log.warn("[INTENT_ROUTER] L2 异常，降级到 L3: {}", e.getMessage());
        }
        if (vectorHit.isPresent()) {
            return vectorHit;
        }
        return  Optional.empty();
    }

    public boolean hashMultiIntentSignal(String query) {
        if (query == null || query.length()<intentProperties.getL0().getLengthThreshold()) {
            return false;
        }
        Matcher matcher = CONJUNCTION_PATTERN.matcher(query);
        return matcher.find() && matcher.start()>=MULTI_INTENT_MIN_CONJUNCTION_OFFSET;
    }
}
