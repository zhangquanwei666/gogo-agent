import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import AppHeader from '../components/AppHeader'
import BookingPanel from '../components/BookingPanel'
import ConversationPanel from '../components/ConversationPanel'
import { useToast } from '../components/useToast'
import { getCurrentUser, logout as logoutApi } from '../api/user'
import { listConversation } from '../api/chat'
import { ApiError, clearToken } from '../api/request'
import type { UserCurrentResp } from '../types/user'
import type { ChatConversation } from '../types/chat'
import '../styles/home.css'

/** 快捷提问，点了填进输入框 */
const QUICK_ASKS = [
  '帮我订下周一北京到上海的机票',
  '上海出差三天，推荐符合差旅标准的酒店',
  '我的差旅额度还剩多少',
  '上个月的打车发票怎么报销',
]

export default function HomePage() {
  const navigate = useNavigate()
  const [user, setUser] = useState<UserCurrentResp | null>(null)
  const [conversations, setConversations] = useState<ChatConversation[]>([])
  const [convLoading, setConvLoading] = useState(true)
  const [ask, setAsk] = useState('')
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

          <section className="agent-card">
            <div className="head">
              <span className="dot">AI</span>
              <h2>智能助手</h2>
            </div>
            <p className="tip">用一句话描述行程，助手会帮你查价、比价、走审批</p>

            <div className="agent-input">
              <input
                value={ask}
                placeholder="例如：帮我订下周三去深圳的机票，下午出发"
                onChange={(e) => setAsk(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') notReady('对话')
                }}
              />
              <button onClick={() => notReady('对话')}>发送</button>
            </div>

            <div className="agent-chips">
              {QUICK_ASKS.map((q) => (
                <button key={q} onClick={() => setAsk(q)}>
                  {q}
                </button>
              ))}
            </div>
          </section>
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
