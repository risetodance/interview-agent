<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import Icon from '../../components/common/Icon.vue'
import { marked } from '../../utils/marked'
import {
  connectRagChatStream,
  getRagSessions,
  getRagMessages,
  createRagSession,
  deleteRagSession,
  updateRagSessionTitle,
  toggleRagSessionPin,
  type ChatMessage
} from '../../api/knowledgebase'

// 路由参数 - 支持多个知识库
const knowledgebaseIds = ref<number[]>([])
const knowledgebaseNames = ref<string[]>([])

// 计算属性：获取主要的知识库名称（用于显示）
const primaryKnowledgeBaseName = computed(() => {
  if (knowledgebaseNames.value.length > 0) {
    return knowledgebaseNames.value[0]
  }
  return '知识库问答'
})

// 计算属性：获取已选知识库数量
const selectedKbCount = computed(() => knowledgebaseIds.value.length)

// 计算属性：获取知识库名称描述
const knowledgeBasesDescription = computed(() => {
  if (knowledgebaseNames.value.length === 0) {
    return '未选择知识库'
  }
  if (knowledgebaseNames.value.length === 1) {
    return knowledgebaseNames.value[0]
  }
  return `${knowledgebaseNames.value.length} 个知识库`
})

// 消息列表
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const isLoading = ref(false)
const scrollTop = ref(99999)

// 会话相关
const sessions = ref<Array<{ id: number; title: string; isPinned: boolean; updatedAt: string }>>([])
const currentSessionId = ref<number | null>(null)
const showSessionList = ref(false)
const loadingSessions = ref(false)

// 是否为新建会话模式（不复用已有会话）
const isNewSessionMode = ref(false)

// 会话编辑相关
const editingSessionId = ref<number | null>(null)
const editingSessionTitle = ref('')

// 消息列表
const messageListRef = ref<any>(null)

// SSE 流清理函数
const streamCleanup = ref<(() => void) | null>(null)

// Think 块数据结构
interface ThinkBlock {
  content: string
  isComplete: boolean
}

// 展开的 think 状态集合
const expandedThinks = ref<Set<string>>(new Set())

// 解析 think 标签（支持流式传输）
// 返回: main=主内容, thinks=完成的 think 块, streamingThink=未完成的 think 内容
const parseThinkBlocks = (content: string): { main: string; thinks: ThinkBlock[]; streamingThink: string | null } => {
  const thinks: ThinkBlock[] = []
  let streamingThink: string | null = null

  // 查找所有完整的 think 块
  const completeRegex = /<think>([\s\S]*?)<\/think>/g
  let match
  let mainContent = content

  while ((match = completeRegex.exec(content)) !== null) {
    thinks.push({ content: match[1].trim(), isComplete: true })
  }

  // 检查是否有未闭合的 think 标签
  const openTag = '<think>'
  const closeTag = '</think>'
  const openIndex = content.indexOf(openTag)

  if (openIndex !== -1) {
    const closeIndex = content.lastIndexOf(closeTag)
    if (closeIndex === -1 || closeIndex < openIndex) {
      // 没有闭标签或闭标签在开标签之前，属于流式传输中的 think
      streamingThink = content.slice(openIndex + openTag.length)
      mainContent = content.slice(0, openIndex)
    } else {
      // 有闭标签，主内容是闭标签之后的部分
      mainContent = content.slice(closeIndex + closeTag.length)
    }
  } else if (thinks.length === 0) {
    // 没有 think 标签
    mainContent = content
  }

  return {
    main: mainContent.trim(),
    thinks,
    streamingThink: streamingThink ? streamingThink.trim() : null,
  }
}

// 切换 think 展开状态
const toggleThink = (msgIndex: number, thinkIndex: number) => {
  const key = `${msgIndex}-${thinkIndex}`
  if (expandedThinks.value.has(key)) {
    expandedThinks.value.delete(key)
  } else {
    expandedThinks.value.add(key)
  }
}

