import { useEffect, useRef } from 'react'
import type { ChatBubble } from '../types/chat'

interface Props {
  messages: ChatBubble[]
  /** 正在等回复，输入框和发送按钮都要禁掉，避免同一会话并发两轮 */
  sending: boolean
  ask: string
  onAskChange: (value: string) => void
  onSend: () => void
  /** 快捷提问，点了直接发出去 */
  quickAsks: string[]
  onQuickAsk: (value: string) => void
}

/**
 * 对话面板：消息列表 + 输入框。
 *
 * <p>只负责渲染，会话状态和接口调用都在 HomePage —— 和 ConversationPanel 一样的分工。
 * 这样组件不用知道 token 失效要跳登录页这类页面级的事。
 */
export default function ChatPanel({
  messages,
  sending,
  ask,
  onAskChange,
  onSend,
  quickAsks,
  onQuickAsk,
}: Props) {
  const listRef = useRef<HTMLDivElement>(null)

  /* 新消息进来后滚到底。依赖里放长度就够了：只有增删才需要滚，改内容不需要 */
  useEffect(() => {
    const el = listRef.current
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  }, [messages.length, sending])

  return (
    <section className="agent-card">
      <div className="head">
        <span className="dot">AI</span>
        <h2>智能助手</h2>
      </div>
      <p className="tip">用一句话描述行程，助手会帮你查价、比价、走审批</p>

      {messages.length > 0 && (
        <div className="chat-list" ref={listRef}>
          {messages.map((m) => (
            <div key={m.id} className={`chat-row ${m.role}`}>
              <div className={`chat-bubble${m.error ? ' error' : ''}`}>
                {m.pending ? (
                  <span className="thinking">
                    <i />
                    <i />
                    <i />
                  </span>
                ) : (
                  m.content
                )}
              </div>
              {/* 意图来源只在有值时露出来，用于开发期确认这句话走没走到大模型 */}
              {m.intentSource && !m.error && (
                <span className="chat-meta">{m.intentSource}</span>
              )}
            </div>
          ))}
        </div>
      )}

      <div className="agent-input">
        <input
          value={ask}
          disabled={sending}
          placeholder={sending ? '正在思考…' : '例如：帮我订下周三去深圳的机票，下午出发'}
          onChange={(e) => onAskChange(e.target.value)}
          onKeyDown={(e) => {
            // 输入法组合中的回车不算发送，否则中文还没上屏就被送出去了
            if (e.key === 'Enter' && !e.nativeEvent.isComposing) {
              onSend()
            }
          }}
        />
        <button onClick={onSend} disabled={sending || !ask.trim()}>
          {sending ? '思考中' : '发送'}
        </button>
      </div>

      {messages.length === 0 && (
        <div className="agent-chips">
          {quickAsks.map((q) => (
            <button key={q} disabled={sending} onClick={() => onQuickAsk(q)}>
              {q}
            </button>
          ))}
        </div>
      )}
    </section>
  )
}
