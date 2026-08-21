/** 跟后端 DTO 一一对应的类型定义 */

/** 后端 BaseResponse：code / msg 和业务字段平铺在同一层 */
export interface BaseResponse {
  code: number
  msg: string
}

/** 登录方式，对应后端 LoginTypeEnum */
export const LoginType = {
  USERNAME: 'USERNAME',
  EMAIL: 'EMAIL',
} as const

export type LoginType = (typeof LoginType)[keyof typeof LoginType]

/** POST /api/v1/auth/register 入参，对应 UserRegisterReqDTO */
export interface UserRegisterReq {
  username: string
  /** 选填，不填传 null，避免空串撞 uk_email 唯一索引 */
  email: string | null
  password: string
  realName: string | null
}

/** POST /api/v1/auth/register 出参，对应 UserRegisterRespDTO */
export interface UserRegisterResp extends BaseResponse {
  userId: string
  username: string
  email: string | null
  realName: string | null
  role: string
}

/** POST /api/v1/auth/login 入参，对应 UserLoginReqDTO */
export interface UserLoginReq {
  type: LoginType
  account: string
  password: string
}

/** POST /api/v1/auth/login 出参，对应 UserLoginRespDTO */
export interface UserLoginResp extends BaseResponse {
  type: string
  userId: string
  username: string
  email: string | null
  realName: string | null
  role: string
  /** token 的请求头名称，取自后端 sa-token 配置的 token-name */
  tokenName: string
  tokenValue: string
  /** 剩余有效期，单位秒，-1 表示永不过期 */
  tokenTimeout: number
}

/** GET /api/v1/user/current 出参，对应 UserCurrentRespDTO */
export interface UserCurrentResp extends BaseResponse {
  userId: string
  username: string
  email: string | null
  realName: string | null
  role: string
}
