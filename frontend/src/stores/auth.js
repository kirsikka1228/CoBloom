import { defineStore } from 'pinia'
import { http } from '../api/http'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null,
    token: localStorage.getItem('cobloom_token') || null,
    sessionError: null
  }),
  actions: {
    async login(payload) {
      const data = await http.post('/auth/login', payload)
      this.setSession(data)
    },
    async register(payload) {
      const data = await http.post('/auth/register', payload)
      this.setSession(data)
    },
    async fetchMe() {
      if (!this.token) return
      this.user = await http.get('/auth/me')
    },
    setSession(data) {
      if (!data?.token) throw new Error('登录响应缺少 token')
      this.token = data.token
      this.user = data.user
      this.sessionError = null
      localStorage.setItem('cobloom_token', data.token)
    },
    clearSession(reason = null) {
      this.token = null
      this.user = null
      this.sessionError = reason instanceof Error ? reason.message : null
      localStorage.removeItem('cobloom_token')
    },
    async logout() {
      let remoteError = null
      try {
        await http.post('/auth/logout')
      } catch (error) {
        remoteError = error
      } finally {
        // Local JWT removal is authoritative. Preserve the remote failure for
        // diagnostics without leaving the user signed in on this device.
        this.clearSession(remoteError)
      }
    }
  }
})