// 检查 think 是否展开（isLastMessage 用于新消息默认展开）
const isThinkExpanded = (msgIndex: number, thinkIndex: number, isLastMessage: boolean): boolean => {
  const key = `${msgIndex}-${thinkIndex}`
  // 新消息的 think 默认展开
  return isLastMessage || expandedThinks.value.has(key)
}

// 获取 think 块的快捷方法（供模板使用）
const getThinkBlocks = (content: string) => {
  return parseThinkBlocks(content)
}

// 获取页面参数
onMounted(() => {
  // #ifdef H5
  // H5 模式下从 URL 获取参数
  console.log('H5 URL:', window.location.href)
  console.log('H5 hash:', window.location.hash)
  const hash = window.location.hash
  const queryIndex = hash.indexOf('?')
  const queryString = queryIndex !== -1 ? hash.slice(queryIndex + 1) : ''
  console.log('Query string:', queryString)
  const urlParams = new URLSearchParams(queryString)

  // 支持多个知识库：ids=1,2,3&names=名称1,名称2,名称3
  const idsStr = urlParams.get('ids') || urlParams.get('id') || ''
  const namesStr = urlParams.get('names') || urlParams.get('name') || ''

  if (idsStr) {
    knowledgebaseIds.value = idsStr.split(',').map(id => Number(id.trim())).filter(id => !isNaN(id))
  }
  if (namesStr) {
    knowledgebaseNames.value = namesStr.split(',').map(name => decodeURIComponent(name.trim()))
  }
  // #endif

  // #ifndef H5
  // 小程序模式下从页面栈获取
  const pages = getCurrentPages()
  const page = pages[pages.length - 1] as any
  const options = page?.options || {}

  const idsStr = options.ids || options.id || ''
  const namesStr = options.names || options.name || ''

  if (idsStr) {
    knowledgebaseIds.value = idsStr.split(',').map((id: string) => Number(id.trim())).filter((id: number) => !isNaN(id))
  }
  if (namesStr) {
    knowledgebaseNames.value = namesStr.split(',').map((name: string) => decodeURIComponent(name.trim()))
  }
  // #endif

  console.log('知识库IDs:', knowledgebaseIds.value, '名称:', knowledgebaseNames.value)

  // 不自动创建会话，等待用户发消息时才创建
  // 添加欢迎消息
  addWelcomeMessage()

  // 加载会话列表
  loadSessions()
})

// 初始化会话
const initSession = async () => {
  if (knowledgebaseIds.value.length === 0) return

  try {
    // 先检查是否有该知识库的已有会话
    const sessionList = await getRagSessions() || []
    // sessionList 中的项目有 knowledgeBaseNames 字段
    const existingSession = sessionList.find((s: any) =>
      s.knowledgeBaseNames?.some((name: string) => knowledgebaseNames.value.includes(name))
    )

    if (existingSession) {
      // 复用已有会话
      currentSessionId.value = existingSession.id
      console.log('复用已有会话, sessionId:', currentSessionId.value)

      // 加载会话消息历史
      await loadSessionMessages(existingSession.id)
    } else {
      // 创建新会话，关联到当前知识库
      const session = await createRagSession(knowledgebaseIds.value)
      currentSessionId.value = session.id
      console.log('创建新会话, sessionId:', currentSessionId.value)
    }
  } catch (error) {
    console.error('初始化会话失败:', error)
    // 出错时创建新会话
    try {
      const session = await createRagSession(knowledgebaseIds.value)
      currentSessionId.value = session.id
      console.log('出错后创建新会话, sessionId:', currentSessionId.value)
    } catch (createError) {
      console.error('创建会话也失败:', createError)
    }
  }
}

