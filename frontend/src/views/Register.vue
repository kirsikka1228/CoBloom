<template>
  <div class="auth-card">
    <h1 class="page-title">创建账号</h1>
    <p class="muted">你的记录只属于你，后续内容会按用户隔离。</p>
    <el-form :model="form" label-position="top" @keyup.enter="submit">
      <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
      <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
      <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
      <el-button type="primary" style="width:100%" :loading="loading" @click="submit">注册并进入</el-button>
    </el-form>
    <router-link to="/login">已有账号？登录</router-link>
  </div>
</template>
<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
const form = reactive({ username: '', nickname: '', password: '' })
const loading = ref(false)
const router = useRouter()
const auth = useAuthStore()
async function submit() {
  if (loading.value) return
  loading.value = true
  try {
    await auth.register(form)
    router.push('/')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>
