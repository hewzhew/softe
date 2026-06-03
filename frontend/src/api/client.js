import axios from 'axios'

export const http = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 8000
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
