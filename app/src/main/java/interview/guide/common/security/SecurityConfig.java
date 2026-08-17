package interview.guide.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.util.List;

/**
 * Spring Security 安全配置
 * 采用 Spring Security 6.x 的 SecurityFilterChain 方式配置
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 管理后台接口白名单，不需要 ADMIN 权限即可访问
     */
    @Value("${security.admin-whitelist:/api/admin/interviewer-roles}")
    private List<String> adminWhitelist;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * 密码编码器
     * 使用 BCrypt 加密算法
     *
     * @return PasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置安全过滤链
     *
     * @param http HttpSecurity
     * @return SecurityFilterChain
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // 禁用 CSRF（使用 JWT 无需 CSRF 防护）
                .csrf(AbstractHttpConfigurer::disable)

                // 配置 CORS
                .cors(cors -> cors.configurationSource(request -> {
                    var corsConfig = new org.springframework.web.cors.CorsConfiguration();
                    corsConfig.addAllowedOriginPattern("*");
                    corsConfig.addAllowedMethod(org.springframework.http.HttpMethod.GET);
                    corsConfig.addAllowedMethod(org.springframework.http.HttpMethod.POST);
                    corsConfig.addAllowedMethod(org.springframework.http.HttpMethod.PUT);
                    corsConfig.addAllowedMethod(org.springframework.http.HttpMethod.DELETE);
                    corsConfig.addAllowedMethod(org.springframework.http.HttpMethod.OPTIONS);
                    corsConfig.addAllowedHeader("*");
                    corsConfig.setAllowCredentials(true);
                    corsConfig.setMaxAge(3600L);
                    return corsConfig;
                }))

                // 配置请求授权规则
                .authorizeHttpRequests(authorize -> authorize
                        // 放行路径
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/resumes/health").permitAll()
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api/actuator/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/config/session").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/swagger-resources/**").permitAll()
                        .requestMatchers("/webjars/**").permitAll()
                        .requestMatchers("/doc.html").permitAll()
                        .requestMatchers("/favicon.ico").permitAll()
                        .requestMatchers("/error").permitAll()
                        // SSE stream 接口（通过 URL 参数传递 token 验证）
                        .requestMatchers("/api/interview/sessions/*/stream").permitAll()
                        // RAG chat 流式接口（permitAll，在接口内通过 @CurrentUser 校验用户）
                        .requestMatchers("/api/rag-chat/sessions/*/messages/stream").permitAll()
                        // RAG chat 会话列表和详情接口（permitAll，在接口内通过 @CurrentUser 校验用户）
                        .requestMatchers("/api/rag-chat/sessions").permitAll()
                        .requestMatchers("/api/rag-chat/sessions/**").permitAll()
                        // 管理后台接口白名单（不需要 ADMIN 权限）
                        .requestMatchers(adminWhitelist.toArray(new String[0])).permitAll()
                        // 管理后台接口需要管理员角色
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // 其他请求需要认证
                        .anyRequest().authenticated()
                )

                // 配置无状态会话管理（JWT 场景使用）
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 区分两种拒绝场景，返回标准状态码：
                // 401 未认证（无 token / token 过期无效）——JwtAuthenticationFilter 对无效 token
                //     不设认证直接放行，匿名请求在此被拒；前端（小程序/web）据 401 登出并跳转登录页
                // 403 已认证但无权限（如非 ADMIN 访问管理接口）——前端按普通错误提示
                // 不配置时 Spring Security 默认 Http403ForbiddenEntryPoint 会把未认证也返回 403，
                // 导致 token 过期后前端无法识别登录态失效（曾引发 profile 页 /users/me 403 卡死）
                .exceptionHandling(ex -> ex
                        // 认证入口点：请求"未通过认证"（无 token / token 过期无效 / 匿名访问受保护接口）时被调用。
                        // 传统表单应用在此跳转登录页；JWT 无状态场景没有页面可跳，直接写 401 JSON
                        // 让前端识别登录态失效并自行处理登出/跳转
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "{\"code\":401,\"message\":\"登录已过期或未登录，请重新登录\",\"data\":null}"))
                        // 访问拒绝处理器：请求"已通过认证但权限不足"（如 ROLE_USER 访问 /api/admin/** 的
                        // hasRole("ADMIN") 接口，抛 AccessDeniedException）时被调用，写 403 JSON。
                        // 与 401 的区别：用户身份有效，只是没资格访问该资源
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                                        "{\"code\":403,\"message\":\"无权限访问\",\"data\":null}"))
                )

                // 添加 JWT 认证过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }

    /**
     * 安全拒绝响应统一输出 JSON（与 Result 响应壳结构一致，前端可直接解析 message）
     */
    private static void writeJson(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }
}
