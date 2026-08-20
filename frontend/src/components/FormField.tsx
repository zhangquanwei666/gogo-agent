import { useState } from 'react'

const EyeOpen = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
    <circle cx="12" cy="12" r="3" />
  </svg>
)

const EyeOff = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
    <line x1="1" y1="1" x2="23" y2="23" />
  </svg>
)

const ClearIcon = () => (
  <svg viewBox="0 0 24 24" fill="currentColor">
    <path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm3.6 13.2-1.4 1.4L12 13.4l-2.2 2.2-1.4-1.4L10.6 12 8.4 9.8l1.4-1.4L12 10.6l2.2-2.2 1.4 1.4L13.4 12l2.2 3.2z" />
  </svg>
)

interface Props {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  placeholder?: string
  /** 密码框会渲染小眼睛切换明文 */
  type?: 'text' | 'email' | 'password'
  required?: boolean
  error?: string
  autoComplete?: string
  /** 挂在输入框和错误信息之间，用来放密码强度条 */
  extra?: React.ReactNode
}

/**
 * 表单字段。
 * 内置清空按钮、密码可见切换、错误态样式，跟 auth.css 里的 .field / .control 对应。
 */
export default function FormField({
  id,
  label,
  value,
  onChange,
  placeholder,
  type = 'text',
  required = false,
  error,
  autoComplete,
  extra,
}: Props) {
  const isPassword = type === 'password'
  const [revealed, setRevealed] = useState(false)

  // 明文显示时把 type 换成 text，浏览器才不会继续掩码
  const inputType = isPassword ? (revealed ? 'text' : 'password') : type

  return (
    <div className={`field${error ? ' has-error' : ''}`}>
      <label htmlFor={id}>
        {label}
        {required && <span className="req">*</span>}
      </label>

      <div className={`control${isPassword ? ' has-eye' : ''}`}>
        <input
          id={id}
          type={inputType}
          value={value}
          placeholder={placeholder}
          autoComplete={autoComplete}
          onChange={(e) => onChange(e.target.value)}
        />

        {value.length > 0 && (
          <button
            type="button"
            className="icon-btn btn-clear"
            aria-label="清空"
            // 用 onMouseDown 阻止默认行为，避免点击时输入框先失焦
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => onChange('')}
          >
            <ClearIcon />
          </button>
        )}

        {isPassword && (
          <button
            type="button"
            className="icon-btn btn-eye"
            aria-label={revealed ? '隐藏密码' : '显示密码'}
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => setRevealed((v) => !v)}
          >
            {revealed ? <EyeOpen /> : <EyeOff />}
          </button>
        )}
      </div>

      {extra}
      {error && <div className="err-msg">{error}</div>}
    </div>
  )
}