// 加载会话消息历史
const loadSessionMessages = async (sessionId: number) => {
  try {
    const data = await getRagMessages(sessionId) as any
    // data 是 SessionDetailDTO，包含 messages 数组
    if (data && data.messages && data.messages.length > 0) {
      // 转换为前端格式，并解析 Markdown
      messages.value = data.messages.map((m: any) => ({
        id: m.id || Date.now() + Math.random(),
        type: m.type === 'user' ? 'question' : 'answer',
        content: parseContent(m.content || ''),
        timestamp: m.createdAt || new Date().toISOString()
      }))
    } else {
      // 没有历史消息，添加欢迎消息
      addWelcomeMessage()
    }
  } catch (error) {
    console.error('加载会话消息失败:', error)
    addWelcomeMessage()
  }
}

// 加载会话列表
const loadSessions = async () => {
  loadingSessions.value = true
  try {
    const data = await getRagSessions()
    sessions.value = data || []
  } catch (error) {
    console.error('加载会话列表失败:', error)
  } finally {
    loadingSessions.value = false
  }
}

// 添加欢迎消息
const addWelcomeMessage = () => {
  const welcomeContent = `欢迎使用知识库问答！我是 AI 助手，可以根据「${knowledgeBasesDescription.value}」中的内容回答你的问题。\n\n请输入你的问题，我会从知识库中检索相关信息并为你解答。`
  messages.value = [{
    id: Date.now(),
    type: 'answer',
    content: parseContent(welcomeContent),
    timestamp: new Date().toISOString()
  }]
}

// 发送消息
const sendMessage = async () => {
  console.log('sendMessage called, sessionId:', currentSessionId.value)
  console.log('inputText:', inputText.value)
  console.log('isLoading:', isLoading.value)

  if (!inputText.value.trim() || isLoading.value) {
    console.log('Early return: empty or loading')
    return
  }

  const question = inputText.value.trim()
  inputText.value = ''

  // 如果没有会话，先创建
  if (!currentSessionId.value) {
    try {
      // 检查是否有该知识库的已有会话（新建会话模式下跳过）
      if (!isNewSessionMode.value) {
        const sessionList = await getRagSessions() || []
        const existingSession = sessionList.find((s: any) =>
          s.knowledgeBaseNames?.some((name: string) => knowledgebaseNames.value.includes(name))
        )

        if (existingSession) {
          currentSessionId.value = existingSession.id
          console.log('复用已有会话, sessionId:', currentSessionId.value)
          // 加载已有会话的消息历史
          await loadSessionMessages(existingSession.id)
          // 重置新建会话模式标志
          isNewSessionMode.value = false
          return
        }
      }

      // 创建新会话，关联到当前知识库
      const session = await createRagSession(knowledgebaseIds.value)
      currentSessionId.value = session.id
      console.log('创建新会话, sessionId:', currentSessionId.value)
      // 重置新建会话模式标志
      isNewSessionMode.value = false
    } catch (error) {
      console.error('初始化会话失败:', error)
      uni.showToast({ title: '会话创建失败', icon: 'none' })
      return
    }
  }

  // 添加用户消息
  addUserMessage(question)

  // 发送请求
  await sendToAI(question)
}

// 添加用户消息
const addUserMessage = (content: string) => {
  messages.value.push({
    id: Date.now(),
    type: 'question',
    content,
    timestamp: new Date().toISOString()
  })

  scrollToBottom()
}

