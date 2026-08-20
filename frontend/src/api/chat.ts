import { post } from './request'
import type { ChatConversationListResp } from '../types/chat'

/** 查询当前登录用户的全部会话，userId 由后端从 token 取，不用传 */
export function listConversation() {
  return post<ChatConversationListResp>('/chat/conversation/list', {})
}
