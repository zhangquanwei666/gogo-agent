import type { BaseResponse } from './user'

/** 会话列表里的单条记录，对应 ChatConversationDTO */
export interface ChatConversation {
  conversationId: string
  title: string
  createdTime: string
  updatedTime: string
}

/** POST /chat/conversation/list 出参，对应 ChatConversationListRespDTO */
export interface ChatConversationListResp extends BaseResponse {
  total: number
  conversations: ChatConversation[]
}