// 发送消息到 AI
const sendToAI = async (question: string) => {
  isLoading.value = true

  // 添加loading消息
  const loadingId = Date.now()
  messages.value.push({
    id: loadingId,
    type: 'answer',
    content: '',
    timestamp: new Date().toISOString()
  })

  // 添加思考中状态
  const thinkingIndex = messages.value.length - 1
  messages.value[thinkingIndex].content = '正在思考中...'

  // 保存当前的回复内容（原始markdown）
  let fullContent = ''

  // 建立 SSE 流式连接
  const cleanup = connectRagChatStream(
    currentSessionId.value!,
    question,
    {
      onConnected: () => {
        console.log('SSE 连接成功')
      },
      onMessage: (content: string) => {
        fullContent += content
        // 实时更新消息内容 - 解析 Markdown 后显示
        messages.value[thinkingIndex].content = parseContent(fullContent) || '正在思考中...'
        scrollToBottom()
      },
      onComplete: () => {
        // 更新loading消息为AI回复
        const loadingIndex = messages.value.findIndex(m => m.id === loadingId)
        if (loadingIndex !== -1) {
          messages.value[loadingIndex] = {
            id: loadingId,
            type: 'answer',
            content: parseContent(fullContent) || '抱歉，未收到有效回复。',
            timestamp: new Date().toISOString()
          }
        }
        isLoading.value = false
        scrollToBottom()
      },
      onError: (error: string) => {
        console.error('SSE 错误:', error)
        const loadingIndex = messages.value.findIndex(m => m.id === loadingId)
        if (loadingIndex !== -1) {
          messages.value[loadingIndex] = {
            id: loadingId,
            type: 'answer',
            content: `抱歉，发生了错误：${error}`,
            timestamp: new Date().toISOString()
          }
        }
        isLoading.value = false
        scrollToBottom()
      }
    }
  )

  // 保存 cleanup 函数以便后续调用
  streamCleanup.value = cleanup

  // 注意：不需要在这里保存 cleanup，因为 SSE 会在 onComplete 时自动结束
  // 如果需要在组件卸载时强制关闭，可以保存并在 onUnmounted 中调用
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    scrollTop.value = 99999
  })
}

// 格式化时间
const formatTime = (timestamp: string): string => {
  const date = new Date(timestamp)
  return `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
}

// 格式化时间Ago
const formatTimeAgo = (dateStr: string): string => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins}分钟前`
  if (diffHours < 24) return `${diffHours}小时前`
  if (diffDays < 7) return `${diffDays}天前`
  return formatTime(date.toISOString())
}

// 解析 Markdown 内容
const parseContent = (content: string) => {
  try {
    return marked(content)
  } catch {
    return content
  }
}

// 返回列表
const goBack = () => {
  uni.navigateBack()
}

// 新建会话
const handleNewSession = () => {
  messages.value = []
  currentSessionId.value = null
  showSessionList.value = false
  // 标记为新建会话模式，不复用已有会话
  isNewSessionMode.value = true
  addWelcomeMessage()
}

// 选择会话
const handleSelectSession = async (session: { id: number }) => {
  currentSessionId.value = session.id
  showSessionList.value = false

  // 加载会话消息
  try {
    const data = await getRagMessages(session.id) as any
    // 后端返回的 messages 是 MessageDTO 数组
    if (data && data.messages && data.messages.length > 0) {
      messages.value = data.messages.map((m: any) => ({
        id: m.id || Date.now() + Math.random(),
        type: m.type === 'user' ? 'question' : 'answer',
        content: parseContent(m.content || ''),
        timestamp: m.createdAt || new Date().toISOString()
      }))
    } else {
      addWelcomeMessage()
    }
  } catch (error) {
    console.error('加载会话消息失败:', error)
    addWelcomeMessage()
  }
}

// 开始编辑会话标题
const startEditSession = (session: { id: number; title: string }) => {
  editingSessionId.value = session.id
  editingSessionTitle.value = session.title
}

// 取消编辑会话标题
const cancelEditSession = () => {
  editingSessionId.value = null
  editingSessionTitle.value = ''
}

// 保存会话标题
const handleSaveSessionTitle = async () => {
  if (!editingSessionId.value || !editingSessionTitle.value.trim()) return

  try {
    await updateRagSessionTitle(editingSessionId.value, editingSessionTitle.value.trim())
    const session = sessions.value.find(s => s.id === editingSessionId.value)
    if (session) {
      session.title = editingSessionTitle.value.trim()
    }
    cancelEditSession()
  } catch (error) {
    uni.showToast({ title: '保存失败', icon: 'none' })
  }
}

