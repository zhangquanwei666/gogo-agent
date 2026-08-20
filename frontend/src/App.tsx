import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import { getToken } from './api/request'

/**
 * 路由守卫：本地没 token 直接回登录页，不发请求。
 * token 是否真的有效由 HomePage 里的 /user/current 二次确认 ——
 * 本地判断只是省掉一次必然 401 的往返，不能当作安全边界。
 */
function RequireAuth({ children }: { children: React.ReactElement }) {
  return getToken() ? children : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route
          path="/"
          element={
            <RequireAuth>
              <HomePage />
            </RequireAuth>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
