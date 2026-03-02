package interview.guide.common.annotation;

import interview.guide.common.security.CurrentUserArgumentResolver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 获取当前登录用户注解
 * 用于方法参数上，自动注入当前登录用户的信息
 *
 * @see CurrentUserArgumentResolver
 * @see interview.guide.common.security.JwtAuthenticationFilter.JwtUserDetails
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {

    /**
     * 是否必须
     * 如果为 true，当未获取到用户信息时抛出异常
     * 如果为 false，当未获取到用户信息时返回 null
     *
     * @return true: 必须, false: 可选
     */
    boolean required() default true;
}
