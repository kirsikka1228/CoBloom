<template>
  <div>
    <div class="page-head">
      <h1 class="page-title">Chat History</h1>
      <el-button class="handwritten-action" @click="$router.push('/qa')">New Question</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div v-if="loading" class="record-card">Loading...</div>
    <el-empty v-else-if="!rows.length" description="No chat history yet. Ask a question first." />
    <div v-for="qa in rows" :key="qa.id" class="record-card qa-history-card" @click="$router.push(`/qa/history/${qa.id}`)">
      <div class="qa-history-main">
        <strong>{{ qa.question }}</strong>
        <p class="muted">{{ qa.createdAt }}</p>
        <p>{{ qa.answer }}</p>
      </div>
      <el-button type="danger" link :loading="deletingId === qa.id" @click.stop="remove(qa.id)">Delete</el-button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../api/http'

const rows = ref([])
const loading = ref(false)
const error = ref('')
const deletingId = ref(null)

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await http.get('/qa/history')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function remove(id) {
  try {
    await ElMessageBox.confirm('Delete this chat record? This action cannot be undone.')
  } catch (action) {
    if (action !== 'cancel' && action !== 'close') {
      ElMessage.error('Unable to open the delete confirmation')
    }
    return
  }
  deletingId.value = id
  try {
    await http.delete(`/qa/history/${id}`)
    rows.value = rows.value.filter(row => row.id !== id)
    ElMessage.success('Chat record deleted')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    deletingId.value = null
  }
}
</script>
