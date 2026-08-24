package com.quanwei.gogo.agent.agent.intent;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 意图识别的各级阈值与开关，对应 application.yaml 的 agent.intent。
 *
 * <p>阈值全部外置：这些值一定要上线后看真实数据调，写死在代码里等于每调一次发一次版。
 */
@Getter
@Setter
@ToString
@Component
@ConfigurationProperties(prefix = "agent.intent")
public class IntentProperties {

    /** 种子文件位置，L1 关键词和 L2 样本都从这里来 */
    private String seedLocation = "classpath:intent_seed.yml";

    private L0 l0 = new L0();

    private L1 l1 = new L1();

    private L2 l2 = new L2();

    /** L0：前置分流 */
    @Getter
    @Setter
    @ToString
    public static class L0 {

        /** 关掉之后所有问题都从 L1 顺次往下走 */
        private boolean enabled = true;

        /**
         * 长度阈值。含连词且长度超过它，才判为复合问题直接下沉 L3。
         * 只看连词不看长度会误伤「北京和上海」这类短句 —— 那里的「和」是并列实体不是并列意图。
         */
        private int lengthThreshold = 10;
    }

    /** L1：关键词分类器 */
    @Getter
    @Setter
    @ToString
    public static class L1 {

        private boolean enabled = true;

        /**
         * 命中一组关键词时给的基础置信度。
         * 给不到 1.0 是因为关键词匹配本质是浅层规则，留出余量让主智能体能按置信度分档处理。
         */
        private double baseConfidence = 0.90D;
    }

    /** L2：向量分类器 */
    @Getter
    @Setter
    @ToString
    public static class L2 {

        private boolean enabled = true;

        /** top1 相似度低于它就放弃 L2，交给 L3 */
        private double threshold = 0.85D;

        /**
         * top1 与 top2 的最小分差。
         * top2 属于别的意图、且分差小于它，说明两个意图在语义上难分，
         * 这时候强行采信 top1 就是在赌 —— 直接放弃，让 L3 去判。
         */
        private double margin = 0.05D;

        /** 向量化模型接入配置 */
        private Embedding embedding = new Embedding();
    }

    /** 向量化模型。AgentScope 2.0.2 的 core 和 dashscope 扩展都没有 embedding 能力，只能自己调 */
    @Getter
    @Setter
    @ToString(exclude = "apiKey")
    public static class Embedding {

        private String apiKey;

        /** OpenAI 兼容的 embeddings 端点，百炼是 https://dashscope.aliyuncs.com/compatible-mode/v1 */
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

        private String modelName = "text-embedding-v4";

        /** 向量维度，要跟模型支持的维度对得上 */
        private int dimensions = 1024;

        /** 单次请求的超时秒数 */
        private int timeoutSeconds = 10;

        /** 建索引时的批大小，百炼单次最多 25 条 */
        private int batchSize = 20;
    }
}
