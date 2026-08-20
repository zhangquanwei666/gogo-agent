import { get, post } from './request'
import type {
  BaseResponse,
  UserCurrentResp,
  UserLoginReq,
  UserLoginResp,
  UserRegisterReq,
  UserRegisterResp,
} from '../types/user'

/** 用户注册 */
export function register(req: UserRegisterReq) {
  return post<UserRegisterResp>('/user/register', req)
}

/** 用户登录，account 按 type 解释成用户名或邮箱 */
export function login(req: UserLoginReq) {
  return post<UserLoginResp>('/user/login', req)
}

/** 查询当前登录用户，身份取自请求头里的 token */
export function getCurrentUser() {
  return get<UserCurrentResp>('/user/current')
}

/** 退出登录 */
export function logout() {
  return post<BaseResponse>('/user/logout', {})
}
