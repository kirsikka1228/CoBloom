<template>
  <div v-if="qa">
    <div class="page-head">
      <h1 class="page-title">Chat Detail</h1>
      <div class="toolbar">
        <el-button class="handwritten-action" @click="$router.back()">Back</el-button>
        <el-button type="danger" :loading="deleting" @click="remove">Delete</el-button>
      </div>
    </div>
    <div class="record-card">
      <h2>{{ qa.question }}</h2>
      <p class="muted">{{ qa.createdAt }}</p>
      <p class="qa-answer">{{ qa.answer }}</p>
    </div>
    <h2>Sources</h2>
    <div v-for="ref in qa.references" :key="ref.recordId + ref.chunkText" class="record-card">
      <router-link :to="`/records/${ref.recordId}`">{{ ref.recordTitle }}</router-link>
      <p>{{ ref.chunkText }}</p>
      <el-tag>Similarity {{ ref.similarity }}</el-tag>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../api/http'

const route = useRoute()
const router = useRouter()
const qa = ref(null)
const deleting = ref(false)

onMounted(async () => {
  qa.value = await http.get(`/qa/history/${route.params.id}`)
})

async function remove() {
  try {
    await ElMessageBox.confirm('Delete this chat record? This action cannot be undone.')
  } catch (action) {
    if (action !== 'cancel' && action !== 'close') {
      ElMessage.error('Unable to open the delete confirmation')
    }
    return
  }
  deleting.value = true
  try {
    await http.delete(`/qa/history/${route.params.id}`)
    ElMessage.success('Chat record deleted')
    router.push('/qa/history')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    deleting.value = false
  }
}
</script>