// 切换置顶
const handleTogglePin = async (session: { id: number; isPinned: boolean }) => {
  try {
    await toggleRagSessionPin(session.id, !session.isPinned)
    session.isPinned = !session.isPinned
  } catch (error) {
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

// 删除会话
const handleDeleteSession = async (session: { id: number }) => {
  uni.showModal({
    title: '确认删除',
    content: '确定要删除这个会话吗？',
    success: async (res) => {
      if (res.confirm) {
        try {
          await deleteRagSession(session.id)
          sessions.value = sessions.value.filter(s => s.id !== session.id)
          if (currentSessionId.value === session.id) {
            handleNewSession()
          }
          uni.showToast({ title: '删除成功', icon: 'success' })
        } catch (error) {
          uni.showToast({ title: '删除失败', icon: 'none' })
        }
      }
    }
  })
}

// 组件卸载时清理
onUnmounted(() => {
  // 关闭 SSE 连接
  if (streamCleanup.value) {
    streamCleanup.value()
    streamCleanup.value = null
  }
})
</script>

<template>
  <view class="chat-container">
    <!-- 顶部导航 -->
    <view class="chat-header">
      <view class="header-left" @click="goBack">
        <Icon name="arrow-left" :size="24" color="#333" />
      </view>
      <view class="header-center" @click="showSessionList = true">
        <text class="chat-title">{{ primaryKnowledgeBaseName }}</text>
        <text v-if="selectedKbCount > 1" class="chat-kb-count">{{ selectedKbCount }} 个知识库</text>
        <text class="chat-subtitle">点击查看会话历史</text>
      </view>
      <view class="header-right" @click="handleNewSession">
        <text class="new-chat-icon">+</text>
      </view>
    </view>

    <!-- 消息列表 -->
    <scroll-view
      class="message-list"
      scroll-y
      :scroll-top="scrollTop"
      :refresher-enabled="false"
    >
      <view
        v-for="(msg, index) in messages"
        :key="msg.id"
        class="message-item"
        :class="msg.type"
      >
        <!-- AI 回答 -->
        <view v-if="msg.type === 'answer'" class="answer-bubble">
          <view class="ai-avatar">
            <Icon name="bot" :size="28" color="#fff" />
          </view>
          <view class="bubble-content">
            <!-- 思考过程区域 -->
            <view v-if="getThinkBlocks(msg.content).thinks.length > 0 || getThinkBlocks(msg.content).streamingThink" class="think-section">
              <!-- 完成的 think 块 -->
              <view v-for="(think, i) in getThinkBlocks(msg.content).thinks" :key="i" class="think-block">
                <view class="think-header" @click="toggleThink(index, i)">
                  <view class="think-title">
                    <Icon name="brain" :size="14" color="#0ea5e9" />
                    <text>AI 思考过程</text>
                  </view>
                  <Icon :name="isThinkExpanded(index, i, index === messages.length - 1) ? 'chevron-up' : 'chevron-down'" :size="14" color="#94a3b8" />
                </view>
                <view v-if="isThinkExpanded(index, i, index === messages.length - 1)" class="think-content">
                  <rich-text :nodes="parseContent(think.content)" />
                </view>
              </view>
              <!-- 流式传输中的 think -->
              <view v-if="getThinkBlocks(msg.content).streamingThink" class="think-block think-streaming">
                <view class="think-header">
                  <view class="think-title">
                    <Icon name="brain" :size="14" color="#0ea5e9" />
                    <text>AI 思考中...</text>
                  </view>
                </view>
                <view class="think-content">
                  <rich-text :nodes="parseContent(getThinkBlocks(msg.content).streamingThink)" />
                </view>
              </view>
            </view>
            <!-- 主内容 -->
            <view class="bubble-text">
              <rich-text :nodes="parseContent(getThinkBlocks(msg.content).main)" />
            </view>
            <text class="bubble-time">{{ formatTime(msg.timestamp) }}</text>
          </view>
        </view>

        <!-- 用户问题 -->
        <view v-else class="question-bubble">
          <view class="bubble-content">
            <text class="bubble-text">{{ msg.content }}</text>
            <text class="bubble-time">{{ formatTime(msg.timestamp) }}</text>
          </view>
          <view class="user-avatar">
            <Icon name="user" :size="28" color="#fff" />
          </view>
        </view>
      </view>

      <!-- 加载状态 -->
      <view v-if="isLoading" class="loading-indicator">
        <view class="loading-dots">
          <view class="dot"></view>
          <view class="dot"></view>
          <view class="dot"></view>
        </view>
        <text class="loading-text">AI 正在思考...</text>
      </view>
    </scroll-view>

    <!-- 输入区域 -->
    <view class="input-section">
      <view class="input-area">
        <textarea
          v-model="inputText"
          class="message-input"
          placeholder="请输入你的问题..."
          placeholder-class="input-placeholder"
          :disabled="isLoading"
          :maxlength="2000"
          :auto-height="true"
          @confirm="sendMessage"
        />

        <view
          class="send-btn"
          :class="{ disabled: !inputText.trim() || isLoading }"
          @click="sendMessage"
        >
          <text>发送</text>
        </view>
      </view>

      <view class="bottom-tip">
        <text>AI 会根据知识库内容回答问题</text>
      </view>
    </view>

    <!-- 会话历史侧边栏 -->
    <view v-if="showSessionList" class="session-mask" @click="showSessionList = false">
      <view class="session-panel" @click.stop>
        <view class="session-header">
          <text class="session-title">对话历史</text>
          <view class="session-actions">
            <text class="new-btn" @click="handleNewSession">新建</text>
            <text class="close-btn" @click="showSessionList = false">✕</text>
          </view>
        </view>

        <scroll-view class="session-list" scroll-y>
          <view v-if="loadingSessions" class="session-loading">
            <text>加载中...</text>
          </view>

          <view v-else-if="sessions.length === 0" class="session-empty">
            <text>暂无对话历史</text>
          </view>

          <view
            v-for="session in sessions"
            :key="session.id"
            class="session-item"
            :class="{ active: currentSessionId === session.id }"
          >
            <view class="session-main" @click="handleSelectSession(session)">
              <view class="session-pin" v-if="session.isPinned"><Icon name="pin" :size="14" color="#0ea5e9" /></view>
              <template v-if="editingSessionId === session.id">
                <input
                  v-model="editingSessionTitle"
                  class="session-edit-input"
                  @click.stop
                  @confirm="handleSaveSessionTitle"
                  @blur="handleSaveSessionTitle"
                />
              </template>
              <template v-else>
                <text class="session-name">{{ session.title }}</text>
              </template>
              <text class="session-time">{{ formatTimeAgo(session.updatedAt) }}</text>
            </view>

            <view class="session-ops">
              <text class="op-btn" @click.stop="handleTogglePin(session)">
                <Icon :name="session.isPinned ? 'pin' : 'message'" :size="14" :color="session.isPinned ? '#0ea5e9' : '#94a3b8'" />
              </text>
              <view class="op-btn" @click.stop="startEditSession(session)"><Icon name="edit" :size="16" color="#94a3b8" /></view>
              <view class="op-btn danger" @click.stop="handleDeleteSession(session)"><Icon name="trash" :size="16" color="#ef4444" /></view>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<style lang="scss">
@import '../../styles/variables.scss';

.chat-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: $bg-color;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 30rpx;
  background-color: #fff;
  border-bottom: 1rpx solid #f0f0f0;
}

.header-left {
  width: 80rpx;

  .back-icon {
    font-size: 40rpx;
    color: #333;
  }
}

.header-center {
  flex: 1;
  text-align: center;

  .chat-title {
    display: block;
    font-size: 32rpx;
    font-weight: 600;
    color: #333;
  }

  .chat-subtitle {
    display: block;
    font-size: 22rpx;
    color: #999;
    margin-top: 4rpx;
  }
}

.header-right {
  width: 80rpx;
  display: flex;
  justify-content: flex-end;

  .new-chat-icon {
    font-size: 44rpx;
    color: $primary;
    font-weight: 600;
  }
}

.message-list {
  flex: 1;
  padding: 30rpx;
  overflow: hidden;
}

.message-item {
  margin-bottom: 30rpx;

  &.answer {
    display: flex;
    align-items: flex-start;
  }

  &.question {
    display: flex;
    justify-content: flex-end;
  }
}

.ai-avatar,
.user-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  flex-shrink: 0;
}

