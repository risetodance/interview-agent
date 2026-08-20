/**
 * 与后端 @Email 校验对齐的宽松正则（非空白字符 + 单 @ + 域名后缀）
 */
export const isValidEmail = (value: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
