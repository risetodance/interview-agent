// Mock 配置开关
const mockEnvValue = import.meta.env.VITE_MOCK_ENABLED;
console.log('[Mock] VITE_MOCK_ENABLED:', mockEnvValue);
export const MOCK_ENABLED = mockEnvValue === 'true';
console.log('[Mock] MOCK_ENABLED:', MOCK_ENABLED);

// Mock 数据
export const MOCK_DATA = {
  // 用户信息
  user: {
    id: 1,
    username: 'testuser',
    nickname: '测试用户',
    avatar: '',
    points: 100,
    vipLevel: 0,
    vipExpireTime: null
  },

  // 简历列表
  resumes: {
    list: [
      {
        id: 1,
        name: '测试简历',
        fileName: 'resume.pdf',
        fileUrl: 'https://example.com/resume.pdf',
        fileSize: 1024000,
        fileType: 'application/pdf',
        status: 'SUBMITTED',
        parseStatus: 'COMPLETED',
        createdAt: '2026-03-12T10:00:00Z',
        updatedAt: '2026-03-12T10:00:00Z'
      }
    ],
    total: 1,
    page: 1,
    pageSize: 10
  },

  // 面试列表
  interviews: {
    list: [
      {
        id: 1,
        title: 'Java开发工程师面试',
        type: 'practice',
        position: 'Java开发工程师',
        company: '某互联网公司',
        status: 'completed',
        score: 85,
        duration: 30,
        questionCount: 5,
        createdAt: '2026-03-12T10:00:00Z',
        updatedAt: '2026-03-12T10:30:00Z'
      },
      {
        id: 2,
        title: '前端工程师面试',
        type: 'practice',
        position: '前端工程师',
        company: '某科技公司',
        status: 'pending',
        score: 0,
        duration: 30,
        questionCount: 5,
        createdAt: '2026-03-12T12:00:00Z',
        updatedAt: '2026-03-12T12:00:00Z'
      }
    ],
    total: 2,
    page: 1,
    pageSize: 10
  },

  // 知识库列表
  knowledgebases: {
    list: [
      {
        id: 1,
        name: 'Java面试题库',
        description: 'Java核心技术面试题',
        documentCount: 50,
        status: 'COMPLETED',
        createdAt: '2026-03-12T10:00:00Z',
        updatedAt: '2026-03-12T10:00:00Z'
      }
    ],
    total: 1,
    page: 1,
    pageSize: 10
  },

  // 通知列表
  notifications: {
    list: [
      {
        id: '1',
        type: 'SYSTEM',
        title: '系统通知',
        content: '欢迎使用AI面试指南',
        isRead: false,
        createdAt: '2026-03-12T10:00:00Z'
      }
    ],
    total: 1,
    page: 1,
    pageSize: 10
  },

  // 积分信息
  points: {
    totalPoints: 100,
    availablePoints: 100,
    frozenPoints: 0,
    historyPoints: 100,
    rank: 1,
    totalUsers: 100
  },

  // 会员状态
  membership: {
    isVip: false,
    vipLevel: 0,
    vipExpireTime: null
  }
}

// 延迟模拟网络请求
const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

// Mock API 函数
export const mockApi = {
  // 模拟登录
  async login(data: { username: string; password: string }) {
    await delay(500)
    if (data.username === 'testuser' && data.password === 'test123456') {
      return {
        token: 'mock_token_' + Date.now(),
        refreshToken: 'mock_refresh_token_' + Date.now(),
        userId: 1,
        username: data.username,
        role: 'USER'
      }
    }
    throw new Error('用户名或密码错误')
  },

  // 模拟获取用户信息
  async getUserProfile() {
    await delay(300)
    return MOCK_DATA.user
  },

  // 模拟获取简历列表
  async getResumes() {
    await delay(300)
    return MOCK_DATA.resumes
  },

  // 模拟获取面试列表
  async getInterviews() {
    await delay(300)
    return MOCK_DATA.interviews
  },

  // 模拟获取面试详情
  async getInterviewDetail(id: number) {
    await delay(300)
    const interview = MOCK_DATA.interviews.list.find(i => i.id === id)
    if (interview) {
      return {
        ...interview,
        questions: [
          {
            id: 1,
            content: '请介绍一下你自己？',
            type: 'text',
            category: '自我介绍',
            difficulty: 'easy',
            orderIndex: 1,
            answerStatus: 'pending'
          },
          {
            id: 2,
            content: '请说说你的项目经验？',
            type: 'text',
            category: '项目经验',
            difficulty: 'medium',
            orderIndex: 2,
            answerStatus: 'pending'
          },
          {
            id: 3,
            content: '你为什么离职？',
            type: 'text',
            category: '职业规划',
            difficulty: 'medium',
            orderIndex: 3,
            answerStatus: 'pending'
          }
        ]
      }
    }
    throw new Error('面试不存在')
  },

  // 模拟获取知识库列表
  async getKnowledgebases() {
    await delay(300)
    return MOCK_DATA.knowledgebases
  },

  // 模拟获取通知列表
  async getNotifications() {
    await delay(300)
    return MOCK_DATA.notifications
  },

  // 模拟获取积分
  async getPoints() {
    await delay(300)
    return MOCK_DATA.points
  },

  // 模拟获取会员状态
  async getMembership() {
    await delay(300)
    return MOCK_DATA.membership
  },

  // 模拟签到
  async checkIn() {
    await delay(500)
    MOCK_DATA.points.totalPoints += 10
    MOCK_DATA.points.availablePoints += 10
    return {
      points: 10,
      consecutiveDays: 1
    }
  }
}
