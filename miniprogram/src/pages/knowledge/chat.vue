<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import Icon from '../../components/common/Icon.vue'
import { renderMarkdown } from '../../utils/marked'
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

// 路由参数
const knowledgebaseIds = ref<number[]>([])
const knowledgebaseNames = ref<string[]>([])

const primaryKnowledgeBaseName = computed(() => knowledgebaseNames.value[0] || '知识库问答')
const selectedKbCount = computed(() => knowledgebaseIds.value.length)
const knowledgeBasesDescription = computed(() => {
  if (knowledgebaseNames.value.length === 0) return '未选择知识库'
  if (knowledgebaseNames.value.length === 1) return knowledgebaseNames.value[0]
  return `${knowledgebaseNames.value.length} 个知识库`
})

// 消息
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const isLoading = ref(false)

// ===== 智能滚动 =====
// 核心思路：流式输出时只有"用户在底部"才自动滚到底；用户主动上滑后不强制拉回，改显示跳转按钮
const scrollTop = ref(0)
const isAtBottom = ref(true)          // 用户是否贴在底部
const showScrollBottom = ref(false)    // 是否显示"跳转到底部"按钮
const scrollViewHeight = ref(0)        // scroll-view 可视高度

// scroll-view 滚动事件：判断是否在底部
const onScroll = (e: any) => {
  const { scrollTop: st, scrollHeight } = e.detail
  if (scrollViewHeight.value === 0) return
  // 距底部小于 120rpx 算"在底部"
  const distanceToBottom = scrollHeight - st - scrollViewHeight.value
  const wasAtBottom = isAtBottom.value
  isAtBottom.value = distanceToBottom < 60
  // 用户主动上滑（不在底部了）→ 显示跳转按钮
  if (!isAtBottom.value) {
    showScrollBottom.value = true
  } else {
    showScrollBottom.value = false
  }
}

// 滚动到底部（只在 isAtBottom 为 true 时执行，避免用户上滑被强制拉回）
const scrollToBottom = () => {
  if (!isAtBottom.value) return
  doScrollToBottom()
}

// 强制滚动到底部（跳转按钮点击 / 发送新消息时用）
const doScrollToBottom = () => {
  nextTick(() => {
    const query = uni.createSelectorQuery()
    query.select('.message-list').boundingClientRect()
    query.selectAll('.message-item').boundingClientRect()
    query.exec((res) => {
      const container = res[0] as { height: number } | null
      const items = res[1] as Array<{ height: number }> | undefined
      if (container && items && items.length) {
        scrollViewHeight.value = container.height
        const contentHeight = items.reduce((sum, it) => sum + (it.height || 0), 0)
        const target = contentHeight > 0 ? contentHeight : container.height
        scrollTop.value = scrollTop.value >= target ? target + 1 : target
      } else {
        scrollTop.value = scrollTop.value + 1
      }
    })
  })
}

// 跳转按钮：强制滚到底 + 标记为在底部
const jumpToBottom = () => {
  isAtBottom.value = true
  showScrollBottom.value = false
  doScrollToBottom()
}

// 会话
const sessions = ref<Array<{ id: number; title: string; isPinned: boolean; updatedAt: string }>>([])
const currentSessionId = ref<number | null>(null)
const showSessionList = ref(false)
const loadingSessions = ref(false)
const isNewSessionMode = ref(false)
const editingSessionId = ref<number | null>(null)
const editingSessionTitle = ref('')
const streamCleanup = ref<(() => void) | null>(null)

// ===== Think 块 =====
interface ThinkBlock { content: string; isComplete: boolean }
const expandedThinks = ref<Record<string, boolean>>({})

