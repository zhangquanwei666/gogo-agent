import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import AppHeader from '../components/AppHeader'
import BookingPanel from '../components/BookingPanel'
import ConversationPanel from '../components/ConversationPanel'
import ChatPanel from '../components/ChatPanel'
import { useToast } from '../components/useToast'
import { getCurrentUser, logout as logoutApi } from '../api/user'
import { listConversation, sendMessage } from '../api/chat'
import { ApiError, clearToken } from '../api/request'
import type { UserCurrentResp } from '../types/user'
import type { ChatBubble, ChatConversation } from '../types/chat'
import '../styles/home.css'

/** 快捷提问，点了填进输入框 */
const QUICK_ASKS = [
  '帮我订下周一北京到上海的机票',
  '上海出差三天，推荐符合差旅标准的酒店',
  '我的差旅额度还剩多少',
  '上个月的打车发票怎么报销',
]

/** 生成一个会话 ID。crypto.randomUUID 在非 HTTPS 的旧浏览器上没有，退回时间戳 + 随机数 */
function newSessionId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID().replace(/-/g, '')
  }
  return `${Date.now()}${Math.random().toString(36).slice(2, 10)}`
}

/** 本地气泡 ID，只用于 React 的 key，和后端的 messageId 无关 */
let bubbleSeq = 0
function nextBubbleId(): string {
  bubbleSeq += 1
  return `b${bubbleSeq}`
}

export default function HomePage() {
  const navigate = useNavigate()
  const [user, setUser] = useState<UserCurrentResp | null>(null)
  const [conversations, setConversations] = useState<ChatConversation[]>([])
  const [convLoading, setConvLoading] = useState(true)
  const [ask, setAsk] = useState('')
  const [messages, setMessages] = useState<ChatBubble[]>([])
  const [sending, setSending] = useState(false)
  /* 会话 ID 进页面就定下来，整轮对话共用一个；刷新页面等于开新会话 */
  const [sessionId] = useState(newSessionId)
  const [toastEl, showToast] = useToast()

  /** token 失效时统一处理：清掉本地 token 并回登录页 */
  const backToLogin = useCallback(() => {
    clearToken()
    navigate('/login', { replace: true })
  }, [navigate])

  /* 进页面先确认身份，401 直接踢回登录页 */
  useEffect(() => {
    let alive = true
    getCurrentUser()
      .then((data) => {
        if (alive) setUser(data)
      })
      .catch((err) => {
        const apiErr = err as ApiError
        if (apiErr.code === 401) {
          backToLogin()
        } else {
          showToast(apiErr.message, 'error')
        }
      })
    return () => {
      alive = false
    }
  }, [backToLogin, showToast])

  /* 身份确认之后再拉会话列表，避免未登录时白跑一次 401 */
  useEffect(() => {
    if (!user) return
    let alive = true
    listConversation()
      .then((data) => {
        if (alive) setConversations(data.conversations ?? [])
      })
      .catch((err) => {
        const apiErr = err as ApiError
        if (apiErr.code === 401) {
          backToLogin()
        } else {
          showToast(apiErr.message, 'error')
        }
      })
      .finally(() => {
        if (alive) setConvLoading(false)
      })
    return () => {
      alive = false
    }
  }, [user, backToLogin, showToast])

  /** 拉一次会话列表，首次发消息后侧边栏才会出现这个新会话 */
  const refreshConversations = useCallback(() => {
    listConversation()
      .then((data) => setConversations(data.conversations ?? []))
      .catch(() => {
        // 列表刷新失败不影响对话本身，静默即可 —— 下次进页面还会再拉
      })
  }, [])

  /**
   * 发一条消息。
   *
   * 先把用户气泡和一个「思考中」占位气泡推上去再发请求：这一轮可能要等好几秒
   * （链路里最多三次模型调用），没有即时反馈的话用户会以为点击没生效，接着重复点。
   */
  async function handleSend(text: string) {
    const content = text.trim()
    if (!content || sending) return

    const pendingId = nextBubbleId()
    setMessages((prev) => [
      ...prev,
      { id: nextBubbleId(), role: 'user', content },
      { id: pendingId, role: 'agent', content: '', pending: true },
    ])
    setAsk('')
    setSending(true)

    try {
      const data = await sendMessage(sessionId, content)
      // 用占位气泡的 id 定位替换，不是整列表重建 —— 期间用户又发了一条也不会错位
      setMessages((prev) =>
        prev.map((m) =>
          m.id === pendingId
            ? {
                ...m,
                pending: false,
                content: data.reply,
                messageId: data.messageId,
                intentSource: data.intentSource,
              }
            : m,
        ),
      )
      refreshConversations()
    } catch (err) {
      const apiErr = err as ApiError
      if (apiErr.code === 401) {
        backToLogin()
        return
      }
      // 失败的那条占位气泡就地变成错误提示，别让它一直转圈
      setMessages((prev) =>
        prev.map((m) =>
          m.id === pendingId
            ? { ...m, pending: false, error: true, content: apiErr.message }
            : m,
        ),
      )
      showToast(apiErr.message, 'error')
    } finally {
      setSending(false)
    }
  }

  async function handleLogout() {
    try {
      await logoutApi()
    } catch {
      // 后端登出失败也要让用户能走，本地 token 清掉就够了
    }
    backToLogin()
  }

  function notReady(name: string) {
    showToast(`${name}功能还在开发中`, 'error')
  }

  // 身份还没确认前不渲染页面骨架，避免闪一下又跳走
  if (!user) {
    return (
      <div className="page-loading">
        <span className="spinner" />
        加载中...
      </div>
    )
  }

  return (
    <div className="home">
      {toastEl}

      <AppHeader user={user} onLogout={handleLogout} onNotReady={notReady} />

      <div className="home-body">
        <main className="home-main">
          <BookingPanel
            userName={user.realName || user.username}
            onSearch={(label) => notReady(`${label}搜索`)}
          />

          <ChatPanel
            messages={messages}
            sending={sending}
            ask={ask}
            onAskChange={setAsk}
            onSend={() => handleSend(ask)}
            quickAsks={QUICK_ASKS}
            onQuickAsk={handleSend}
          />
        </main>

        <ConversationPanel
          conversations={conversations}
          loading={convLoading}
          onSelect={() => notReady('会话详情')}
        />
      </div>
    </div>
  )
}
