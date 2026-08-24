package com.quanwei.gogo.agent.agent.intent.embedding;

import com.quanwei.gogo.agent.agent.intent.IntentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 向量化客户端。
 *
 * <p>自己写而不是用 AgentScope 提供的 —— 2.0.2 的 core 和 dashscope 扩展里都没有 embedding 能力，
 * 只有 ChatModel。走 OpenAI 兼容的 /embeddings 端点，百炼、DeepSeek、豆包都认这个协议。
 */
@Slf4j
@Component
public class EmbeddingClient {

    @Autowired
    private IntentProperties intentProperties;

    private RestClient restClient;

    /** 没配密钥就整体禁用 L2，不抛异常 */
    public boolean isAvailable() {
        return StringUtils.hasText(intentProperties.getL2().getEmbedding().getApiKey());
    }

    /**
     * 批量向量化。
     *
     * @return 与入参顺序一一对应的向量列表
     * @throws IllegalStateException 未配置、请求失败或返回结构不符合预期
     */
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (!isAvailable()) {
            throw new IllegalStateException("向量化模型未配置 api-key，检查 agent.intent.l2.embedding");
        }

        IntentProperties.Embedding config = intentProperties.getL2().getEmbedding();
        List<float[]> all = new ArrayList<>(texts.size());

        // 百炼单次最多 25 条，超了直接报错，所以按 batchSize 切片
        for (int from = 0; from < texts.size(); from += config.getBatchSize()) {
            int to = Math.min(from + config.getBatchSize(), texts.size());
            all.addAll(embedBatch(texts.subList(from, to), config));
        }
        return all;
    }

    /** 单条向量化 */
    public float[] embedOne(String text) {
        List<float[]> result = embed(List.of(text));
        if (result.isEmpty()) {
            throw new IllegalStateException("向量化返回为空：" + text);
        }
        return result.get(0);
    }

    @SuppressWarnings("unchecked")
    private List<float[]> embedBatch(List<String> batch, IntentProperties.Embedding config) {
        Map<String, Object> body = Map.of(
                "model", config.getModelName(),
                "input", batch,
                "dimensions", config.getDimensions(),
                "encoding_format", "float");

        Map<String, Object> response = client(config).post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + config.getApiKey())
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null || !(response.get("data") instanceof List<?> data)) {
            throw new IllegalStateException("向量化响应结构异常：" + response);
        }

        // 返回顺序不保证跟入参一致，必须按 index 排序后再取，否则整个索引的意图标签会错位
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object item : data) {
            items.add((Map<String, Object>) item);
        }
        items.sort(Comparator.comparingInt(it -> toInt(it.get("index"))));

        List<float[]> vectors = new ArrayList<>(items.size());
        for (Map<String, Object> item : items) {
            if (!(item.get("embedding") instanceof List<?> raw)) {
                throw new IllegalStateException("向量化响应缺少 embedding 字段：" + item);
            }
            float[] vector = new float[raw.size()];
            for (int i = 0; i < raw.size(); i++) {
                vector[i] = ((Number) raw.get(i)).floatValue();
            }
            vectors.add(vector);
        }

        if (vectors.size() != batch.size()) {
            throw new IllegalStateException(
                    "向量化返回数量与入参不符：期望 " + batch.size() + "，实际 " + vectors.size());
        }
        return vectors;
    }

    /** 懒加载，避免配置还没绑定完就建客户端 */
    private RestClient client(IntentProperties.Embedding config) {
        if (restClient == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            Duration timeout = Duration.ofSeconds(config.getTimeoutSeconds());
            factory.setConnectTimeout(timeout);
            factory.setReadTimeout(timeout);
            restClient = RestClient.builder()
                    .baseUrl(config.getBaseUrl())
                    .requestFactory(factory)
                    .build();
            log.info("向量化客户端就绪：{}，模型 {}，维度 {}",
                    config.getBaseUrl(), config.getModelName(), config.getDimensions());
        }
        return restClient;
    }

    private static int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    /**
     * 余弦相似度。
     * 有些模型返回的向量已经归一化，点积就够了；但不能假设，统一按标准公式算。
     */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0.0D;
        }
        double dot = 0.0D;
        double normA = 0.0D;
        double normB = 0.0D;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0D || normB == 0.0D) {
            return 0.0D;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