const parseThinkBlocks = (content: string): { main: string; thinks: ThinkBlock[]; streamingThink: string | null } => {
  const thinks: ThinkBlock[] = []
  let streamingThink: string | null = null
  const completeRegex = /<think>([\s\S]*?)<\/think>/g
  let match
  let mainContent = content
  while ((match = completeRegex.exec(content)) !== null) {
    thinks.push({ content: match[1].trim(), isComplete: true })
  }
  const openTag = '<think>'
  const closeTag = '</think>'
  const openIndex = content.indexOf(openTag)
  if (openIndex !== -1) {
    const closeIndex = content.lastIndexOf(closeTag)
    if (closeIndex === -1 || closeIndex < openIndex) {
      streamingThink = content.slice(openIndex + openTag.length)
      mainContent = content.slice(0, openIndex)
    } else {
      mainContent = content.slice(closeIndex + closeTag.length)
    }
  } else if (thinks.length === 0) {
    mainContent = content
  }
  return { main: mainContent.trim(), thinks, streamingThink: streamingThink ? streamingThink.trim() : null }
}

const toggleThink = (msgIndex: number, thinkIndex: number) => {
  const key = `${msgIndex}-${thinkIndex}`
  expandedThinks.value = { ...expandedThinks.value, [key]: !expandedThinks.value[key] }
}

const isThinkExpanded = (msgIndex: number, thinkIndex: number, isLastMessage: boolean): boolean => {
  const key = `${msgIndex}-${thinkIndex}`
  if (key in expandedThinks.value) return expandedThinks.value[key]
  return isLastMessage
}

const getThinkBlocks = (content: string) => parseThinkBlocks(content)

// ===== 初始化 =====
onMounted(() => {
  // #ifdef H5
  ;{
  const hash = window.location.hash
  const queryIndex = hash.indexOf('?')
  const queryString = queryIndex !== -1 ? hash.slice(queryIndex + 1) : ''
  const urlParams = new URLSearchParams(queryString)
  const idsStr = urlParams.get('ids') || urlParams.get('id') || ''
  const namesStr = urlParams.get('names') || urlParams.get('name') || ''
  if (idsStr) knowledgebaseIds.value = idsStr.split(',').map(id => Number(id.trim())).filter(id => !isNaN(id))
  if (namesStr) knowledgebaseNames.value = namesStr.split(',').map(name => decodeURIComponent(name.trim()))
  }
  // #endif
  // #ifndef H5
  ;{
  const pages = getCurrentPages()
  const page = pages[pages.length - 1] as any
  const options = page?.options || {}
  const idsStr = options.ids || options.id || ''
  const namesStr = options.names || options.name || ''
  if (idsStr) knowledgebaseIds.value = idsStr.split(',').map((id: string) => Number(id.trim())).filter((id: number) => !isNaN(id))
  if (namesStr) knowledgebaseNames.value = namesStr.split(',').map((name: string) => decodeURIComponent(name.trim()))
  }
  // #endif
  addWelcomeMessage()
  loadSessions()
  // 欢迎消息渲染后自动滚到底部（首次进入页面应贴底）
  nextTick(() => { isAtBottom.value = true; doScrollToBottom() })
})

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

const addWelcomeMessage = () => {
  messages.value = [{
    id: Date.now(),
    type: 'answer',
    content: `欢迎使用知识库问答！我可以根据「${knowledgeBasesDescription.value}」中的内容回答你的问题。\n\n请输入你的问题，我会从知识库中检索相关信息并为你解答。`,
    timestamp: new Date().toISOString()
  }]
}

// ===== 发送消息 =====
const sendMessage = async () => {
  if (!inputText.value.trim() || isLoading.value) return

  const question = inputText.value.trim()
  inputText.value = ''

  // 没有会话则创建
  if (!currentSessionId.value) {
    try {
      const session = await createRagSession(knowledgebaseIds.value)
      currentSessionId.value = session.id
      isNewSessionMode.value = false
    } catch (error) {
      uni.showToast({ title: '会话创建失败', icon: 'none' })
      return
    }
  }

  // 用户消息
  messages.value.push({
    id: Date.now(),
    type: 'question',
    content: question,
    timestamp: new Date().toISOString()
  })

  // 发送后强制滚到底部（用户主动发消息，一定想看到回复）
  isAtBottom.value = true
  showScrollBottom.value = false
  doScrollToBottom()

  await sendToAI(question)
}

