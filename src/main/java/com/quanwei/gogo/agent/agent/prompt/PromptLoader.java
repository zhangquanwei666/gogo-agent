package com.quanwei.gogo.agent.agent.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词加载器。
 *
 * <p>提示词以纯文本文件存放，不写在 Java 代码里 —— 改一句话不该触发一次编译发版，
 * 也让不写 Java 的人（产品、运营）能直接调。
 *
 * <p>模板占位符用 <code>{{name}}</code> 形式，刻意不用 <code>${}</code>：
 * 后者会跟 Spring 的属性占位符打架，提示词里出现 <code>${</code> 时容易被误解析。
 */
@Slf4j
@Component
public class PromptLoader {

    /** 文件后缀，调用方只传名字，不用带扩展名 */
    private static final String SUFFIX = ".txt";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private PromptProperties promptProperties;

    /**
     * 按名字加载提示词原文。
     *
     * @param name 文件名去掉 .txt，例如 query-rewrite-system
     * @throws UncheckedIOException 文件不存在或读取失败时抛出。
     *         这属于配置错误，应该在启动或首次调用时就暴露，不要静默返回空字符串
     */
    public String load(String name) {
        if (!promptProperties.isCache()) {
            return doLoad(name);
        }
        return cache.computeIfAbsent(name, this::doLoad);
    }

    /**
     * 加载并渲染。
     *
     * @param variables 占位符键值，key 不带大括号
     */
    public String render(String name, Map<String, String> variables) {
        String template = load(name);
        if (variables == null || variables.isEmpty()) {
            return template;
        }

        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            // 用 Matcher.quoteReplacement 防止值里的 $ 和 \ 被当成替换语法
            result = result.replace("{{" + entry.getKey() + "}}", value);
        }
        return result;
    }

    /** 清空缓存，改完提示词文件后可以调它热更新，不用重启 */
    public void clearCache() {
        cache.clear();
        log.info("提示词缓存已清空");
    }

    private String doLoad(String name) {
        String path = promptProperties.getLocation() + name + SUFFIX;
        Resource resource = resourceLoader.getResource(path);

        if (!resource.exists()) {
            throw new UncheckedIOException(new IOException("提示词文件不存在：" + path));
        }
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            log.debug("已加载提示词 {}，长度 {}", path, content.length());
            return content;
        } catch (IOException e) {
            throw new UncheckedIOException("提示词读取失败：" + path, e);
        }
    }
}
