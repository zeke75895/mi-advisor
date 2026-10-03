import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { authApi, hasToken, saveToken, setUnauthorizedHandler } from '../api/client'

const AuthContext = createContext(null)
const EMAIL_KEY = 'cc_email'

function readEmail() {
  try {
    return localStorage.getItem(EMAIL_KEY)
  } catch {
    return null
  }
}

export function AuthProvider({ children }) {
  const [email, setEmail] = useState(() => (hasToken() ? readEmail() : null))
  const [signedIn, setSignedIn] = useState(hasToken)

  const finish = useCallback((res) => {
    saveToken(res.token)
    try {
      localStorage.setItem(EMAIL_KEY, res.user.email)
    } catch {
      // ignore
    }
    setEmail(res.user.email)
    setSignedIn(true)
  }, [])

  const logout = useCallback(() => {
    saveToken(null)
    setEmail(null)
    setSignedIn(false)
  }, [])

  useEffect(() => setUnauthorizedHandler(logout), [logout])

  const value = useMemo(
    () => ({
      signedIn,
      email,
      login: async (e, p) => finish(await authApi.login(e, p)),
      register: async (e, p) => finish(await authApi.register(e, p)),
      logout,
    }),
    [signedIn, email, finish, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  return useContext(AuthContext)
}
