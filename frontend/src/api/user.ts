import { post } from './request'
import type {
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
