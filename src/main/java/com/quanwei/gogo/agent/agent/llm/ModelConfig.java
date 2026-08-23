package com.quanwei.gogo.agent.agent.llm;

import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

/**
 * 模型统一管理，四个档位各一个 bean，全部走 DashScope 原生协议。
 *
 * <p>模型名、是否流式、是否开思考、密钥、地址全部来自配置，这个类里不写死任何一个值 ——
 * 换模型、调开关只改 yaml，不用重新编译。
 *
 * <p>bean 名字就是各档位对外的标识，智能体靠 {@code @Qualifier("stableModel")} 这样按名字取。
 * 注意容器里有四个 Model 候选、其中 strongModel 标了 @Primary，而 Spring 解析多候选时
 * @Primary 的优先级高于按参数名匹配 —— 注入点漏写 @Qualifier 不会报错，会静默拿到 strongModel。
 *
 * <p>跟框架自带的 {@code io.agentscope.core.agent.config.ModelConfig} 同名但无关，
 * 那个是 (maxRetries, fallbackModel) 的重试配置，同一个文件里别把两个都 import 进来。
 */
@Slf4j
@Configuration
public class ModelConfig {

    @Autowired
    private ModelProperties modelProperties;

    /** 快模型：改写、分类、抽取这类简单任务用它，省钱也省延迟 */
    @Bean("fastModel")
    public Model fastModel() {
        return build("fast", modelProperties.getFast());
    }

    /** 强模型：复杂推理、多步规划才值得用 */
    @Bean("strongModel")
    @Primary
    public Model strongModel() {
        return build("strong", modelProperties.getStrong());
    }

    /** 强模型 + 深度思考：比 strongModel 还慢，结论正确性压倒延迟时才用 */
    @Bean("strongModelWithThinking")
    public Model strongModelWithThinking() {
        return build("strongThinking", modelProperties.getStrongThinking());
    }

    /** 稳定模型：日常主力，拿不准用哪档就用它 */
    @Bean("stableModel")
    public Model stableModel() {
        return build("stable", modelProperties.getStable());
    }

    /**
     * 按配置建一个 DashScope 模型实例。
     * 五个值全部只认本档自己的配置，档位之间不共享任何东西。
     */
    private Model build(String tierName, ModelProperties.Tier tier) {
        requireConfigured(tierName, tier);

        DashScopeChatModel.Builder builder = DashScopeChatModel.builder()
                .apiKey(tier.getApiKey())
                .modelName(tier.getModelName())
                .stream(tier.isStream())
                .enableThinking(tier.isEnableThinking());

        // 没配就别调，让 SDK 用它内置的百炼地址；显式传 null 反而可能把默认值冲掉
        if (StringUtils.hasText(tier.getBaseUrl())) {
            builder.baseUrl(tier.getBaseUrl());
        }

        log.info("模型档位 [{}] 接入 {}，流式={}，深度思考={}，地址={}",
                tierName, tier.getModelName(), tier.isStream(), tier.isEnableThinking(),
                StringUtils.hasText(tier.getBaseUrl()) ? tier.getBaseUrl() : "SDK 默认");

        return builder.build();
    }

    /**
     * 配置校验。
     *
     * <p>model-name 和 api-key 缺了都直接抛：两个都没有合理默认值，缺了建出来的模型必然调不通。
     * 让它在启动时炸掉，比上线后第一次请求才发现好。
     */
    private void requireConfigured(String tierName, ModelProperties.Tier tier) {
        if (tier == null || !StringUtils.hasText(tier.getModelName())) {
            throw new IllegalStateException(
                    "模型档位 [" + tierName + "] 缺少 model-name，检查 agent.model." + tierName);
        }
        if (!StringUtils.hasText(tier.getApiKey())) {
            throw new IllegalStateException(
                    "模型档位 [" + tierName + "] 缺少 api-key，检查 agent.model." + tierName);
        }
    }
}
