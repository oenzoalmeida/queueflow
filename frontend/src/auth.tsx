import React, { createContext, useContext, useState } from 'react'
import { api, setSession, clearSession, getStoredUser, getToken, errMessage, type LoggedUser } from './api/client'

interface AuthCtx {
  user: LoggedUser | null
  login: (email: string, password: string) => Promise<LoggedUser>
  logout: () => void
}

const Ctx = createContext<AuthCtx>(null!)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<LoggedUser | null>(() => (getToken() ? getStoredUser() : null))

  const login = async (email: string, password: string) => {
    try {
      const { data } = await api.post('/auth/login', { email, password })
      const u: LoggedUser = { id: data.id, name: data.name, email: data.email, role: data.role }
      setSession(data.token, u)
      setUser(u)
      return u
    } catch (e) {
      throw new Error(errMessage(e))
    }
  }

  const logout = () => {
    clearSession()
    setUser(null)
    window.location.href = '/login'
  }

  return <Ctx.Provider value={{ user, login, logout }}>{children}</Ctx.Provider>
}

export const useAuth = () => useContext(Ctx)
