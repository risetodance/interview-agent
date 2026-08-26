// 协议文档类型定义（入库，不含个人信息）
// 真实内容由 src/config/agreement-content.ts 提供（本地排除不入库，
// 协作者 clone 后 postinstall 自动从 agreement-content.example.ts 复制占位版）

export interface AgreementSection {
  /** 章节标题 */
  title: string
  /** 章节段落（每段一行展示） */
  paragraphs: string[]
}

export interface AgreementContent {
  /** 运营者主体名称（须与微信小程序注册主体、ICP 备案主体一致） */
  operatorName: string
  /** 联系邮箱 */
  contactEmail: string
  /** 政策更新日期 */
  lastUpdated: string
  /** 用户协议章节 */
  termsSections: AgreementSection[]
  /** 隐私政策章节 */
  privacySections: AgreementSection[]
}