// ===== AI 流式回复 =====
const sendToAI = async (question: string) => {
  isLoading.value = true

  // 只添加一条 AI 占位消息，SSE 内容到达后填充它（不再额外加 thinking-row，避免"两条回复"）
  const aiMsgId = Date.now() + 1
  messages.value.push({
    id: aiMsgId,
    type: 'answer',
    content: '',
    timestamp: new Date().toISOString()
  })

  const aiIndex = messages.value.length - 1
  let fullContent = ''

  const cleanup = connectRagChatStream(
    currentSessionId.value!,
    question,
    {
      onConnected: () => {},
      onMessage: (content: string) => {
        if (isLoading.value) isLoading.value = false
        fullContent += content
        messages.value[aiIndex].content = fullContent
        scrollToBottom()
      },
      onComplete: () => {
        messages.value[aiIndex] = {
          id: aiMsgId,
          type: 'answer',
          content: fullContent || '抱歉，未收到有效回复。',
          timestamp: new Date().toISOString()
        }
        isLoading.value = false
        scrollToBottom()
      },
      onError: (error: string) => {
        messages.value[aiIndex] = {
          id: aiMsgId,
          type: 'answer',
          content: `抱歉，发生了错误：${error}`,
          timestamp: new Date().toISOString()
        }
        isLoading.value = false
        scrollToBottom()
      }
    }
  )
  streamCleanup.value = cleanup
}