.ai-avatar {
  background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
  color: #fff;
  margin-right: 20rpx;
}

.user-avatar {
  background: linear-gradient(135deg, #0ea5e9 0%, #38bdf8 100%);
  color: #fff;
  margin-left: 20rpx;
}

.bubble-content {
  max-width: 70%;
  position: relative;
}

.answer-bubble {
  .bubble-content {
    background-color: #fff;
    border-radius: 24rpx;
    padding: 24rpx;
    box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
  }

  .bubble-text {
    font-size: 28rpx;
    color: #333;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-word;

    // Markdown 样式
    :deep(.md-h1) { font-size: 36rpx; font-weight: 700; margin: 16rpx 0; }
    :deep(.md-h2) { font-size: 32rpx; font-weight: 600; margin: 14rpx 0; }
    :deep(.md-h3) { font-size: 28rpx; font-weight: 600; margin: 12rpx 0; }
    :deep(.md-p) { margin: 8rpx 0; }
    :deep(.md-code) { background: #f5f5f5; padding: 4rpx 8rpx; border-radius: 4rpx; font-family: monospace; }
    :deep(.md-pre) { background: #f5f5f5; padding: 16rpx; border-radius: 8rpx; overflow-x: auto; margin: 12rpx 0; }
    :deep(.md-ul), :deep(.md-ol) { margin: 8rpx 0; padding-left: 32rpx; }
    :deep(.md-li) { margin: 4rpx 0; }
    :deep(.md-strong) { font-weight: 700; }
    :deep(.md-em) { font-style: italic; }
    :deep(.md-blockquote) { border-left: 4rpx solid #ddd; padding-left: 16rpx; margin: 12rpx 0; color: #666; }
  }

  .bubble-time {
    display: block;
    margin-top: 12rpx;
    font-size: 20rpx;
    color: #bbb;
    text-align: right;
  }
}

.question-bubble {
  .bubble-content {
    background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
    border-radius: 24rpx;
    padding: 24rpx;

    .bubble-text {
      color: #fff;
    }

    .bubble-time {
      color: rgba(255, 255, 255, 0.7);
    }
  }

  .bubble-text {
    font-size: 28rpx;
    color: #333;
    line-height: 1.6;
    white-space: pre-wrap;
  }

  .bubble-time {
    display: block;
    margin-top: 12rpx;
    font-size: 20rpx;
    color: #bbb;
    text-align: right;
  }
}

.loading-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 30rpx;

  .loading-dots {
    display: flex;
    gap: 12rpx;
    margin-bottom: 16rpx;

    .dot {
      width: 16rpx;
      height: 16rpx;
      border-radius: 50%;
      background-color: $primary;
      animation: loadingBounce 1.4s ease-in-out infinite;

      &:nth-child(1) { animation-delay: 0s; }
      &:nth-child(2) { animation-delay: 0.2s; }
      &:nth-child(3) { animation-delay: 0.4s; }
    }
  }

  .loading-text {
    font-size: 24rpx;
    color: #999;
  }
}

// Think 思考过程样式
.think-section {
  margin-bottom: 20rpx;
}

.think-block {
  border: 1rpx solid #e2e8f0;
  border-radius: 12rpx;
  overflow: hidden;
  margin-bottom: 12rpx;
  background-color: #f8fafc;

  &.think-streaming {
    border-color: #bfdbfe;
    background-color: #f0f9ff;
  }
}

.think-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 20rpx;
  background-color: #f1f5f9;
  cursor: pointer;

  .think-title {
    display: flex;
    align-items: center;
    gap: 12rpx;

    text {
      font-size: 22rpx;
      font-weight: 500;
      color: #475569;
    }
  }

  &:active {
    background-color: #e2e8f0;
  }
}

.think-content {
  padding: 20rpx;
  font-size: 22rpx;
  color: #64748b;
  line-height: 1.6;

  // Markdown 样式
  :deep(.md-h1) { font-size: 32rpx; font-weight: 700; margin: 12rpx 0; }
  :deep(.md-h2) { font-size: 28rpx; font-weight: 600; margin: 10rpx 0; }
  :deep(.md-h3) { font-size: 26rpx; font-weight: 600; margin: 8rpx 0; }
  :deep(.md-p) { margin: 6rpx 0; }
  :deep(.md-code) { background: #e2e8f0; padding: 4rpx 8rpx; border-radius: 4rpx; font-family: monospace; }
  :deep(.md-pre) { background: #e2e8f0; padding: 12rpx; border-radius: 8rpx; overflow-x: auto; margin: 10rpx 0; }
  :deep(.md-ul), :deep(.md-ol) { margin: 6rpx 0; padding-left: 28rpx; }
  :deep(.md-li) { margin: 4rpx 0; }
  :deep(.md-strong) { font-weight: 700; }
  :deep(.md-em) { font-style: italic; }
  :deep(.md-blockquote) { border-left: 4rpx solid #cbd5e1; padding-left: 12rpx; margin: 10rpx 0; color: #64748b; }
}

@keyframes loadingBounce {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.input-section {
  background-color: #fff;
  border-top: 1rpx solid #f0f0f0;
}

.input-area {
  display: flex;
  align-items: flex-end;
  padding: 20rpx 30rpx;
  gap: 20rpx;
}

.message-input {
  flex: 1;
  min-height: 72rpx;
  max-height: 200rpx;
  padding: 16rpx 20rpx;
  background-color: #f5f5f5;
  border-radius: 36rpx;
  font-size: 28rpx;
  color: #333;
  line-height: 1.4;

  &.input-placeholder {
    color: #999;
  }
}

.send-btn {
  padding: 16rpx 32rpx;
  background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
  border-radius: 36rpx;
  color: #fff;
  font-size: 28rpx;
  font-weight: 500;
  flex-shrink: 0;

  &.disabled {
    opacity: 0.5;
  }
}

.bottom-tip {
  text-align: center;
  padding: 16rpx;
  font-size: 22rpx;
  color: #bbb;
  border-top: 1rpx solid #f0f0f0;
}

// 会话历史侧边栏
.session-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 999;
}

.session-panel {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 560rpx;
  background: #fff;
  display: flex;
  flex-direction: column;
}

.session-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 30rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.session-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #333;
}

.session-actions {
  display: flex;
  align-items: center;
  gap: 20rpx;

  .new-btn {
    font-size: 26rpx;
    color: $primary;
    padding: 8rpx 16rpx;
  }

  .close-btn {
    font-size: 32rpx;
    color: #999;
    padding: 8rpx;
  }
}

.session-list {
  flex: 1;
  overflow-y: auto;
}

.session-loading,
.session-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60rpx;
  font-size: 26rpx;
  color: #999;
}

.session-item {
  padding: 24rpx 30rpx;
  border-bottom: 1rpx solid #f5f5f5;

  &.active {
    background: rgba($primary, 0.05);
  }
}

.session-main {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.session-pin {
  font-size: 24rpx;
}

.session-name {
  flex: 1;
  font-size: 28rpx;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-edit-input {
  flex: 1;
  height: 64rpx;
  background: #f5f5f5;
  border: 2rpx solid $primary;
  border-radius: 8rpx;
  padding: 0 16rpx;
  font-size: 26rpx;
}

.session-time {
  font-size: 22rpx;
  color: #999;
  flex-shrink: 0;
}

.session-ops {
  display: flex;
  gap: 16rpx;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx dashed #f0f0f0;
}

.op-btn {
  font-size: 28rpx;
  padding: 8rpx;

  &.danger {
    color: $danger;
  }
}
</style>
