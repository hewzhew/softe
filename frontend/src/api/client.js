import axios from 'axios'
import { loadAuthSession } from '../utils/authSession.js'

export const http = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 8000
})

http.interceptors.request.use((config) => {
  const token = loadAuthSession()?.token
  if (token) {
    config.headers = config.headers || {}
    config.headers['X-Session-Token'] = token
  }
  return config
})

export async function unwrap(promise) {
  try {
    const response = await promise
    if (!response.data.success) {
      throw new Error(response.data.message || '请求失败')
    }
    return response.data.data
  } catch (error) {
    const message = error.response?.data?.message || error.message || '请求失败'
    throw new Error(message)
  }
}
