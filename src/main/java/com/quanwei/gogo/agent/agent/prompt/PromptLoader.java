package com.quanwei.gogo.agent.agent.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词加载器。
 *
 * <p>提示词以文件存放，不写在 Java 代码里 —— 改一句话不该触发一次编译发版，
 * 也让不写 Java 的人（产品、运营）能直接调。
 *
 * <p>三件事：
 * <ol>
 *   <li>读文件（位置和缓存策略由 agent.prompt.* 控制，本地可切 file: 热调）；</li>
 *   <li>展开 <code>{{include:fragments/xxx.md}}</code> 片段引用，让多个智能体复用同一段规则；</li>
 *   <li>注入时间变量 <code>{{current_date}}</code> / <code>{{current_weekday}}</code> / <code>{{current_time}}</code>。</li>
 * </ol>
 *
 * <p>占位符统一用 <code>{{name}}</code>，刻意不用 <code>${}</code>：
 * 后者会跟 Spring 的属性占位符打架，提示词里出现 <code>${</code> 时容易被误解析。
 *
 * <p>文件名要带扩展名，例如 query-rewriting-agent-system.md。
 */
@Slf4j
@Component
public class PromptLoader {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 片段引用指令，形如 <code>{{include: fragments/time-rules.md}}</code> */
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("\\{\\{include:\\s*([\\w\\-./]+)\\s*}}");

    /** 片段嵌套展开的最大层数，超过一律视为配置写错了 */
    private static final int MAX_INCLUDE_DEPTH = 5;

    /** loadStatic 时给 current_time 的占位说明，提醒模型真实时间在后面 */
    private static final String TIME_PLACEHOLDER = "（见下方动态注入）";

    /**
     * 缓存的是「读文件 + 展开片段」之后的模板，不含变量替换。
     * 分这一层是因为变量里有时间，整体缓存会把时间冻住；
     * 而真正费时的磁盘 IO 和正则展开又完全可以复用。
     */
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private PromptProperties promptProperties;

    /**
     * 加载并注入全部时间变量，含精确到秒的 current_time。
     *
     * <p>适合每次调用都重新加载的场景。如果加载结果要存进字段长期持有，
     * 用 {@link #loadStatic(String)}，否则 current_time 会冻在加载那一刻。
     */
    public String load(String filename) {
        return render(filename, LocalTime.now().format(TIME_FORMATTER));
    }

    /**
     * 加载但不注入实时时分秒，只注入 current_date 和 current_weekday（一天只变一次）。
     *
     * <p>这么拆是为了让 system prompt 在同一天内保持字节级稳定 ——
     * 百炼的隐式缓存按前缀匹配，前缀一致的部分只按 20% 计费。
     * 把每秒都在变的时间掺进 system prompt，等于每次请求都刷掉整个缓存。
     *
     * <p>需要精确到分秒时，用 {@link #buildTimeContext()} 拼一条独立的 system 消息，
     * 排在主 system prompt 之后 —— 放在后面就不影响前缀匹配。
     */
    public String loadStatic(String filename) {
        return render(filename, TIME_PLACEHOLDER);
    }

    /**
     * 动态时间上下文，作为独立的 system 消息追加在主 system prompt 之后。
     * 跟 {@link #loadStatic(String)} 配套使用。
     */
    public String buildTimeContext() {
        return "当前时间：" + LocalTime.now().format(TIME_FORMATTER);
    }

    /** 清空缓存，改完提示词文件后可以调它热更新，不用重启 */
    public void clearCache() {
        templateCache.clear();
        log.info("提示词缓存已清空");
    }

    /**
     * 展开片段之后注入变量。
     *
     * <p>直接注入权威的「星期几」而不是让模型从日期自己推算 ——
     * 差旅场景里「下周一」「本周」这类表述全靠星期锚定，模型推错星期整条链路就歪了。
     */
    private String render(String filename, String currentTime) {
        String template = loadTemplate(filename);
        LocalDate today = LocalDate.now();
        String weekday = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
        return template
                .replace("{{current_date}}", today.format(DATE_FORMATTER))
                .replace("{{current_weekday}}", weekday)
                .replace("{{current_time}}", currentTime);
    }

    /** 读文件 + 展开片段。这一步的结果与时间无关，可以缓存 */
    private String loadTemplate(String filename) {
        if (!promptProperties.isCache()) {
            return doLoadTemplate(filename);
        }
        return templateCache.computeIfAbsent(filename, this::doLoadTemplate);
    }

    private String doLoadTemplate(String filename) {
        String content = readResource(filename);
        // 先展开片段再替换变量，这样片段内部的占位符同样生效
        return expandIncludes(content, new LinkedHashSet<>(Set.of(filename)), 0);
    }

    /**
     * 递归展开片段引用。
     *
     * @param chain 当前引用链，从主提示词开始，用于环形引用检测和报错定位
     * @param depth 当前嵌套层数
     * @throws IllegalStateException 片段不存在/为空、环形引用、或嵌套超过上限
     */
    private String expandIncludes(String content, Set<String> chain, int depth) {
        if (depth > MAX_INCLUDE_DEPTH) {
            throw new IllegalStateException("提示词片段嵌套超过 " + MAX_INCLUDE_DEPTH
                    + " 层，引用链：" + String.join(" -> ", chain));
        }

        Matcher matcher = INCLUDE_PATTERN.matcher(content);
        StringBuilder sb = new StringBuilder();
        boolean matched = false;
        while (matcher.find()) {
            matched = true;
            String path = matcher.group(1);
            if (chain.contains(path)) {
                throw new IllegalStateException("提示词片段存在环形引用："
                        + String.join(" -> ", chain) + " -> " + path);
            }

            String fragment = readResource(path);
            if (fragment.isBlank()) {
                throw new IllegalStateException("提示词片段为空：" + path
                        + "（引用链：" + String.join(" -> ", chain) + "）");
            }

            Set<String> nested = new LinkedHashSet<>(chain);
            nested.add(path);
            String expanded = expandIncludes(fragment.strip(), nested, depth + 1);
            // quoteReplacement 防止片段里的 $ 和 \ 被当成替换语法
            matcher.appendReplacement(sb, Matcher.quoteReplacement(expanded));
        }

        if (!matched) {
            return content;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 读一个提示词文件。
     *
     * @throws UncheckedIOException 文件不存在或读取失败。
     *         这属于配置错误，必须立刻炸出来 —— 静默返回一句兜底提示词的话，
     *         服务会照常启动、照常响应，只是所有规则都悄悄失效了，这种故障最难查
     */
    private String readResource(String filename) {
        String path = promptProperties.getLocation() + filename;
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
