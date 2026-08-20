import type { BaseResponse } from '../types/user'

const TOKEN_KEY = 'gogo_token'
const TOKEN_NAME_KEY = 'gogo_token_name'

/**
 * 业务异常。
 * 后端 HTTP 状态码始终是 200，靠响应体里的 code 区分成败，
 * 所以这里把 code !== 200 的情况统一转成异常抛出，页面用 code 决定怎么展示。
 */
export class ApiError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    // 隐私模式下 localStorage 可能不可用
    return null
  }
}

export function saveToken(tokenName: string, tokenValue: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, tokenValue)
    localStorage.setItem(TOKEN_NAME_KEY, tokenName)
  } catch {
    // 存不了就算了，不影响本次会话
  }
}

export async function post<T extends BaseResponse>(url: string, body: unknown): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json; charset=utf-8',
  }

  // 已登录时带上 token，登录注册接口本身不需要，但后续接口复用这个方法
  const token = getToken()
  const tokenName = localStorage.getItem(TOKEN_NAME_KEY)
  if (token && tokenName) {
    headers[tokenName] = token
  }

  let res: Response
  try {
    res = await fetch(url, { method: 'POST', headers, body: JSON.stringify(body) })
  } catch {
    throw new ApiError(-1, '网络异常，请确认后端服务已启动')
  }

  let data: T
  try {
    data = (await res.json()) as T
  } catch {
    throw new ApiError(res.status, `服务返回的不是合法 JSON（HTTP ${res.status}）`)
  }

  if (data.code !== 200) {
    throw new ApiError(data.code, data.msg || '请求失败')
  }
  return data
}
