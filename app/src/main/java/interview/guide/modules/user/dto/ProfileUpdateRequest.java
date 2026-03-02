package interview.guide.modules.user.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新个人资料请求
 */
public record ProfileUpdateRequest(
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    String nickname,

    @Size(max = 500, message = "头像URL长度不能超过500个字符")
    String avatar
) {}
