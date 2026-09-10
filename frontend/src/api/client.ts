import axios from 'axios'

export interface LoggedUser {
  id: number
  name: string
  email: string
  role: 'ADMIN' | 'ATTENDANT'
}

// In dev, VITE_API_URL is unset and requests go through the Vite proxy (relative '/api').
// In production, set VITE_API_URL to the deployed backend's origin (e.g. https://queueflow-backend.onrender.com).
const apiBase = import.meta.env.VITE_API_URL ?? ''

export const api = axios.create({
  baseURL: `${apiBase}/api`,
  withCredentials: true,
})

api.interceptors.response.use(
  (r) => r,
  (err) => {
    if (err.response?.status === 401 && window.location.pathname !== '/login') {
      if (!window.location.pathname.startsWith('/totem') && !window.location.pathname.startsWith('/display')) {
        window.location.href = '/login'
      }
    }
    return Promise.reject(err)
  },
)

export function errMessage(e: unknown): string {
  const anyE = e as { response?: { data?: { message?: string; fields?: Record<string, string> } } }
  if (anyE?.response?.data?.fields) {
    return Object.values(anyE.response.data.fields).join(' · ')
  }
  return anyE?.response?.data?.message ?? 'Erro inesperado. Tente novamente.'
}
