import type { BaseResponse } from './user'

/** 会话列表里的单条记录，对应 ChatConversationDTO */
export interface ChatConversation {
  conversationId: string
  title: string
  createdTime: string
  updatedTime: string
}

/** POST /api/v1/chat/conversation/list 出参，对应 ChatConversationListRespDTO */
export interface ChatConversationListResp extends BaseResponse {
  total: number
  conversations: ChatConversation[]
}

/** POST /api/v1/chat/send 入参，对应后端 ChatRequest */
export interface ChatSendReq {
  /** 会话 ID。由前端生成并在整轮会话里保持不变，后端发现库里没有会自动建会话 */
  sessionId: string
  /** 用户这一轮说的话 */
  content: string
}

/** POST /api/v1/chat/send 出参，对应后端 ChatResponse */
export interface ChatSendResp extends BaseResponse {
  sessionId: string
  /** 回复落库后的消息 ID，后续点赞点踩要用 */
  messageId: string
  /** 智能体的回复正文 */
  reply: string
  /** 回复来自哪个智能体 */
  agentName: string
  /** 本轮识别到的主意图，如 greeting / flight_search */
  intent: string
  /** 意图由哪一级给出：L1 规则 / L2 向量 / L3 大模型 */
  intentSource: string
  /** 本轮是否被中断 */
  interrupted: boolean
}

/**
 * 界面上的一条消息。
 * 只服务于渲染，不和后端实体一一对应 —— 后端那张表还有 feedback、extra 等字段，
 * 界面这一层用不上，混进来只会让组件被迫关心存储细节。
 */
export interface ChatBubble {
  /** 本地生成，React 列表的 key。后端返回 messageId 后不覆盖它，避免整条重新挂载 */
  id: string
  role: 'user' | 'agent'
  content: string
  /** 智能体消息落库后的 ID，点赞点踩用；用户消息为空 */
  messageId?: string
  /** 意图来源，只在开发期展示，用来一眼看出这句话有没有走到大模型 */
  intentSource?: string
  /** 这条是不是「正在思考」的占位气泡 */
  pending?: boolean
  /** 这条是不是出错提示 */
  error?: boolean
}
