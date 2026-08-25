import { API_PREFIX, post } from './request'
import type { ChatConversationListResp, ChatSendReq, ChatSendResp } from '../types/chat'

/** 查询当前登录用户的全部会话，userId 由后端从 token 取，不用传 */
export function listConversation() {
  return post<ChatConversationListResp>(`${API_PREFIX}/chat/conversation/list`, {})
}

/**
 * 发送一条消息，等智能体回复。
 *
 * <p>userId 由后端从 token 取，不用传；sessionId 由前端生成，
 * 后端发现库里没有这个会话会自动创建，所以不需要先调「创建会话」。
 *
 * <p>这个请求可能要等好几秒 —— 链路里最多有改写、意图识别、主智能体三次模型调用，
 * 调用方务必给用户一个等待状态。
 */
export function sendMessage(sessionId: string, content: string) {
  const body: ChatSendReq = { sessionId, content }
  return post<ChatSendResp>(`${API_PREFIX}/chat/send`, body)
}
