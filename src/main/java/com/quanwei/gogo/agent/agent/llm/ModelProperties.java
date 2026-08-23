package com.quanwei.gogo.agent.agent.llm;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 四个模型档位的接入配置，对应 application.yaml 里的 agent.model。
 *
 * <p>每一档都是完整独立的一组配置：密钥、地址、模型名、流式、深度思考各配各的，
 * 档位之间没有任何继承或回落关系。所以强模型和快模型可以来自两个不同的账号、
 * 甚至两个不同的接入地址，互不影响。
 */
@Getter
@Setter
@ToString
@Component
@ConfigurationProperties(prefix = "agent.model")
public class ModelProperties {

    /** 快模型：最便宜最快的一档 */
    private Tier fast = new Tier();

    /** 强模型：能力最强的一档 */
    private Tier strong = new Tier();

    /** 强模型 + 深度思考 */
    private Tier strongThinking = new Tier();

    /** 稳定模型：中等档位，日常主力 */
    private Tier stable = new Tier();

    /**
     * 单档配置。
     */
    @Getter
    @Setter
    @ToString(exclude = "apiKey")
    public static class Tier {

        /** 本档的密钥，跟别的档位无关 */
        private String apiKey;

        /** 本档的接入地址。留空走 SDK 内置的百炼地址 */
        private String baseUrl;

        /** 模型名，百炼侧的准确标识。必填 */
        private String modelName;

        /**
         * 是否流式。
         * 注意跟调用方对得上：一次性调用的智能体要把 Flux 的所有片段拼起来才是完整结果，
         * 只取最后一个元素会拿到残缺的尾巴。
         */
        private boolean stream = true;

        /** 是否开启深度思考。只有支持思考的模型认这个开关，普通模型开了也没用 */
        private boolean enableThinking = false;
    }
}
