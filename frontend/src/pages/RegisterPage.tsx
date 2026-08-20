import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import BrandPanel from '../components/BrandPanel'
import FormField from '../components/FormField'
import { useToast } from '../components/useToast'
import { register } from '../api/user'
import type { ApiError } from '../api/request'
import type { UserRegisterResp } from '../types/user'

const FEATURES = [
  { num: '10万+', label: '合作酒店' },
  { num: '全球', label: '航线覆盖' },
  { num: '0 元', label: '平台使用费' },
]

interface Errors {
  username?: string
  email?: string
  realName?: string
  password?: string
  confirm?: string
}

/** 密码强度 0~3：长度、是否含字母、是否含数字或符号 */
function passwordStrength(value: string): number {
  if (!value) return 0
  if (value.length < 6) return 1
  let score = 0
  if (value.length >= 8) score++
  if (/[a-zA-Z]/.test(value)) score++
  if (/[\d\W]/.test(value)) score++
  return score
}

export default function RegisterPage() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [realName, setRealName] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [agreed, setAgreed] = useState(false)
  const [errors, setErrors] = useState<Errors>({})
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<UserRegisterResp | null>(null)
  const [toastEl, showToast] = useToast()

  const strength = passwordStrength(password)

  /** 校验规则跟后端和 DDL 的字段长度对齐 */
  function validate(): boolean {
    const next: Errors = {}

    const name = username.trim()
    if (!name) {
      next.username = '请输入用户名'
    } else if (name.length < 4 || name.length > 64) {
      next.username = '用户名长度需在 4-64 位之间'
    }

    // 邮箱选填，填了才校验
    const mail = email.trim()
    if (mail) {
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(mail)) {
        next.email = '邮箱格式不正确'
      } else if (mail.length > 128) {
        next.email = '邮箱长度不能超过 128 位'
      }
    }

    if (realName.trim().length > 64) {
      next.realName = '姓名长度不能超过 64 位'
    }

    if (!password) {
      next.password = '请输入密码'
    } else if (password.length < 8) {
      next.password = '密码至少 8 位'
    }

    if (!confirm) {
      next.confirm = '请再次输入密码'
    } else if (confirm !== password) {
      next.confirm = '两次输入的密码不一致'
    }

    setErrors(next)

    if (!agreed) {
      showToast('请先阅读并同意服务协议', 'error')
      return false
    }
    return Object.keys(next).length === 0
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!validate()) return

    setResult(null)
    setLoading(true)
    try {
      const data = await register({
        username: username.trim(),
        // 不填就传 null，后端会存成 null，避免空串撞 uk_email 唯一索引
        email: email.trim() || null,
        password,
        realName: realName.trim() || null,
      })

      showToast('注册成功，正在跳转登录...', 'success')
      setResult(data)
      setTimeout(() => navigate('/login'), 1500)
    } catch (err) {
      const apiErr = err as ApiError
      // 409 是用户名或邮箱重复，定位到具体的输入框
      if (apiErr.code === 409) {
        if (apiErr.message.includes('邮箱')) {
          setErrors({ email: apiErr.message })
        } else {
          setErrors({ username: apiErr.message })
        }
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
            开启企业差旅的
            <br />
            数字化管理
          </>
        }
        subtitle={
          <>
            注册后即可创建差旅申请、预订机酒用车，
            <br />
            所有行程与费用集中在一个平台。
          </>
        }
        features={FEATURES}
      />

      <section className="form-panel">
        <div className="auth-card">
          <div className="card-head">
            <h2>创建账号</h2>
            <p>填写以下信息完成注册</p>
          </div>

          <form onSubmit={handleSubmit} noValidate>
            <FormField
              id="username"
              label="用户名"
              placeholder="4-64 位，登录时使用"
              autoComplete="username"
              required
              value={username}
              error={errors.username}
              onChange={(v) => {
                setUsername(v)
                setErrors((e) => ({ ...e, username: undefined }))
              }}
            />

            <FormField
              id="email"
              label="邮箱"
              type="email"
              placeholder="选填，填了可用邮箱登录"
              autoComplete="email"
              value={email}
              error={errors.email}
              onChange={(v) => {
                setEmail(v)
                setErrors((e) => ({ ...e, email: undefined }))
              }}
            />

            <FormField
              id="realName"
              label="真实姓名"
              placeholder="选填，用于机票酒店预订"
              autoComplete="name"
              value={realName}
              error={errors.realName}
              onChange={(v) => {
                setRealName(v)
                setErrors((e) => ({ ...e, realName: undefined }))
              }}
            />

            <FormField
              id="password"
              label="密码"
              type="password"
              placeholder="至少 8 位，建议字母加数字"
              autoComplete="new-password"
              required
              value={password}
              error={errors.password}
              onChange={(v) => {
                setPassword(v)
                setErrors((e) => ({ ...e, password: undefined }))
              }}
              extra={
                <div className={`strength${strength ? ` s${strength}` : ''}`}>
                  <span />
                  <span />
                  <span />
                </div>
              }
            />

            <FormField
              id="confirm"
              label="确认密码"
              type="password"
              placeholder="请再次输入密码"
              autoComplete="new-password"
              required
              value={confirm}
              error={errors.confirm}
              onChange={(v) => {
                setConfirm(v)
                setErrors((e) => ({ ...e, confirm: undefined }))
              }}
            />

            <label className="agree">
              <input
                type="checkbox"
                checked={agreed}
                onChange={(e) => setAgreed(e.target.checked)}
              />
              <span>
                我已阅读并同意 <a href="#agreement">《服务协议》</a> 和{' '}
                <a href="#privacy">《隐私政策》</a>
              </span>
            </label>

            <button type="submit" className="btn-submit" disabled={loading}>
              {loading && <span className="spinner" />}
              <span>{loading ? '处理中...' : '注 册'}</span>
            </button>
          </form>

          {result && (
            <div className="result-box">
              <b>注册成功</b>
              <br />
              userId：{result.userId}
              <br />
              用户名：{result.username}
              <br />
              角色：{result.role}
            </div>
          )}

          <p className="foot-tip">
            已有账号？<Link className="link" to="/login">直接登录</Link>
          </p>
        </div>
      </section>
    </div>
  )
}
