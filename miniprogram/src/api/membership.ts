import { get, post } from '../utils/request'

// ========== 会员相关类型 ==========

// 会员状态（对齐后端 MembershipDTO，B7）
export interface MembershipStatus {
  membership: string        // MembershipType 枚举：FREE / PREMIUM
  points: number
  resumeQuota: number
  interviewQuota: number
  aiCallQuota: number
  vipExpiryDate?: string | null
  // 派生便捷字段（供 UI 直接消费）
  isVip: boolean
  vipLevel: number
}

// VIP 套餐
export interface VipPackage {
  id: string
  name: string
  price: number
  originalPrice: number
  duration: number
  durationUnit: 'day' | 'month' | 'year'
  benefits: string[]
  isPopular?: boolean
}

// 微信支付配置
export interface WechatPaymentConfig {
  appId: string
  timeStamp: string
  nonceStr: string
  package: string
  signType: string
  paySign: string
}

// ========== 积分相关类型 ==========

// 积分信息
export interface PointsInfo {
  totalPoints: number
  availablePoints: number
  frozenPoints: number
  historyPoints: number
  rank?: number
  totalUsers?: number
}

// 积分记录（对齐后端 PointsRecordDTO，N11：移除后端不存在的 balance 字段）
export interface PointsRecord {
  id: number
  type: 'earn' | 'spend'
  amount: number
  description: string
  source: string
  sourceId?: string
  createTime: string
}

// 积分任务
export interface PointsTask {
  id: string
  name: string
  description: string
  points: number
  type: 'daily' | 'once' | 'weekly'
  isCompleted: boolean
  canClaim: boolean
  progress?: number
  maxProgress?: number
}

// ========== 积分 API ==========

/**
 * 获取积分信息
 * 包含当前积分、历史积分（从积分记录计算）
 */
export const getPointsInfo = async () => {
  try {
    // 获取当前积分
    const currentPointsData = await get<number>('/api/points')
    const currentPoints = currentPointsData || 0

    // 获取积分记录来计算历史积分
    let historyPoints = currentPoints
    try {
      const historyData = await get<any[]>('/api/points/history')
      const historyList = historyData || []
      // 计算历史获得的积分总和（只计算正向的积分）
      if (historyList.length > 0) {
        const earnedPoints = historyList
          .filter((r: any) => r.points > 0)
          .reduce((sum: number, r: any) => sum + r.points, 0)
        historyPoints = earnedPoints > 0 ? earnedPoints : currentPoints
      }
    } catch (e) {
      // 使用当前积分作为历史积分
      console.error('[getPointsInfo] 获取积分信息失败:', e)
    }

    return {
      totalPoints: currentPoints,
      availablePoints: currentPoints,
      frozenPoints: 0,
      historyPoints: historyPoints
    }
  } catch (error) {
    return {
      totalPoints: 0,
      availablePoints: 0,
      frozenPoints: 0,
      historyPoints: 0
    }
  }
}

/**
 * 获取签到状态
 */
export const getSignInStatus = async () => {
  try {
    return await get<any>('/api/points/signin/status')
  } catch (e) {
    return { signedIn: false, consecutiveDays: 0, pointsCanEarn: 10 }
  }
}

// 积分类型标签（对应后端 PointsType 枚举，N11）
const POINTS_TYPE_LABELS: Record<string, string> = {
  SIGN_IN: '每日签到',
  COMPLETE_INTERVIEW: '完成面试',
  SHARE_KB: '知识库分享',
  EXCHANGE: '积分兑换'
}

/**
 * 获取积分记录
 * 后端 PointsRecordDTO 字段：id / points(带符号) / type(PointsType) / description / createdAt（无 balance）。
 * @param page 页码（后端当前不分页，全量返回）
 * @param pageSize 每页数量
 * @param type 类型筛选：earn/spend（后端不处理，前端过滤）
 */
export const getPointsRecords = (page = 1, pageSize = 20, type?: 'earn' | 'spend') => {
  return get<any[]>('/api/points/history', {
    page,
    pageSize,
    type
  }).then(data => {
    // N11: 按积分正负判定 earn/spend（比枚举白名单更稳健，覆盖所有 PointsType）；
    // source 用后端枚举映射为中文标签；不再伪造 balance 字段。
    const list = (data || []).map((record: any) => {
      const points = Number(record.points) || 0
      return {
        id: record.id,
        type: (points >= 0 ? 'earn' : 'spend') as 'earn' | 'spend',
        amount: Math.abs(points),
        description: record.description || '',
        source: POINTS_TYPE_LABELS[record.type] || record.type || '',
        createTime: record.createdAt
      } as PointsRecord
    })
    const filtered = type ? list.filter(r => r.type === type) : list
    return {
      list: filtered,
      total: filtered.length
    }
  })
}

