package interview.guide.common.security;

import interview.guide.common.annotation.CurrentUser;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.exception.JwtAuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * CurrentUser 参数解析器
 * 用于解析带有 @CurrentUser 注解的方法参数
 * 从 SecurityContextHolder 中获取当前登录用户的信息
 *
 * @see CurrentUser
 * @see JwtAuthenticationFilter.JwtUserDetails
 */
@Slf4j
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * SecurityContextHolder 中认证对象的 key
     */
    private static final String AUTHENTICATION_ATTRIBUTE = "SPRING_SECURITY_CONTEXT";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        // 检查参数是否带有 @CurrentUser 注解
        return parameter.hasParameterAnnotation(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {

        CurrentUser currentUserAnnotation = parameter.getParameterAnnotation(CurrentUser.class);
        boolean required = currentUserAnnotation != null && currentUserAnnotation.required();

        // 从 SecurityContextHolder 获取认证信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 如果认证为空或未通过验证
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            if (required) {
                log.warn("无法获取当前用户信息，请求未认证");
                throw JwtAuthenticationException.USER_NOT_FOUND;
            }
            return null;
        }

        // 获取用户详情
        Object principal = authentication.getPrincipal();

        // 支持 JwtUserDetails 和普通 String 用户名
        if (principal instanceof JwtAuthenticationFilter.JwtUserDetails jwtUserDetails) {
            return resolveFromJwtUserDetails(parameter, jwtUserDetails, required);
        }

        // 如果是用户名（字符串），根据参数类型返回
        if (principal instanceof String username) {
            return resolveFromUsername(parameter, username, required);
        }

        // 不支持的认证主体类型
        log.warn("不支持的认证主体类型: {}", principal.getClass().getName());
        if (required) {
            throw JwtAuthenticationException.USER_NOT_FOUND;
        }
        return null;
    }

    /**
     * 从 JwtUserDetails 解析参数
     */
    private Object resolveFromJwtUserDetails(MethodParameter parameter,
                                              JwtAuthenticationFilter.JwtUserDetails userDetails,
                                              boolean required) {
        Class<?> parameterType = parameter.getParameterType();

        if (parameterType.equals(Long.class) || parameterType.equals(long.class)) {
            return userDetails.userId();
        } else if (parameterType.equals(String.class)) {
            return userDetails.username();
        } else if (parameterType.equals(JwtAuthenticationFilter.JwtUserDetails.class)) {
            return userDetails;
        } else if (parameterType.equals(Integer.class) || parameterType.equals(int.class)) {
            // 尝试将 Long userId 转换为 Integer
            if (userDetails.userId() != null && userDetails.userId() <= Integer.MAX_VALUE) {
                return userDetails.userId().intValue();
            }
            if (required) {
                throw JwtAuthenticationException.USER_NOT_FOUND;
            }
            return null;
        }

        // 不支持的参数类型
        log.warn("不支持的 @CurrentUser 参数类型: {}", parameterType.getName());
        if (required) {
            throw JwtAuthenticationException.USER_NOT_FOUND;
        }
        return null;
    }

    /**
     * 从用户名（字符串）解析参数
     * 当使用其他认证方式时可能走到这个分支
     */
    private Object resolveFromUsername(MethodParameter parameter,
                                        String username,
                                        boolean required) {
        Class<?> parameterType = parameter.getParameterType();

        if (parameterType.equals(String.class)) {
            return username;
        }

        // 不支持的参数类型
        log.warn("不支持的 @CurrentUser 参数类型: {}", parameterType.getName());
        if (required) {
            throw JwtAuthenticationException.USER_NOT_FOUND;
        }
        return null;
    }
}
