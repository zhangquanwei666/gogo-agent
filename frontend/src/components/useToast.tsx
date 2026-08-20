import { useCallback, useEffect, useRef, useState } from 'react'

type ToastType = 'error' | 'success'

interface ToastState {
  message: string
  type: ToastType
  visible: boolean
}

/**
 * 顶部提示条。
 * 返回 [toast 元素, 触发函数]，页面把元素渲染出来、需要时调触发函数即可。
 */
export function useToast(duration = 3000) {
  const [state, setState] = useState<ToastState>({
    message: '',
    type: 'error',
    visible: false,
  })
  const timer = useRef<number | undefined>(undefined)

  const show = useCallback(
    (message: string, type: ToastType = 'error') => {
      setState({ message, type, visible: true })
      window.clearTimeout(timer.current)
      timer.current = window.setTimeout(() => {
        // 只关可见性，保留文案，否则收起动画期间字会先消失
        setState((s) => ({ ...s, visible: false }))
      }, duration)
    },
    [duration],
  )

  // 组件卸载时清掉定时器，避免在已卸载的组件上 setState
  useEffect(() => () => window.clearTimeout(timer.current), [])

  const element = (
    <div className={`toast ${state.type}${state.visible ? ' show' : ''}`}>{state.message}</div>
  )

  return [element, show] as const
}
