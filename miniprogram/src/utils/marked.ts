/**
 * Markdown 解析工具
 * 使用 marked 库将 Markdown 转换为 HTML，并做轻量 HTML 净化（N13）
 */
import { marked } from 'marked'

// 简单 HTML 转义（自定义 renderer 输出时使用）
const escapeHtml = (s: string): string =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

// 代码块/行内代码样式：必须内联（微信端 rich-text 隔离渲染，页面 CSS 无法穿透）
// pre-wrap + break-all 让长代码行在窄屏内折行，不溢出屏幕
const CODE_BLOCK_STYLE = 'display:block;white-space:pre-wrap;word-break:break-all;overflow-wrap:break-word;background:#f6f8fa;border:1px solid #e4e7eb;border-radius:6px;padding:12px;margin:8px 0;font-size:12px;line-height:1.6;color:#1f2937;'
const CODE_INLINE_STYLE = 'background:#e8edf0;border-radius:3px;padding:1px 4px;font-size:0.9em;color:#1f4d63;'

marked.use({
  renderer: {
    // 代码块（v12+ renderer 签名接收 token 对象）
    code({ text }: { text: string }): string {
      return `<pre style="${CODE_BLOCK_STYLE}"><code>${escapeHtml(text)}</code></pre>`
    },
    // 行内代码
    codespan({ text }: { text: string }): string {
      return `<code style="${CODE_INLINE_STYLE}">${escapeHtml(text)}</code>`
    },
  },
})

// 配置 marked 选项
marked.setOptions({
  gfm: true,  // 启用 GitHub 风格的 Markdown
  breaks: true  // 转换换行符为 <br>
})

/**
 * 轻量 HTML 净化（N13）
 * marked v5 起移除了内置 sanitize，且默认会透传 markdown 中的内联 HTML。
 * rich-text 虽不执行脚本，但畸形/恶意 nodes 仍可能导致渲染异常，故对输出做净化：
 * - 移除 script/style/iframe/object/embed/link/meta 等危险标签（含其内容）
 * - 移除 on* 事件处理属性
 * - 中和 javascript:/vbscript: 协议的 href/src
 */
const sanitizeHtml = (html: string): string => {
  if (!html) return ''
  try {
    return html
      // 1. 移除成对危险标签及其内容：<script>...</script> 等
      .replace(/<(script|style|iframe|object|embed|link|meta|svg)\b[\s\S]*?<\/\1\s*>/gi, '')
      // 2. 移除未闭合/自闭合的危险标签
      .replace(/<(script|style|iframe|object|embed|link|meta)\b[^>]*>/gi, '')
      // 3. 移除事件处理属性：onclick=... / onload=... 等
      .replace(/\s+on\w+\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)/gi, '')
      // 4. 中和危险协议 URL（href/src="javascript:..."）
      .replace(/(href|src)\s*=\s*(?:"\s*(?:javascript|vbscript|data)\s*:[^"]*"|'\s*(?:javascript|vbscript|data)\s*:[^']*')/gi, '$1=""')
  } catch (e) {
    console.error('HTML 净化失败:', e)
    return html
  }
}

/**
 * 将 Markdown 文本转换为（已净化的）HTML
 * @param text Markdown 文本
 * @returns HTML 字符串
 */
export const renderMarkdown = (text: string | null | undefined): string => {
  if (!text) return ''
  try {
    const html = marked.parse(text) as string
    return sanitizeHtml(html)
  } catch (e) {
    console.error('Markdown 解析失败:', e)
    return sanitizeHtml(text)
  }
}

export { marked }
