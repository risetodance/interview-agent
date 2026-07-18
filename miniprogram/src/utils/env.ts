/**
 * 统一环境与基础地址配置
 *
 * 背景（D2 / N6 / B3）：
 * - `baseURL` 拼接逻辑（读 `VITE_API_BASE_URL` 后去掉 `/api` 尾）此前在 request.ts /
 *   knowledgebase.ts / resume.ts / mock.ts 等 5 处重复，interview.ts 正是因单独使用
 *   `process.env` 漂移而触发 B1。统一收敛到此模块，避免再次漂移。
 * - `isH5` 此前有三种写法并存：user.ts / notification.ts 用 `!uni.getSystemInfoSync`
 *   恒为 false（死代码），login.vue / resume.ts 用 `typeof window`。统一为基于 `window`
 *   的运行时判定（H5 有 window，小程序运行环境无 window）。
 *
 * 注意：不要改用 uni-app 条件编译（`#ifdef H5`）来定义本常量——vue-tsc 不识别条件编译
 * 注释，会把两处声明都编译进而报 "Cannot redeclare block-scoped variable"。
 */

/**
 * API 基础地址：读取 `VITE_API_BASE_URL` 并去掉 `/api` 尾巴
 * （各业务模块自带 `/api` 前缀，故此处去尾；缺省回退到生产域名）。
 */
export const apiBaseUrl: string =
  import.meta.env.VITE_API_BASE_URL?.replace(/\/api$/, '') || 'https://api.interview-guide.com'

/**
 * 是否运行在 H5 环境（浏览器）。
 * - H5：浏览器存在 `window` 对象 → true
 * - 微信小程序：无 `window` → false
 */
export const isH5: boolean = typeof window !== 'undefined'