/**
 * 获取积分任务列表
 * 注意：签到状态由页面根据已加载的积分记录判断
 */
export const getPointsTasks = async () => {
  // 返回默认任务列表，签到状态由页面根据积分记录判断
  return Promise.resolve<PointsTask[]>([
    {
      id: '1',
      name: '每日签到',
      description: '每日签到可获得积分',
      points: 10,  // 默认值，实际值由页面根据签到状态设置
      type: 'daily',
      isCompleted: false,
      canClaim: true,
      progress: 0,
      maxProgress: 1
    },
    {
      id: '2',
      name: '完善简历',
      description: '上传并完善简历信息',
      points: 20,
      type: 'once',
      isCompleted: false,
      canClaim: false,
      progress: 0,
      maxProgress: 1
    },
    {
      id: '3',
      name: '完成一次面试',
      description: '完成一次AI模拟面试',
      points: 30,
      type: 'once',
      isCompleted: false,
      canClaim: false,
      progress: 0,
      maxProgress: 1
    }
  ])
}

/**
 * 签到（免费获取积分）
 * 后端返回 signedIn, consecutiveDays, pointsCanEarn
 * 转换为前端期望的 points, consecutiveDays 格式
 */
export const checkIn = () => {
  return post<{ points: number; consecutiveDays: number }>('/api/points/signin').then(data => ({
    points: (data as any).pointsCanEarn || 10,  // 兼容后端返回的 pointsCanEarn 字段，默认10
    consecutiveDays: (data as any).consecutiveDays || 0
  }))
}

/**
 * 领取积分任务奖励
 * 后端暂无此接口，返回mock数据
 * @param taskId 任务ID
 */
export const claimTaskReward = (taskId: string) => {
  // 后端暂无任务领取接口，返回模拟成功响应
  const taskPoints: Record<string, number> = {
    '1': 10,  // 每日签到
    '2': 20,  // 完善简历
    '3': 30   // 完成一次面试
  }
  return Promise.resolve({
    points: taskPoints[taskId] || 10
  })
}

// ========== 会员 API ==========

// TODO: 待会员中心页接入后启用（当前无调用方）
/**
 * 获取会员状态（B7：对齐后端 MembershipDTO 结构并派生 isVip/vipLevel）
 */
export const getMembershipStatus = () => {
  return get<any>('/api/membership').then((data: any) => {
    const membership: string = data?.membership || 'FREE'
    const vipExpiryDate = data?.vipExpiryDate ?? null
    // 派生：MembershipType 只有 FREE / PREMIUM；过期则视为非 VIP
    const notExpired = vipExpiryDate ? new Date(vipExpiryDate).getTime() > Date.now() : true
    const isVip = membership !== 'FREE' && notExpired
    const vipLevel = membership === 'PREMIUM' ? 1 : 0
    return {
      membership,
      points: data?.points ?? 0,
      resumeQuota: data?.resumeQuota ?? 0,
      interviewQuota: data?.interviewQuota ?? 0,
      aiCallQuota: data?.aiCallQuota ?? 0,
      vipExpiryDate,
      isVip,
      vipLevel
    } as MembershipStatus
  })
}

/**
 * 获取VIP套餐列表
 */
export const getVipPackages = () => {
  return get<VipPackage[]>('/api/membership/packages')
}

/**
 * 创建支付订单
 * 个人开发者无法使用微信支付，返回mock数据
 */
export const createPaymentOrder = (packageId: string) => {
  // 个人开发者无法使用微信支付
  return Promise.resolve({
    orderId: 'mock_order_' + Date.now(),
    paymentParams: {},
    message: '个人开发者无法使用支付功能'
  })
}

/**
 * 验证支付结果
 * 个人开发者无法使用微信支付
 */
export const verifyPayment = (orderId: string) => {
  return Promise.resolve({ success: false, message: '个人开发者无法使用支付功能' })
}
