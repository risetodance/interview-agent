/**
 * Markdown 解析工具
 * 使用 marked 库将 Markdown 转换为 HTML
 */
import { marked } from 'marked'

// 配置 marked 选项
marked.setOptions({
  gfm: true,  // 启用 GitHub 风格的 Markdown
  breaks: true  // 转换换行符为 <br>
})

/**
 * 将 Markdown 文本转换为 HTML
 * @param text Markdown 文本
 * @returns HTML 字符串
 */
export const renderMarkdown = (text: string | null | undefined): string => {
  if (!text) return ''
  try {
    return marked.parse(text) as string
  } catch (e) {
    console.error('Markdown 解析失败:', e)
    return text
  }
}

export { marked }