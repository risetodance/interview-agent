import { useUserStore } from '../stores/user'
import { storeToRefs } from 'pinia'

// WebSocket 配置
const WS_URL = import.meta.env.VITE_WS_URL || 'wss://api.interview-guide.com/ws'
const HEARTBEAT_INTERVAL = 30000 // 30秒心跳间隔
const RECONNECT_INTERVAL = 3000 // 3秒重连间隔
const MAX_RECONNECT_COUNT = 5 // 最大重连次数
const MAX_RECONNECT_DELAY = 30000 // 最大重连延迟（30秒）

// 消息类型
export enum WebSocketMessageType {
  HEARTBEAT = 'HEARTBEAT',
  INTERVIEW_QUESTION = 'INTERVIEW_QUESTION',
  INTERVIEW_ANSWER = 'INTERVIEW_ANSWER',
  INTERVIEW_COMPLETE = 'INTERVIEW_COMPLETE',
  AI_EVALUATION = 'AI_EVALUATION',
  SYSTEM_NOTICE = 'SYSTEM_NOTICE',
  ERROR = 'ERROR'
}

// 消息格式
export interface WebSocketMessage {
  type: WebSocketMessageType
  data?: any
  timestamp?: number
}

// 回调函数类型
type MessageCallback = (data: any) => void
type ConnectCallback = () => void
type CloseCallback = (code: number, reason: string) => void
type ErrorCallback = (error: any) => void

/**
 * WebSocket 管理器
 */
class WebSocketManager {
  private socket: UniApp.SocketTask | null = null
  private heartbeatTimer: number | null = null
  private reconnectTimer: number | null = null
  private reconnectCount = 0
  private isManualClose = false

  // 回调存储
  private messageCallbacks: Map<string, MessageCallback[]> = new Map()
  private connectCallback: ConnectCallback | null = null
  private closeCallback: CloseCallback | null = null
  private errorCallback: ErrorCallback | null = null

  // 状态
  private _isConnected = false
  private _lastHeartbeatTime = 0

  get isConnected(): boolean {
    return this._isConnected
  }

  get lastHeartbeatTime(): number {
    return this._lastHeartbeatTime
  }

  /**
   * 连接 WebSocket
   */
  connect(): void {
    if (this.socket || this._isConnected) {
      console.warn('[WebSocket] Already connected or connecting')
      return
    }

    this.isManualClose = false
    this.reconnectCount = 0

    const userStore = useUserStore()
    const { token } = storeToRefs(userStore)

    // 构建 WebSocket URL（带 token）
    let wsUrl = WS_URL
    if (token.value) {
      const separator = wsUrl.includes('?') ? '&' : '?'
      wsUrl = `${wsUrl}${separator}token=${encodeURIComponent(token.value)}`
    }

    console.log('[WebSocket] Connecting to:', wsUrl)

    try {
      this.socket = uni.connectSocket({
        url: wsUrl,
        success: () => {
          console.log('[WebSocket] ConnectSocket success')
        },
        fail: (error) => {
          console.error('[WebSocket] ConnectSocket fail:', error)
          this.handleConnectError(error)
        }
      })

      // 绑定事件
      this.bindEvents()
    } catch (error) {
      console.error('[WebSocket] Connect error:', error)
      this.handleConnectError(error)
    }
  }

  /**
   * 绑定事件
   */
  private bindEvents(): void {
    if (!this.socket) return

    // 连接成功
    this.socket.onOpen(() => {
      console.log('[WebSocket] Connection opened')
      this._isConnected = true
      this.reconnectCount = 0

      // 启动心跳
      this.startHeartbeat()

      // 触发连接回调
      if (this.connectCallback) {
        this.connectCallback()
      }

      // 通知所有订阅者
      this.emit('connect', {})
    })

    // 接收消息
    this.socket.onMessage((res) => {
      try {
        const message: WebSocketMessage = JSON.parse(res.data as string)
        this.handleMessage(message)
      } catch (error) {
        console.error('[WebSocket] Parse message error:', error)
      }
    })

    // 连接关闭
    this.socket.onClose((res) => {
      console.log('[WebSocket] Connection closed:', res.code, res.reason)
      this._isConnected = false

      // 停止心跳
      this.stopHeartbeat()

      // 触发关闭回调
      if (this.closeCallback) {
        this.closeCallback(res.code, res.reason)
      }

      // 通知所有订阅者
      this.emit('close', { code: res.code, reason: res.reason })

      // 如果不是手动关闭，尝试重连
      if (!this.isManualClose) {
        this.scheduleReconnect()
      }
    })

    // 连接错误
    this.socket.onError((error) => {
      console.error('[WebSocket] Connection error:', error)

      if (this.errorCallback) {
        this.errorCallback(error)
      }

      this.emit('error', error)
    })
  }

  /**
   * 处理接收到的消息
   */
  private handleMessage(message: WebSocketMessage): void {
    // 更新心跳时间
    if (message.type === WebSocketMessageType.HEARTBEAT) {
      this._lastHeartbeatTime = Date.now()
      return
    }

    // 触发对应类型的回调
    const callbacks = this.messageCallbacks.get(message.type)
    if (callbacks) {
      callbacks.forEach(callback => {
        try {
          callback(message.data)
        } catch (error) {
          console.error('[WebSocket] Callback error:', error)
        }
      })
    }

    // 也触发 'message' 类型的回调
    const allCallbacks = this.messageCallbacks.get('message')
    if (allCallbacks) {
      allCallbacks.forEach(callback => {
        try {
          callback(message)
        } catch (error) {
          console.error('[WebSocket] Message callback error:', error)
        }
      })
    }
  }

