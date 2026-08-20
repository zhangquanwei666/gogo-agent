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

export function clearToken(): void {
  try {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(TOKEN_NAME_KEY)
  } catch {
    // 存不了也删不了，忽略
  }
}

/** 组装请求头，已登录时带上 token */
function buildHeaders(withBody: boolean): Record<string, string> {
  const headers: Record<string, string> = {}
  if (withBody) {
    headers['Content-Type'] = 'application/json; charset=utf-8'
  }
  const token = getToken()
  const tokenName = localStorage.getItem(TOKEN_NAME_KEY)
  if (token && tokenName) {
    headers[tokenName] = token
  }
  return headers
}

async function request<T extends BaseResponse>(url: string, init: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await fetch(url, init)
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

export async function post<T extends BaseResponse>(url: string, body: unknown): Promise<T> {
  return request<T>(url, {
    method: 'POST',
    headers: buildHeaders(true),
    body: JSON.stringify(body),
  })
}

export async function get<T extends BaseResponse>(url: string): Promise<T> {
  return request<T>(url, { method: 'GET', headers: buildHeaders(false) })
}
