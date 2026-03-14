import { get, post } from '../utils/request'

// ========== 会员相关类型 ==========

// 会员状态
export interface MembershipStatus {
  isVip: boolean
  vipLevel: number
  vipExpireTime?: string
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

// 积分记录
export interface PointsRecord {
  id: number
  type: 'earn' | 'spend'
  amount: number
  balance: number
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
      console.error('获取积分历史失败:', e)
      // 使用当前积分作为历史积分
    }

    return {
      totalPoints: currentPoints,
      availablePoints: currentPoints,
      frozenPoints: 0,
      historyPoints: historyPoints,
      rank: null
    }
  } catch (error) {
    console.error('获取积分信息失败:', error)
    return {
      totalPoints: 0,
      availablePoints: 0,
      frozenPoints: 0,
      historyPoints: 0,
      rank: null
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
    console.error('获取签到状态失败:', e)
    return { signedIn: false, consecutiveDays: 0, pointsCanEarn: 10 }
  }
}

/**
 * 获取积分记录
 * @param page 页码
 * @param pageSize 每页数量
 * @param type 类型筛选：earn/spend
 */
export const getPointsRecords = (page = 1, pageSize = 20, type?: 'earn' | 'spend') => {
  return get<any[]>('/api/points/history', {
    page,
    pageSize,
    type
  }).then(data => ({
    list: (data || []).map((record: any) => ({
      id: record.id,
      type: record.type === 'SIGN_IN' || record.type === 'INTERVIEW_COMPLETE' ? 'earn' : 'spend',
      amount: Math.abs(record.points || 0),
      balance: record.points,  // 积分变化值
      source: record.description || '',
      createTime: record.createdAt
    })),
    total: data?.length || 0
  }))
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

/**
 * 获取会员状态
 */
export const getMembershipStatus = () => {
  return get<MembershipStatus>('/api/membership')
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
