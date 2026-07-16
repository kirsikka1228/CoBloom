import axios from 'axios'

export const http = axios.create({ baseURL: '/api' })

http.interceptors.request.use(config => {
  const token = localStorage.getItem('cobloom_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  res => res.data,
  err => {
    const message = err.response?.data?.message || 'Request failed'
    if (isInvalidTokenError(err)) {
      localStorage.removeItem('cobloom_token')
      window.dispatchEvent(new CustomEvent('cobloom:auth-invalid'))
    }
    return Promise.reject(new Error(message))
  }
)

function isInvalidTokenError(err) {
  const status = err.response?.status
  return status === 401
}
