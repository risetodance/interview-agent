import { useUserStore } from '../stores/user'
import { storeToRefs } from 'pinia'
import { apiBaseUrl } from './env'

// N7：401 竞态去重标记。并发请求同时收到 401 时，只允许第一个触发 logout + reLaunch，
// 后续 401 直接 reject，避免重复 toast / 多次 reLaunch / 多次调登出接口。
let isLoggingOut = false

// 请求配置类型
interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  data?: Record<string, any>
  header?: Record<string, string>
  timeout?: number
  showLoading?: boolean
  showError?: boolean
}

// 响应类型
interface ResponseData<T = any> {
  code: number
  message: string
  data: T
}

// 创建请求拦截器
const requestInterceptors = (options: RequestOptions): RequestOptions => {
  const userStore = useUserStore()
  const { token } = storeToRefs(userStore)

  const header: Record<string, string> = {
    'Content-Type': 'application/json',
    ...options.header
  }

  // 添加 token
  if (token.value) {
    header['Authorization'] = `Bearer ${token.value}`
  }

  // 添加租户ID（可选）
  const tenantId = uni.getStorageSync('tenantId')
  if (tenantId) {
    header['X-Tenant-Id'] = tenantId
  }

  return {
    ...options,
    header
  }
}

// 创建响应拦截器
// showError: 是否弹出错误 toast（业务调用可传 false 静默处理，如 findUnfinishedSession）
const responseInterceptors = (
  response: UniApp.RequestSuccessCallbackResult,
  showError = true
): any => {
  const statusCode = response.statusCode || 200

  // HTTP 401：鉴权失败（含后端非 Result 包装的空体 401，如 InterviewStreamController/Security 默认 401）→ 登出（B4）
  // N7：并发 401 去重——首个 401 触发登出+跳转，后续 401 直接 reject 不重复处理
  if (statusCode === 401) {
    if (!isLoggingOut) {
      isLoggingOut = true
      const userStore = useUserStore()
      // logout 完成后（无论成功失败）释放守卫，允许下次登录态失效再触发
      userStore.logout().finally(() => { isLoggingOut = false })

      if (showError) {
        uni.showToast({
          title: '登录已过期，请重新登录',
          icon: 'none'
        })
      }

      // 跳转到登录页
      uni.reLaunch({
        url: '/pages/auth/login'
      })
    }

    return Promise.reject(new Error('登录已过期'))
  }

  // 其它 HTTP >=400：优先按 HTTP 状态码判断，避免非 Result 格式响应穿透（B4）
  if (statusCode >= 400) {
    const errData = response.data as any
    const message = (errData && typeof errData === 'object' && errData.message)
      ? errData.message
      : `请求失败(${statusCode})`
    if (showError) {
      uni.showToast({ title: message, icon: 'none' })
    }
    return Promise.reject(new Error(message))
  }

  // HTTP 200：data 可能是非对象（纯文本/HTML/空），兜底
  const data = (response.data && typeof response.data === 'object')
    ? response.data as ResponseData
    : null
  if (!data) {
    // 非标准 Result 响应，直接返回原始 data
    return response.data
  }

  // 根据业务状态码处理
  if (data.code === 200 || data.code === 0) {
    return data.data
  }

  // 业务码 401（个别接口在 HTTP 200 内返回业务 401）
  // N7：同样走 isLoggingOut 守卫，避免与 HTTP 401 分支重复登出
  if (data.code === 401) {
    if (!isLoggingOut) {
      isLoggingOut = true
      const userStore = useUserStore()
      userStore.logout().finally(() => { isLoggingOut = false })

      if (showError) {
        uni.showToast({
          title: '登录已过期，请重新登录',
          icon: 'none'
        })
      }

      // 跳转到登录页
      uni.reLaunch({
        url: '/pages/auth/login'
      })
    }

    return Promise.reject(new Error(data.message || '登录已过期'))
  }

  // 其他业务错误
  if (data.code >= 400) {
    if (showError) {
      uni.showToast({
        title: data.message || '请求失败',
        icon: 'none'
      })
    }
    return Promise.reject(new Error(data.message || '请求失败'))
  }

  return data.data
}

// 处理请求错误
const handleRequestError = (error: any): void => {
  let message = '网络请求失败，请稍后重试'

  if (error.errMsg) {
    if (error.errMsg.includes('timeout')) {
      message = '请求超时，请检查网络'
    } else if (error.errMsg.includes('abort')) {
      message = '请求被中断'
    } else if (error.errMsg.includes('fail')) {
      message = '网络连接失败'
    }
  }

  uni.showToast({
    title: message,
    icon: 'none'
  })
}

/**
 * 统一请求方法
 * @param options 请求配置
 * @returns Promise<T>
 */
