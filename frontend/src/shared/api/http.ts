import axios from 'axios'
import type { ApiEnvelope } from './contracts'

const DEFAULT_LOCAL_USER = 'local-finance'
function normalizeUserId(value?: string | null) {
  const userId = value?.trim()
  return !userId || userId === 'demo-finance' ? DEFAULT_LOCAL_USER : userId
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  config.headers = config.headers || {}
  config.headers['X-User-Id'] = normalizeUserId(localStorage.getItem('amoeba.user'))
  return config
})

export async function unwrap<T>(request: Promise<{ data: ApiEnvelope<T> }>): Promise<T> {
  const response = await request
  if (response.data.code !== '0' && response.data.code !== 'OK') throw new Error(response.data.message || '接口调用失败')
  return response.data.data
}
