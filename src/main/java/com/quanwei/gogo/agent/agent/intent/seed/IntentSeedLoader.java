package com.quanwei.gogo.agent.agent.intent.seed;

import com.quanwei.gogo.agent.agent.enums.IntentCategory;
import com.quanwei.gogo.agent.agent.intent.IntentProperties;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 意图种子加载器。
 *
 * <p>启动时把 intent_seed.yml 读进内存，L1 和 L2 共用同一份数据。
 *
 * <p>校验从严：intent 写错、关键词和样本都为空，一律在启动时抛异常。
 * 这类错误如果放过去，表现是「某个意图永远识别不出来」——
 * 服务照常启动、接口照常返回，只是悄悄少了一个分类，这种故障最难查。
 */
@Slf4j
@Component
public class IntentSeedLoader {

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private IntentProperties intentProperties;

    /** 加载好的种子，按 yml 里的顺序 */
    @Getter
    private List<IntentSeed> seeds = List.of();

    @PostConstruct
    public void load() {
        String location = intentProperties.getSeedLocation();
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new UncheckedIOException(new IOException("意图种子文件不存在：" + location));
        }

        Map<String, Object> root;
        try (InputStream in = resource.getInputStream()) {
            root = new Yaml().load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("意图种子文件读取失败：" + location, e);
        }
        if (root == null || !(root.get("intents") instanceof List<?> rawList)) {
            throw new IllegalStateException("意图种子文件缺少 intents 节点：" + location);
        }

        List<IntentSeed> parsed = new ArrayList<>();
        Map<IntentCategory, Integer> seen = new LinkedHashMap<>();
        for (Object raw : rawList) {
            if (!(raw instanceof Map<?, ?> item)) {
                throw new IllegalStateException("intents 下存在非法条目：" + raw);
            }
            IntentSeed seed = parseOne(item);

            // 同一个意图配两次，后面那份会让人以为生效了其实被合并/覆盖，直接拦掉
            Integer previous = seen.put(seed.intent(), parsed.size());
            if (previous != null) {
                throw new IllegalStateException("意图 " + seed.intent().getCode() + " 在种子文件里重复定义");
            }
            parsed.add(seed);
        }

        this.seeds = List.copyOf(parsed);
        log.info("意图种子加载完成：{} 个意图，关键词组 {} 个，向量样本 {} 条，来源 {}",
                seeds.size(),
                seeds.stream().mapToInt(s -> s.keywords().size()).sum(),
                seeds.stream().mapToInt(s -> s.samples().size()).sum(),
                location);
    }

    private IntentSeed parseOne(Map<?, ?> item) {
        Object codeObj = item.get("intent");
        if (codeObj == null || codeObj.toString().isBlank()) {
            throw new IllegalStateException("种子条目缺少 intent 字段：" + item);
        }
        String code = codeObj.toString().trim();

        IntentCategory intent = IntentCategory.of(code);
        // of() 查不到会回落成 UNKNOWN，这里要把「真的写了 UNKNOWN」和「写错了」区分开
        if (intent == IntentCategory.UNKNOWN && !IntentCategory.UNKNOWN.getCode().equalsIgnoreCase(code)) {
            throw new IllegalStateException("种子文件里的意图 " + code + " 在 IntentCategory 中不存在，两边必须一致");
        }

        List<List<String>> keywords = new ArrayList<>();
        if (item.get("keywords") instanceof List<?> rawGroups) {
            for (Object group : rawGroups) {
                if (!(group instanceof List<?> words) || words.isEmpty()) {
                    throw new IllegalStateException(code + " 的 keywords 必须是非空的词组数组，实际：" + group);
                }
                List<String> one = words.stream().map(String::valueOf).map(String::trim).toList();
                keywords.add(List.copyOf(one));
            }
        }

        List<String> samples = new ArrayList<>();
        if (item.get("samples") instanceof List<?> rawSamples) {
            for (Object sample : rawSamples) {
                String text = String.valueOf(sample).trim();
                if (!text.isEmpty()) {
                    samples.add(text);
                }
            }
        }

        if (keywords.isEmpty() && samples.isEmpty()) {
            throw new IllegalStateException(code + " 的 keywords 和 samples 不能同时为空，否则这个意图永远命中不了");
        }
        return new IntentSeed(intent, keywords, samples);
    }
}
