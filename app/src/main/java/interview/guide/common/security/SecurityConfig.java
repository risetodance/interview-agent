package interview.guide.common.security;

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

/**
 * Spring Security 安全配置
 * 采用 Spring Security 6.x 的 SecurityFilterChain 方式配置
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

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
                        .requestMatchers("/api/config/session").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/swagger-resources/**").permitAll()
                        .requestMatchers("/webjars/**").permitAll()
                        .requestMatchers("/doc.html").permitAll()
                        .requestMatchers("/favicon.ico").permitAll()
                        .requestMatchers("/error").permitAll()
                        // 临时允许admin接口（测试用）
                        .requestMatchers("/api/admin/**").permitAll()

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
