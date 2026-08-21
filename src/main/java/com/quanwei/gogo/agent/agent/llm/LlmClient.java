package com.quanwei.gogo.agent.agent.llm;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 对 AgentScope {@link Model} 的薄封装。
 *
 * <p>存在的意义是把三件事收口，别让每个智能体各写一遍：
 * <ul>
 *   <li>Flux 转同步 —— AgentScope 的 Model 只有流式接口，非流式场景要自己收敛；</li>
 *   <li>从 ContentBlock 列表里抽纯文本；</li>
 *   <li>统一超时。</li>
 * </ul>
 *
 * <p>同时也是一层隔离：所有智能体只依赖这个类，
 * 将来换模型框架或换厂商，改动不外溢到智能体代码。
 */
@Slf4j
@Component
public class LlmClient {

    /** 由 agentscope-openai-spring-boot-starter 自动装配 */
    @Autowired
    private Model model;

    /**
     * 一次性调用，拿完整文本。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户输入
     * @param temperature  采样温度，改写/抽取这类确定性任务传 0
     * @param timeout      超时时间，超时抛异常由调用方兜
     * @return 模型返回的纯文本，已 trim
     */
    public String complete(String systemPrompt, String userPrompt, double temperature, Duration timeout) {
        List<Msg> messages = List.of(
                Msg.builder()
                        .role(MsgRole.SYSTEM)
                        .content(TextBlock.builder().text(systemPrompt).build())
                        .build(),
                Msg.builder()
                        .role(MsgRole.USER)
                        .content(TextBlock.builder().text(userPrompt).build())
                        .build());

        GenerateOptions options = GenerateOptions.builder()
                // 关掉流式，Flux 只发一个完整响应，blockLast 拿到的就是全量结果。
                // 开着流式时每个元素是增量片段，还要自己拼，这里没必要
                .stream(Boolean.FALSE)
                .temperature(temperature)
                .build();

        ChatResponse response = model.stream(messages, List.of(), options)
                .blockLast(timeout);

        if (response == null) {
            throw new IllegalStateException("模型没有返回任何响应");
        }
        return extractText(response);
    }

    /** 从响应的 ContentBlock 列表里把 TextBlock 拼成纯文本 */
    private String extractText(ChatResponse response) {
        List<ContentBlock> blocks = response.getContent();
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (block instanceof TextBlock textBlock && textBlock.getText() != null) {
                sb.append(textBlock.getText());
            }
        }
        return sb.toString().trim();
    }
}