  /**
   * 发送消息
   */
  send(type: WebSocketMessageType, data?: any): boolean {
    if (!this.socket || !this._isConnected) {
      console.warn('[WebSocket] Not connected, cannot send message')
      return false
    }

    const message: WebSocketMessage = {
      type,
      data,
      timestamp: Date.now()
    }

    try {
      this.socket.send({
        data: JSON.stringify(message),
        success: () => {
          console.log('[WebSocket] Message sent:', type)
        },
        fail: (error) => {
          console.error('[WebSocket] Send message fail:', error)
        }
      })
      return true
    } catch (error) {
      console.error('[WebSocket] Send message error:', error)
      return false
    }
  }

  /**
   * 关闭连接
   */
  close(): void {
    console.log('[WebSocket] Manual close')
    this.isManualClose = true

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }

    this.stopHeartbeat()

    if (this.socket) {
      this.socket.close({
        code: 1000,
        reason: 'Client closed',
        success: () => {
          console.log('[WebSocket] Close success')
        },
        fail: (error) => {
          console.error('[WebSocket] Close fail:', error)
        }
      })
      this.socket = null
    }

    this._isConnected = false
  }

  /**
   * 启动心跳
   */
  private startHeartbeat(): void {
    this.stopHeartbeat()

    this.heartbeatTimer = setInterval(() => {
      this.send(WebSocketMessageType.HEARTBEAT, { time: Date.now() })
    }, HEARTBEAT_INTERVAL) as unknown as number

    console.log('[WebSocket] Heartbeat started')
  }

  /**
   * 停止心跳
   */
  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
      console.log('[WebSocket] Heartbeat stopped')
    }
  }

  /**
   * 调度重连
   */
  private scheduleReconnect(): void {
    if (this.isManualClose) return

    if (this.reconnectCount >= MAX_RECONNECT_COUNT) {
      console.error('[WebSocket] Max reconnect count reached')
      uni.showToast({
        title: '连接失败，请检查网络',
        icon: 'none'
      })
      return
    }

    // 计算延迟（指数退避）
    const delay = Math.min(
      RECONNECT_INTERVAL * Math.pow(2, this.reconnectCount),
      MAX_RECONNECT_DELAY
    )

    console.log(`[WebSocket] Scheduling reconnect in ${delay}ms, count: ${this.reconnectCount}`)

    this.reconnectTimer = setTimeout(() => {
      this.reconnectCount++
      this.connect()
    }, delay) as unknown as number
  }

  /**
   * 处理连接错误
   */
  private handleConnectError(error: any): void {
    console.error('[WebSocket] Handle connect error:', error)
    this._isConnected = false

    if (!this.isManualClose) {
      this.scheduleReconnect()
    }
  }

  /**
   * 订阅消息
   */
  on(type: string, callback: MessageCallback): () => void {
    if (!this.messageCallbacks.has(type)) {
      this.messageCallbacks.set(type, [])
    }

    this.messageCallbacks.get(type)!.push(callback)

    // 返回取消订阅的函数
    return () => {
      const callbacks = this.messageCallbacks.get(type)
      if (callbacks) {
        const index = callbacks.indexOf(callback)
        if (index > -1) {
          callbacks.splice(index, 1)
        }
      }
    }
  }

  /**
   * 触发消息
   */
  private emit(type: string, data: any): void {
    const callbacks = this.messageCallbacks.get(type)
    if (callbacks) {
      callbacks.forEach(callback => {
        try {
          callback(data)
        } catch (error) {
          console.error('[WebSocket] Emit error:', error)
        }
      })
    }
  }

  /**
   * 设置连接成功回调
   */
  onConnect(callback: ConnectCallback): void {
    this.connectCallback = callback
  }

  /**
   * 设置关闭回调
   */
  onClose(callback: CloseCallback): void {
    this.closeCallback = callback
  }

  /**
   * 设置错误回调
   */
  onError(callback: ErrorCallback): void {
    this.errorCallback = callback
  }
}

// 导出单例
export const wsManager = new WebSocketManager()

// 便捷方法

/**
 * 发送面试答案
 */
export const sendInterviewAnswer = (answer: string, questionId: string): boolean => {
  return wsManager.send(WebSocketMessageType.INTERVIEW_ANSWER, {
    answer,
    questionId,
    timestamp: Date.now()
  })
}

/**
 * 请求下一个面试题
 */
export const requestNextQuestion = (interviewId: string): boolean => {
  return wsManager.send(WebSocketMessageType.INTERVIEW_QUESTION, {
    interviewId,
    action: 'next'
  })
}

/**
 * 结束面试
 */
export const endInterview = (interviewId: string): boolean => {
  return wsManager.send(WebSocketMessageType.INTERVIEW_COMPLETE, {
    interviewId,
    timestamp: Date.now()
  })
}

export default {
  wsManager,
  WebSocketMessageType,
  sendInterviewAnswer,
  requestNextQuestion,
  endInterview
}
