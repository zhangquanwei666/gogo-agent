package com.quanwei.gogo.agent.agent.intent.vector;

import com.quanwei.gogo.agent.agent.enums.IntentCategory;
import com.quanwei.gogo.agent.agent.intent.IntentProperties;
import com.quanwei.gogo.agent.agent.intent.embedding.EmbeddingClient;
import com.quanwei.gogo.agent.agent.intent.seed.IntentSeed;
import com.quanwei.gogo.agent.agent.intent.seed.IntentSeedLoader;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 意图向量库：把「向量化 + 建索引 + top-K 检索」这一层单独收在这里，
 * 相当于 AgentScope 的 {@code Knowledge}，只是它那套在 2.0.2 里没有 embedding 能力，只能自己搭。
 *
 * <p>为什么要和 {@link IntentVectorMatcher} 拆开：<b>检索和裁决是两件事</b>。
 * 「取回最像的 K 条样本」是纯数据操作，没有业务判断；
 * 「相似度够不够、top-2 咬得紧不紧、要不要放弃」全是策略。
 * 拆开之后调阈值不用碰检索代码，换向量库（真上 Milvus/PgVector）也不用碰策略代码，
 * 而且策略部分能用假数据单测，不需要真的调一次 embedding。
 *
 * <p>与 {@link EmbeddingClient} 的分工：那个类只负责「文本 → float[]」这一次 HTTP 调用，
 * 不知道意图的存在；本类负责把意图标签和向量绑在一起并提供检索。
 *
 * <p>整层可降级：没配 api-key、建索引失败，索引就一直是空的，
 * {@link #isReady()} 返回 false，L2 静默跳过走 L3，不影响服务启动。
 */
@Slf4j
@Component
public class IntentVectorStore {

    @Autowired
    private IntentSeedLoader intentSeedLoader;

    @Autowired
    private IntentProperties intentProperties;

    @Autowired
    private EmbeddingClient embeddingClient;

    /** 样本索引。建失败就一直是空的，retrieve 直接返回空列表 */
    private volatile List<SampleVector> index = List.of();

    /**
     * 启动时建索引。
     *
     * <p>失败只记 warn 不抛异常 —— 向量化服务不通不该让整个应用起不来，
     * L2 静默降级到 L3 之后，用户侧只是慢一点，功能不受影响。
     *
     * <p>放在启动而不是首次请求时建：样本几十上百条，懒加载等于让第一个用户白等一两秒，
     * 而这个成本挪到启动期是完全无感的。
     */
    @PostConstruct
    public void buildIndex() {
        IntentProperties.L2 config = intentProperties.getL2();
        if (!config.isEnabled()) {
            log.info("[L2_STORE] 已通过配置关闭，跳过建索引");
            return;
        }
        if (!embeddingClient.isAvailable()) {
            log.warn("[L2_STORE] 未配置向量化模型 api-key，L2 整级禁用，意图识别将由 L1 和 L3 承担");
            return;
        }

        List<String> texts = new ArrayList<>();
        List<IntentCategory> labels = new ArrayList<>();
        for (IntentSeed seed : intentSeedLoader.getSeeds()) {
            for (String sample : seed.samples()) {
                texts.add(sample);
                labels.add(seed.intent());
            }
        }
        if (texts.isEmpty()) {
            log.warn("[L2_STORE] 种子文件里没有任何样本，L2 不可用");
            return;
        }

        long start = System.currentTimeMillis();
        try {
            List<float[]> vectors = embeddingClient.embed(texts);
            List<SampleVector> built = new ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i++) {
                built.add(new SampleVector(labels.get(i), texts.get(i), vectors.get(i)));
            }
            this.index = List.copyOf(built);
            log.info("[L2_STORE] 索引就绪：{} 条样本，覆盖 {} 个意图，耗时 {}ms",
                    index.size(),
                    index.stream().map(SampleVector::intent).distinct().count(),
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("[L2_STORE] 建索引失败，本级禁用，后续统一降级到 L3。原因：{}", e.getMessage());
        }
    }

    /** 索引是否可用。不可用时上层应当直接跳过 L2 */
    public boolean isReady() {
        return !index.isEmpty();
    }

    /** 索引里的样本条数，给健康检查和日志用 */
    public int size() {
        return index.size();
    }

    /**
     * 检索最相似的 topK 条样本，按相似度降序。
     *
     * <p>只做检索，<b>不做任何阈值过滤</b> —— 分数够不够是调用方的策略，
     * 这里过滤掉低分样本的话，上层就再也拿不到原始分数来算 margin 了。
     *
     * @param text 用户问题，通常是改写智能体的产出
     * @param topK 取前几条，小于 1 时按 1 处理
     * @return 降序的命中列表；索引不可用或入参为空时返回空列表
     * @throws IllegalStateException 向量化调用失败，由调用方决定是降级还是上报
     */
    public List<Scored> retrieve(String text, int topK) {
        if (!isReady() || text == null || text.isBlank()) {
            return List.of();
        }
        float[] queryVector = embeddingClient.embedOne(text.trim());
        return index.stream()
                .map(sample -> new Scored(sample.intent(), sample.text(),
                        EmbeddingClient.cosine(queryVector, sample.vector())))
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(Math.max(1, topK))
                .toList();
    }

    /**
     * 索引里的一条样本。
     *
     * @param intent 样本所属意图，检索命中后就是判定结果
     * @param text   样本原文，会回填进 reason，排查误判时能看出是「像哪句话」才判成这个意图的
     * @param vector 样本向量
     */
    private record SampleVector(IntentCategory intent, String text, float[] vector) {
    }

    /**
     * 一条检索结果。
     *
     * @param intent     命中样本的意图
     * @param sampleText 命中样本的原文
     * @param score      余弦相似度 0~1
     */
    public record Scored(IntentCategory intent, String sampleText, double score) {
    }
}
