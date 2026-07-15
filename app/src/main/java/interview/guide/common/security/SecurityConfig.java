package interview.guide.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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

                // 添加 JWT 认证过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }
}
