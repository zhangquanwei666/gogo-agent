package com.quanwei.gogo.agent.agent.prompt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 提示词加载配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "agent.prompt")
public class PromptProperties {

    /**
     * 提示词文件所在位置，走 Spring 的资源协议。
     *
     * <p>{@code classpath:prompt/} —— 打进 jar，改动要重新构建，适合生产；
     * <p>{@code file:./prompt/} —— 读磁盘目录，改完重启即可生效（关掉 cache 连重启都不用），
     * 调 prompt 的时候很省事。
     */
    private String location = "classpath:prompt/";

    /**
     * 是否缓存已加载的提示词。
     * 生产开着；本地调 prompt 时关掉，改完文件刷新请求就能看到效果。
     */
    private boolean cache = true;
}
