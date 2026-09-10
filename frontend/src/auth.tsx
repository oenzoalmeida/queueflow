import React, { createContext, useContext, useEffect, useState } from 'react'
import { api, errMessage, type LoggedUser } from './api/client'

interface AuthCtx {
  user: LoggedUser | null
  loading: boolean
  login: (email: string, password: string) => Promise<LoggedUser>
  logout: () => Promise<void>
}

const Ctx = createContext<AuthCtx>(null!)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<LoggedUser | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    api
      .get<LoggedUser>('/auth/me')
      .then(({ data }) => {
        if (active) setUser(data)
      })
      .catch(() => {
        if (active) setUser(null)
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [])

  const login = async (email: string, password: string) => {
    try {
      const { data } = await api.post<LoggedUser>('/auth/login', { email, password })
      setUser(data)
      return data
    } catch (e) {
      throw new Error(errMessage(e))
    }
  }

  const logout = async () => {
    try {
      await api.post('/auth/logout')
    } finally {
      setUser(null)
      window.location.href = '/login'
    }
  }

  return <Ctx.Provider value={{ user, loading, login, logout }}>{children}</Ctx.Provider>
}

export const useAuth = () => useContext(Ctx)
