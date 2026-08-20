import { useEffect, useRef, useState } from 'react'
import type { UserCurrentResp } from '../types/user'

const NAV_ITEMS = ['首页', '机票', '酒店', '用车', '差旅审批', '费用报销']

interface Props {
  user: UserCurrentResp
  onLogout: () => void
  /** 点了还没做的功能时提示一下，不要点了没反应 */
  onNotReady: (name: string) => void
}

export default function AppHeader({ user, onLogout, onNotReady }: Props) {
  const [menuOpen, setMenuOpen] = useState(false)
  const [activeNav, setActiveNav] = useState('首页')
  const userRef = useRef<HTMLDivElement>(null)

  // 点击外部关闭下拉菜单
  useEffect(() => {
    if (!menuOpen) return
    function onDocClick(e: MouseEvent) {
      if (userRef.current && !userRef.current.contains(e.target as Node)) {
        setMenuOpen(false)
      }
    }
    document.addEventListener('mousedown', onDocClick)
    return () => document.removeEventListener('mousedown', onDocClick)
  }, [menuOpen])

  const displayName = user.realName || user.username
  const initial = displayName.slice(0, 1).toUpperCase()

  return (
    <header className="app-header">
      <div className="app-logo">
        <div className="mark">G</div>
        <div className="name">GoGo 智能商旅</div>
      </div>

      <nav className="app-nav">
        {NAV_ITEMS.map((item) => (
          <button
            key={item}
            className={item === activeNav ? 'active' : ''}
            onClick={() => {
              if (item === '首页') {
                setActiveNav(item)
              } else {
                onNotReady(item)
              }
            }}
          >
            {item}
          </button>
        ))}
      </nav>

      <div className="app-user" ref={userRef}>
        <button className="trigger" onClick={() => setMenuOpen((v) => !v)}>
          <span className="avatar">{initial}</span>
          <span className="uname">{displayName}</span>
          <span className="caret">▾</span>
        </button>

        {menuOpen && (
          <div className="user-menu">
            <div className="meta">
              <div className="n">{displayName}</div>
              {user.email && <div className="e">{user.email}</div>}
              <span className="role-tag">{user.role}</span>
            </div>
            <button onClick={() => onNotReady('个人中心')}>个人中心</button>
            <button onClick={() => onNotReady('我的订单')}>我的订单</button>
            <button className="danger" onClick={onLogout}>
              退出登录
            </button>
          </div>
        )}
      </div>
    </header>
  )
}
