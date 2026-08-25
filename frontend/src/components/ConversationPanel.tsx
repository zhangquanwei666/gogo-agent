import type { ChatConversation } from '../types/chat'

interface Props {
  conversations: ChatConversation[]
  loading: boolean
  /** 当前正在看的会话，用来高亮 */
  activeId: string
  /** 正在拉历史消息的那条；不为 null 时整列禁用，挡住连点 */
  loadingId: string | null
  onSelect: (conversation: ChatConversation) => void
  /** 开一条新会话 */
  onNew: () => void
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

/**
 * 侧边的历史会话列表，数据来自 POST /api/v1/chat/conversation/list。
 *
 * <p>只负责渲染和把点击往上抛，拉历史消息、切当前会话都在 HomePage —— 和 ChatPanel 一样的分工。
 */
export default function ConversationPanel({
  conversations,
  loading,
  activeId,
  loadingId,
  onSelect,
  onNew,
}: Props) {
  const busy = loadingId !== null

  return (
    <aside className="side-panel">
      <div className="title">
        <h3>我的会话</h3>
        <div className="title-right">
          {!loading && <span className="count">共 {conversations.length} 条</span>}
          <button type="button" className="conv-new" disabled={busy} onClick={onNew}>
            + 新会话
          </button>
        </div>
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
              type="button"
              className={`conv-item${c.conversationId === activeId ? ' active' : ''}`}
              disabled={busy}
              onClick={() => onSelect(c)}
            >
              <div className="t">{c.title}</div>
              <div className="d">
                {c.conversationId === loadingId ? '加载中…' : formatTime(c.updatedTime)}
              </div>
            </button>
          ))}
        </div>
      )}
    </aside>
  )
}