export const request = <T = any>(options: RequestOptions): Promise<T> => {
  const {
    url,
    method = 'GET',
    data = {},
    header = {},
    timeout = 60000,
    showLoading = false,
    showError = true
  } = options

  // 请求拦截
  const processedOptions = requestInterceptors({ url, method, data, header, timeout })

  // 显示 loading
  if (showLoading) {
    uni.showLoading({
      title: '加载中...',
      mask: true
    })
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url: apiBaseUrl + processedOptions.url,
      method: processedOptions.method,
      data: processedOptions.data,
      header: processedOptions.header,
      timeout: processedOptions.timeout,
      success: (res) => {
        if (showLoading) {
          uni.hideLoading()
        }

        try {
          const result = responseInterceptors(res, showError)
          resolve(result)
        } catch (error: any) {
          reject(error)
        }
      },
      fail: (error) => {
        if (showLoading) {
          uni.hideLoading()
        }

        if (showError) {
          handleRequestError(error)
        }

        reject(error)
      }
    })
  })
}

// 便捷方法封装
export const get = <T = any>(url: string, data?: Record<string, any>, options?: Partial<RequestOptions>): Promise<T> => {
  return request<T>({
    url,
    method: 'GET',
    data,
    ...options
  })
}

export const post = <T = any>(url: string, data?: Record<string, any>, options?: Partial<RequestOptions>): Promise<T> => {
  return request<T>({
    url,
    method: 'POST',
    data,
    ...options
  })
}

export const put = <T = any>(url: string, data?: Record<string, any>, options?: Partial<RequestOptions>): Promise<T> => {
  return request<T>({
    url,
    method: 'PUT',
    data,
    ...options
  })
}

export const del = <T = any>(url: string, data?: Record<string, any>, options?: Partial<RequestOptions>): Promise<T> => {
  return request<T>({
    url,
    method: 'DELETE',
    data,
    ...options
  })
}

export const patch = <T = any>(url: string, data?: Record<string, any>, options?: Partial<RequestOptions>): Promise<T> => {
  return request<T>({
    url,
    method: 'PATCH',
    data,
    ...options
  })
}

/**
 * 上传文件方法
 * @param filePath 文件路径
 * @param options 请求配置
 * @returns Promise<T>
 */
export const uploadFile = <T = any>(
  filePath: string,
  options: {
    url: string
    name?: string
    formData?: Record<string, any>
    showLoading?: boolean
  }
): Promise<T> => {
  const userStore = useUserStore()
  const { token } = storeToRefs(userStore)

  const header: Record<string, string> = {}
  if (token.value) {
    header['Authorization'] = `Bearer ${token.value}`
  }

  if (options.showLoading) {
    uni.showLoading({
      title: '上传中...',
      mask: true
    })
  }

  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: apiBaseUrl + options.url,
      filePath,
      name: options.name || 'file',
      formData: options.formData || {},
      header,
      success: (res) => {
        if (options.showLoading) {
          uni.hideLoading()
        }

        try {
          const data = JSON.parse(res.data) as ResponseData
          if (data.code === 200 || data.code === 0) {
            resolve(data.data)
          } else {
            uni.showToast({
              title: data.message || '上传失败',
              icon: 'none'
            })
            reject(new Error(data.message))
          }
        } catch {
          reject(new Error('解析响应失败'))
        }
      },
      fail: (error) => {
        if (options.showLoading) {
          uni.hideLoading()
        }
        handleRequestError(error)
        reject(error)
      }
    })
  })
}

/**
 * 下载文件方法
 * @param url 下载地址
 * @param showLoading 是否显示loading
 * @returns Promise<string> 文件临时路径
 */
export const downloadFile = (url: string, showLoading = false): Promise<string> => {
  const userStore = useUserStore()
  const { token } = storeToRefs(userStore)

  // 注入鉴权头，与 request/uploadFile 保持一致（后端 downloadKnowledgeBase 依赖 @CurrentUser）
  const header: Record<string, string> = {}
  if (token.value) {
    header['Authorization'] = `Bearer ${token.value}`
  }

  if (showLoading) {
    uni.showLoading({
      title: '下载中...',
      mask: true
    })
  }

  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: apiBaseUrl + url,
      header,
      success: (res) => {
        if (showLoading) {
          uni.hideLoading()
        }

        if (res.statusCode === 200) {
          resolve(res.tempFilePath)
        } else {
          uni.showToast({
            title: '下载失败',
            icon: 'none'
          })
          reject(new Error('下载失败'))
        }
      },
      fail: (error) => {
        if (showLoading) {
          uni.hideLoading()
        }
        handleRequestError(error)
        reject(error)
      }
    })
  })
}

export default {
  request,
  get,
  post,
  put,
  del,
  patch,
  uploadFile,
  downloadFile
}
