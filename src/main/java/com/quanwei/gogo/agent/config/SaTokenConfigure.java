package com.quanwei.gogo.agent.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局鉴权拦截。
 *
 * <p>策略是「默认拦截，白名单放行」——新加的接口不写任何鉴权代码也是安全的，
 * 而不是反过来忘了写就裸奔。
 *
 * <p>拦截范围只有 {@code /api/**}，前端静态资源（/、/assets/**、/favicon.svg 等）
 * 走的是另一套路径，天然不受影响，不用再维护一份静态资源白名单。
 *
 * <p>注册这个拦截器之后，{@code @SaCheckLogin}、{@code @SaCheckRole} 这类注解才会生效，
 * 它们本身不做拦截，要靠拦截器驱动。
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    /** 拦截范围：只管接口，不管静态资源 */
    private static final String API_PATTERN = "/api/**";

    /**
     * 无需登录即可访问的接口，逐个列举、不用通配符。
     *
     * <p>用 {@code /api/v1/auth/**} 整段放行更省事，但以后往 auth 这组里新增接口
     * （重置密码、登录设备列表之类）会自动继承免鉴权，且没有任何提示。
     * 逐个列举的代价是加公开接口时要记得来这儿补一行 ——
     * 忘了补的后果是接口被拦（马上能发现），比忘了收紧导致接口裸奔安全得多。
     */
    private static final String[] WHITE_LIST = {
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            // 登出放行是为了让 token 已过期的用户也能正常调用，避免前端卡在中间态
            "/api/v1/auth/logout",
            "/error",
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> SaRouter
                        .match(API_PATTERN)
                        .notMatch(WHITE_LIST)
                        .check(r -> StpUtil.checkLogin())))
                .addPathPatterns(API_PATTERN);
    }
}
