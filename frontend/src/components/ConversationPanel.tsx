import type { ChatConversation } from '../types/chat'

interface Props {
  conversations: ChatConversation[]
  loading: boolean
  onSelect: (conversation: ChatConversation) => void
}

/** 把后端返回的 LocalDateTime 字符串显示成「今天 14:30」这种相对时间 */
function formatTime(value: string): string {
  if (!value) return ''
  // 后端序列化出来是 2026-08-20T14:30:00，Safari 对带 T 的格式挑剔，统一换成空格
  const date = new Date(value.replace('T', ' ').replace(/-/g, '/'))
  if (Number.isNaN(date.getTime())) return value

  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const hm = `${pad(date.getHours())}:${pad(date.getMinutes())}`

  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  if (sameDay) return `今天 ${hm}`

  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  const isYesterday =
    date.getFullYear() === yesterday.getFullYear() &&
    date.getMonth() === yesterday.getMonth() &&
    date.getDate() === yesterday.getDate()
  if (isYesterday) return `昨天 ${hm}`

  if (date.getFullYear() === now.getFullYear()) {
    return `${date.getMonth() + 1}月${date.getDate()}日 ${hm}`
  }
  return `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())}`
}

/** 侧边的历史会话列表，数据来自 POST /api/v1/chat/conversation/list */
export default function ConversationPanel({ conversations, loading, onSelect }: Props) {
  return (
    <aside className="side-panel">
      <div className="title">
        <h3>我的会话</h3>
        {!loading && <span className="count">共 {conversations.length} 条</span>}
      </div>

      {loading ? (
        <div className="side-loading">加载中...</div>
      ) : conversations.length === 0 ? (
        <div className="side-empty">
          <div className="icon">💬</div>
          还没有会话记录
          <br />
          在上方问一句就能开始
        </div>
      ) : (
        <div className="conv-list">
          {conversations.map((c) => (
            <button
              key={c.conversationId}
              className="conv-item"
              onClick={() => onSelect(c)}
            >
              <div className="t">{c.title}</div>
              <div className="d">{formatTime(c.updatedTime)}</div>
            </button>
          ))}
        </div>
      )}
    </aside>
  )
}
