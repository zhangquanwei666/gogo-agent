import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import BrandPanel from '../components/BrandPanel'
import FormField from '../components/FormField'
import { useToast } from '../components/useToast'
import { login } from '../api/user'
import { ApiError, saveToken } from '../api/request'
import { LoginType } from '../types/user'

const LAST_ACCOUNT_KEY = 'gogo_last_account'
const LAST_TYPE_KEY = 'gogo_last_type'

const FEATURES = [
  { num: '30%', label: '差旅成本节省' },
  { num: '7×24', label: '专属客服支持' },
  { num: '5min', label: '平均预订耗时' },
]

interface Errors {
  account?: string
  password?: string
}

export default function LoginPage() {
  const navigate = useNavigate()
  const [loginType, setLoginType] = useState<LoginType>(LoginType.USERNAME)
  const [account, setAccount] = useState('')
  const [password, setPassword] = useState('')
  const [remember, setRemember] = useState(false)
  const [errors, setErrors] = useState<Errors>({})
  const [loading, setLoading] = useState(false)
  const [toastEl, showToast] = useToast()

  const isEmailMode = loginType === LoginType.EMAIL

  /* 记住的账号，回填并把 Tab 切到当时用的方式 */
  useEffect(() => {
    const saved = localStorage.getItem(LAST_ACCOUNT_KEY)
    if (!saved) return
    if (localStorage.getItem(LAST_TYPE_KEY) === LoginType.EMAIL) {
      setLoginType(LoginType.EMAIL)
    }
    setAccount(saved)
    setRemember(true)
  }, [])

  /* Tab 切换：清掉账号和它的报错，密码保留 */
  function switchType(type: LoginType) {
    if (type === loginType) return
    setLoginType(type)
    setAccount('')
    setErrors((e) => ({ ...e, account: undefined }))
  }

  function validate(): boolean {
    const next: Errors = {}
    const value = account.trim()

    if (!value) {
      next.account = isEmailMode ? '请输入邮箱' : '请输入用户名'
    } else if (isEmailMode && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
      next.account = '邮箱格式不正确'
    }
    if (!password) {
      next.password = '请输入密码'
    }

    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!validate()) return

    setLoading(true)
    try {
      const data = await login({
        type: loginType,
        account: account.trim(),
        password,
      })

      saveToken(data.tokenName, data.tokenValue)
      if (remember) {
        localStorage.setItem(LAST_ACCOUNT_KEY, account.trim())
        localStorage.setItem(LAST_TYPE_KEY, loginType)
      } else {
        localStorage.removeItem(LAST_ACCOUNT_KEY)
        localStorage.removeItem(LAST_TYPE_KEY)
      }

      showToast('登录成功', 'success')
      // 让成功提示露个脸再跳，跳转用 replace，避免用户回退又落回登录页
      setTimeout(() => navigate('/', { replace: true }), 600)
    } catch (err) {
      const apiErr = err as ApiError
      // 401 是账号或密码错误，直接标在密码框上；其余走顶部提示
      if (apiErr.code === 401) {
        setErrors({ password: apiErr.message })
      } else {
        showToast(apiErr.message, 'error')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-wrap">
      {toastEl}

      <BrandPanel
        title={
          <>
            让每一次商务出行
            <br />
            都省心省钱
          </>
        }
        subtitle={
          <>
            机票、酒店、用车一站式预订，差旅标准自动管控，
            <br />
            费用报销全流程线上流转。
          </>
        }
        features={FEATURES}
      />

      <section className="form-panel">
        <div className="auth-card">
          <div className="card-head">
            <h2>欢迎回来</h2>
            <p>登录后即可开始预订与管理差旅</p>
          </div>

          {/* 登录方式切换，对应后端的 type 字段 */}
          <div className="tabs">
            <button
              type="button"
              className={`tab${!isEmailMode ? ' active' : ''}`}
              onClick={() => switchType(LoginType.USERNAME)}
            >
              用户名登录
            </button>
            <button
              type="button"
              className={`tab${isEmailMode ? ' active' : ''}`}
              onClick={() => switchType(LoginType.EMAIL)}
            >
              邮箱登录
            </button>
          </div>

          <form onSubmit={handleSubmit} noValidate>
            <FormField
              id="account"
              label={isEmailMode ? '邮箱' : '用户名'}
              type={isEmailMode ? 'email' : 'text'}
              placeholder={isEmailMode ? '请输入邮箱' : '请输入用户名'}
              autoComplete="username"
              required
              value={account}
              error={errors.account}
              onChange={(v) => {
                setAccount(v)
                setErrors((e) => ({ ...e, account: undefined }))
              }}
            />

            <FormField
              id="password"
              label="密码"
              type="password"
              placeholder="请输入密码"
              autoComplete="current-password"
              required
              value={password}
              error={errors.password}
              onChange={(v) => {
                setPassword(v)
                setErrors((e) => ({ ...e, password: undefined }))
              }}
            />

            <div className="row-between">
              <label>
                <input
                  type="checkbox"
                  checked={remember}
                  onChange={(e) => setRemember(e.target.checked)}
                />
                记住账号
              </label>
              <button
                type="button"
                className="link"
                onClick={() => showToast('请联系企业管理员重置密码', 'error')}
              >
                忘记密码？
              </button>
            </div>

            <button type="submit" className="btn-submit" disabled={loading}>
              {loading && <span className="spinner" />}
              <span>{loading ? '处理中...' : '登 录'}</span>
            </button>
          </form>

          <p className="foot-tip">
            还没有账号？<Link className="link" to="/register">立即注册</Link>
          </p>
        </div>
      </section>
    </div>
  )
}
