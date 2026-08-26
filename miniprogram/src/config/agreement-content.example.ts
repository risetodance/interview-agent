// ==================================================================
// 协议内容占位模板（入库，不含任何个人信息）
//
// 使用方法：首次 clone 后无需手动操作（postinstall 会自动复制本文件
// 为 agreement-content.ts）；发布前请编辑 agreement-content.ts，
// 将运营者名称、联系邮箱与各章节正文替换为真实信息。
//
// 注意：真实内容文件 agreement-content.ts 已被本地排除不入库，
// 换电脑构建时需手动携带该文件。
// ==================================================================

import type { AgreementContent } from './agreement-types'

export const agreementContent: AgreementContent = {
  operatorName: '你的名称（与小程序注册主体一致）',
  contactEmail: 'contact@example.com',
  lastUpdated: '2026年1月1日',
  termsSections: [
    {
      title: '协议的接受',
      paragraphs: ['（占位内容）此处填写用户协议正文，请复制本文件为 agreement-content.ts 后替换。']
    }
  ],
  privacySections: [
    {
      title: '引言',
      paragraphs: ['（占位内容）此处填写隐私政策正文，请复制本文件为 agreement-content.ts 后替换。']
    }
  ]
}