// ===== 格式化 =====
const formatTime = (timestamp: string): string => {
  const d = new Date(timestamp)
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

const formatTimeAgo = (dateStr: string): string => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const diffMs = Date.now() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)
  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins}分钟前`
  if (diffHours < 24) return `${diffHours}小时前`
  if (diffDays < 7) return `${diffDays}天前`
  return formatTime(dateStr)
}

const parseContent = (content: string | null | undefined) => renderMarkdown(content)

// ===== 会话管理 =====
const goBack = () => uni.navigateBack()

const handleNewSession = () => {
  messages.value = []
  currentSessionId.value = null
  showSessionList.value = false
  isNewSessionMode.value = true
  addWelcomeMessage()
  nextTick(() => doScrollToBottom())
}

const handleSelectSession = async (session: { id: number }) => {
  currentSessionId.value = session.id
  showSessionList.value = false
  try {
    const data = await getRagMessages(session.id) as any
    if (data && data.messages && data.messages.length > 0) {
      messages.value = data.messages.map((m: any) => ({
        id: m.id || Date.now() + Math.random(),
        type: m.type === 'user' ? 'question' : 'answer',
        content: m.content || '',
        timestamp: m.createdAt || new Date().toISOString()
      }))
    } else {
      addWelcomeMessage()
    }
    isAtBottom.value = true
    nextTick(() => doScrollToBottom())
  } catch (error) {
    addWelcomeMessage()
  }
}

const startEditSession = (session: { id: number; title: string }) => {
  editingSessionId.value = session.id
  editingSessionTitle.value = session.title
}

const cancelEditSession = () => {
  editingSessionId.value = null
  editingSessionTitle.value = ''
}

const handleSaveSessionTitle = async () => {
  if (!editingSessionId.value || !editingSessionTitle.value.trim()) return
  try {
    await updateRagSessionTitle(editingSessionId.value, editingSessionTitle.value.trim())
    const session = sessions.value.find(s => s.id === editingSessionId.value)
    if (session) session.title = editingSessionTitle.value.trim()
    cancelEditSession()
  } catch (error) {
    uni.showToast({ title: '保存失败', icon: 'none' })
  }
}

const handleTogglePin = async (session: { id: number; isPinned: boolean }) => {
  try {
    await toggleRagSessionPin(session.id, !session.isPinned)
    session.isPinned = !session.isPinned
  } catch (error) {
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

const handleDeleteSession = async (session: { id: number }) => {
  uni.showModal({
    title: '确认删除',
    content: '确定要删除这个会话吗？',
    success: async (res) => {
      if (res.confirm) {
        try {
          await deleteRagSession(session.id)
          sessions.value = sessions.value.filter(s => s.id !== session.id)
          if (currentSessionId.value === session.id) handleNewSession()
          uni.showToast({ title: '删除成功', icon: 'success' })
        } catch (error) {
          uni.showToast({ title: '删除失败', icon: 'none' })
        }
      }
    }
  })
}

onUnmounted(() => {
  if (streamCleanup.value) {
    streamCleanup.value()
    streamCleanup.value = null
  }
})
</script>

<template>
  <view class="chat-container">
    <!-- 品牌蓝顶栏 -->
    <view class="chat-header">
      <view class="hd-btn" @click="goBack">
        <Icon name="arrow-left" :size="20" color="#fff" />
      </view>
      <view class="hd-center" @click="showSessionList = true">
        <text class="hd-title">{{ primaryKnowledgeBaseName }}</text>
        <text class="hd-sub">{{ selectedKbCount > 1 ? selectedKbCount + ' 个知识库' : '点击查看会话历史' }}</text>
      </view>
      <view class="hd-btn" @click="handleNewSession">
        <Icon name="plus" :size="22" color="#fff" />
      </view>
    </view>

    <!-- 消息列表 -->
    <scroll-view
      class="message-list"
      scroll-y
      :scroll-top="scrollTop"
      :scroll-with-animation="false"
      @scroll="onScroll"
    >
      <view class="msg-padding-top"></view>
      <view
        v-for="(msg, index) in messages"
        :key="msg.id"
        class="msg-row"
        :class="msg.type"
      >
        <!-- AI 回答 -->
        <template v-if="msg.type === 'answer'">
          <view class="avatar ai-avatar">
            <Icon name="bot" :size="20" color="#fff" />
          </view>
          <view class="bubble ai-bubble">
            <!-- 思考过程 -->
            <template v-if="getThinkBlocks(msg.content).thinks.length > 0 || getThinkBlocks(msg.content).streamingThink">
              <view v-for="(think, i) in getThinkBlocks(msg.content).thinks" :key="i" class="think-block">
                <view class="think-header" @click="toggleThink(index, i)">
                  <view class="think-label">
                    <Icon name="brain" :size="12" color="#0ea5e9" />
                    <text>AI 思考过程</text>
                  </view>
                  <Icon :name="isThinkExpanded(index, i, index === messages.length - 1) ? 'chevron-up' : 'chevron-down'" :size="12" color="#94a3b8" />
                </view>
                <view v-if="isThinkExpanded(index, i, index === messages.length - 1)" class="think-body">
                  <rich-text :nodes="parseContent(think.content)" />
                </view>
              </view>
              <view v-if="getThinkBlocks(msg.content).streamingThink" class="think-block think-streaming">
                <view class="think-header">
                  <view class="think-label">
                    <Icon name="brain" :size="12" color="#0ea5e9" />
                    <text>AI 思考中...</text>
                  </view>
                </view>
                <view class="think-body">
                  <rich-text :nodes="parseContent(getThinkBlocks(msg.content).streamingThink)" />
                </view>
              </view>
            </template>
            <!-- 主内容 -->
            <view class="bubble-text" v-if="getThinkBlocks(msg.content).main">
              <rich-text :nodes="parseContent(getThinkBlocks(msg.content).main)" />
            </view>
            <!-- 空内容 + 加载中 = 三点动画（单条消息，不会出现两条回复） -->
            <view v-if="isLoading && index === messages.length - 1 && !msg.content" class="thinking-dots">
              <view class="dot"></view><view class="dot"></view><view class="dot"></view>
            </view>
            <text class="bubble-time" v-if="msg.content">{{ formatTime(msg.timestamp) }}</text>
          </view>
        </template>

        <!-- 用户问题 -->
        <template v-else>
          <view class="bubble user-bubble">
            <text class="bubble-text">{{ msg.content }}</text>
            <text class="bubble-time">{{ formatTime(msg.timestamp) }}</text>
          </view>
          <view class="avatar user-avatar">
            <Icon name="user" :size="20" color="#fff" />
          </view>
        </template>
      </view>
      <view class="msg-padding-bottom"></view>
    </scroll-view>

    <!-- 跳转到底部按钮 -->
    <view v-if="showScrollBottom" class="scroll-bottom-btn" @click="jumpToBottom">
      <Icon name="chevron-down" :size="18" color="#0ea5e9" />
    </view>

    <!-- 输入区 -->
    <view class="input-section">
      <view class="input-bar">
        <textarea
          v-model="inputText"
          class="msg-input"
          placeholder="输入问题，基于知识库作答"
          placeholder-class="input-ph"
          :disabled="isLoading"
          :maxlength="2000"
          :auto-height="true"
          @confirm="sendMessage"
        />
        <view class="send-btn" :class="{ disabled: !inputText.trim() || isLoading }" @click="sendMessage">
          <Icon name="send" :size="18" color="#fff" />
        </view>
      </view>
    </view>

    <!-- 会话历史侧边栏 -->
    <view v-if="showSessionList" class="session-mask" @click="showSessionList = false">
      <view class="session-panel" @click.stop>
        <view class="session-header">
          <text class="session-title">对话历史</text>
          <view class="session-actions">
            <view class="sa-btn" @click="handleNewSession"><Icon name="plus" :size="18" color="#0ea5e9" /></view>
            <view class="sa-btn" @click="showSessionList = false"><Icon name="x" :size="18" color="#94a3b8" /></view>
          </view>
        </view>
        <scroll-view class="session-scroll" scroll-y>
          <view v-if="loadingSessions" class="session-empty"><text>加载中...</text></view>
          <view v-else-if="sessions.length === 0" class="session-empty">
            <view class="se-icon"><Icon name="message" :size="40" color="#cbd5e1" /></view>
            <text class="se-text">暂无对话历史</text>
          </view>
          <view v-for="session in sessions" :key="session.id" class="session-item" :class="{ active: currentSessionId === session.id }">
            <view class="session-main" @click="handleSelectSession(session)">
              <view v-if="session.isPinned" class="session-pin"><Icon name="pin" :size="12" color="#0ea5e9" /></view>
              <template v-if="editingSessionId === session.id">
                <input v-model="editingSessionTitle" class="session-edit" @click.stop @confirm="handleSaveSessionTitle" @blur="handleSaveSessionTitle" />
              </template>
              <template v-else>
                <text class="session-name">{{ session.title }}</text>
                <text class="session-time">{{ formatTimeAgo(session.updatedAt) }}</text>
              </template>
            </view>
            <view class="session-ops">
              <view class="op-btn" @click.stop="handleTogglePin(session)"><Icon :name="session.isPinned ? 'pin' : 'bookmark'" :size="15" :color="session.isPinned ? '#0ea5e9' : '#94a3b8'" /></view>
              <view class="op-btn" @click.stop="startEditSession(session)"><Icon name="edit" :size="15" color="#94a3b8" /></view>
              <view class="op-btn danger" @click.stop="handleDeleteSession(session)"><Icon name="trash" :size="15" color="#ef4444" /></view>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.chat-container { display: flex; flex-direction: column; height: 100vh; background: $bg; }

// ===== 顶栏 =====
.chat-header {
  flex-shrink: 0;
  display: flex; align-items: center; gap: 12rpx;
  background: linear-gradient(135deg, $primary 0%, $primary-dark 100%);
  padding: calc(env(safe-area-inset-top, 0px) + 24rpx) 24rpx 24rpx;
}
.hd-btn {
  width: 64rpx; height: 64rpx; border-radius: 50%;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
  background: rgba(255,255,255,0.15);
  &:active { background: rgba(255,255,255,0.28); }
}
.hd-center { flex: 1; min-width: 0; display: flex; flex-direction: column; align-items: center; }
.hd-title { font-size: 30rpx; font-weight: 700; color: #fff; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; max-width: 100%; }
.hd-sub { font-size: 20rpx; color: rgba(255,255,255,0.65); margin-top: 2rpx; }

// ===== 消息列表 =====
.message-list { flex: 1; }
.msg-padding-top { height: 24rpx; }
.msg-padding-bottom { height: 24rpx; }

.msg-row {
  display: flex; align-items: flex-start; gap: 16rpx;
  padding: 0 24rpx; margin-bottom: 28rpx;
  &.question { justify-content: flex-end; }
}

.avatar {
  width: 64rpx; height: 64rpx; border-radius: 50%;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.ai-avatar { background: linear-gradient(135deg, $primary, $primary-light); box-shadow: 0 4rpx 12rpx rgba($primary,0.25); }
.user-avatar { background: #94a3b8; }

.bubble {
  max-width: 76%; border-radius: 20rpx; padding: 20rpx 24rpx; box-sizing: border-box;
}
.ai-bubble {
  background: #fff; border-top-left-radius: 6rpx;
  box-shadow: 0 2rpx 12rpx rgba(15,23,42,0.06); color: $text-primary;
}
.user-bubble {
  background: linear-gradient(135deg, $primary, $primary-dark);
  border-top-right-radius: 6rpx; color: #fff;
  box-shadow: 0 4rpx 16rpx rgba($primary,0.25);
}

.bubble-text {
  font-size: 28rpx; line-height: 1.6; word-break: break-word; overflow-wrap: anywhere;
  :deep(.md-h1) { font-size: 34rpx; font-weight: 700; margin: 14rpx 0; }
  :deep(.md-h2) { font-size: 30rpx; font-weight: 600; margin: 12rpx 0; }
  :deep(.md-h3) { font-size: 28rpx; font-weight: 600; margin: 10rpx 0; }
  :deep(.md-p) { margin: 8rpx 0; }
  :deep(.md-code) { background: #f1f5f9; padding: 4rpx 8rpx; border-radius: 4rpx; font-family: monospace; }
  :deep(.md-pre) { background: #f1f5f9; padding: 16rpx; border-radius: 8rpx; overflow-x: auto; margin: 12rpx 0; }
  :deep(.md-ul), :deep(.md-ol) { margin: 8rpx 0; padding-left: 32rpx; }
  :deep(.md-li) { margin: 4rpx 0; }
  :deep(.md-strong) { font-weight: 700; }
  :deep(.md-em) { font-style: italic; }
  :deep(.md-blockquote) { border-left: 4rpx solid #cbd5e1; padding-left: 16rpx; margin: 12rpx 0; }
}
.bubble-time { display: block; margin-top: 10rpx; font-size: 20rpx; text-align: right; opacity: 0.5; }

// ===== 思考块 =====
.think-block { border: 1rpx solid #e2e8f0; border-radius: 12rpx; overflow: hidden; margin-bottom: 12rpx; background: #f8fafc; }
.think-block.think-streaming { border-color: #bfdbfe; background: #f0f9ff; }
.think-header {
  display: flex; align-items: center; justify-content: space-between; padding: 14rpx 18rpx; background: #f1f5f9;
  &:active { background: #e2e8f0; }
  .think-label { display: flex; align-items: center; gap: 8rpx; text { font-size: 22rpx; font-weight: 500; color: $text-secondary; } }
}
.think-body { padding: 16rpx 18rpx; font-size: 22rpx; color: $text-muted; line-height: 1.6; }

// ===== 思考中三点 =====
.thinking-dots { display: flex; align-items: center; gap: 10rpx; height: 36rpx; padding: 4rpx 0; }
.thinking-dots .dot { width: 14rpx; height: 14rpx; border-radius: 50%; background: $primary; animation: thinkBounce 1.4s ease-in-out infinite; }
.thinking-dots .dot:nth-child(1) { animation-delay: 0s; }
.thinking-dots .dot:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes thinkBounce { 0%,80%,100% { transform: scale(0.5); opacity: 0.4; } 40% { transform: scale(1); opacity: 1; } }

// ===== 跳转按钮 =====
.scroll-bottom-btn {
  // 用 fixed 相对视口定位：absolute 在 flex 容器内会相对消息列表区，位置错乱
  position: fixed; right: 32rpx;
  bottom: calc(env(safe-area-inset-bottom, 0px) + 144rpx);
  width: 72rpx; height: 72rpx; border-radius: 50%;
  background: #fff; box-shadow: 0 4rpx 20rpx rgba(15,23,42,0.15);
  display: flex; align-items: center; justify-content: center; z-index: 50;
  &:active { transform: scale(0.92); }
}

// ===== 输入区 =====
.input-section {
  flex-shrink: 0; background: #fff; border-top: 1rpx solid #f1f5f9;
  padding: 16rpx 24rpx calc(env(safe-area-inset-bottom, 0px) + 16rpx);
}
.input-bar { display: flex; align-items: flex-end; gap: 16rpx; }
.msg-input {
  flex: 1; min-height: 72rpx; max-height: 200rpx; padding: 16rpx 24rpx;
  background: $bg; border-radius: 36rpx; font-size: 28rpx; color: $text-primary; line-height: 1.4; box-sizing: border-box;
}
.input-ph { color: $text-muted; }
.send-btn {
  width: 72rpx; height: 72rpx; border-radius: 50%;
  background: linear-gradient(135deg, $primary, $primary-dark);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
  box-shadow: 0 4rpx 12rpx rgba($primary,0.3);
  &:active { transform: scale(0.92); }
  &.disabled { opacity: 0.4; box-shadow: none; }
}

// ===== 会话侧栏 =====
.session-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 200; }
.session-panel { position: absolute; left: 0; top: 0; bottom: 0; width: 600rpx; background: #fff; display: flex; flex-direction: column; }
.session-header { display: flex; align-items: center; justify-content: space-between; padding: calc(env(safe-area-inset-top, 0px) + 28rpx) 28rpx 24rpx; border-bottom: 1rpx solid #f1f5f9; }
.session-title { font-size: 32rpx; font-weight: 700; color: $text-primary; }
.session-actions { display: flex; gap: 12rpx; }
.sa-btn { width: 56rpx; height: 56rpx; border-radius: 50%; background: $bg; display: flex; align-items: center; justify-content: center; &:active { background: #e2e8f0; } }
.session-scroll { flex: 1; }
.session-empty { display: flex; flex-direction: column; align-items: center; padding: 120rpx 0; }
.se-icon { width: 96rpx; height: 96rpx; display: flex; align-items: center; justify-content: center; margin-bottom: 20rpx; }
.se-text { font-size: 26rpx; color: $text-muted; }
.session-item { padding: 20rpx 28rpx; border-bottom: 1rpx solid #f8fafc; &.active { background: rgba($primary,0.04); } }
.session-main { display: flex; align-items: center; gap: 8rpx; }
.session-pin { display: flex; align-items: center; }
.session-name { flex: 1; font-size: 28rpx; color: $text-primary; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-edit { flex: 1; height: 60rpx; background: $bg; border: 2rpx solid $primary; border-radius: 10rpx; padding: 0 16rpx; font-size: 26rpx; min-width: 0; }
.session-time { font-size: 22rpx; color: $text-muted; flex-shrink: 0; }
.session-ops { display: flex; gap: 8rpx; margin-top: 14rpx; padding-top: 14rpx; border-top: 1rpx dashed #f1f5f9; }
.op-btn { width: 56rpx; height: 56rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; background: $bg; &:active { background: #e2e8f0; } &.danger { background: #fef2f2; } }
</style>
