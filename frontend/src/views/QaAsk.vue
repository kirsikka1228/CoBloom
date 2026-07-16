<template>
  <div>
    <div class="page-head">
      <h1 class="page-title">Chat</h1>
      <el-button class="handwritten-action" @click="$router.push('/qa/history')">View History</el-button>
    </div>
    <el-input
      v-model="question"
      type="textarea"
      :rows="4"
      placeholder="For example: What progress did I make on projects this month?"
    />
    <el-button
      class="handwritten-action"
      style="margin-top:12px"
      :loading="loading"
      :disabled="loading || !question.trim()"
      @click="ask"
    >
      Ask From My Records
    </el-button>
    <div v-if="answer" class="record-card">
      <h2>Answer</h2>
      <p class="qa-answer">{{ answer.answer }}</p>
      <h3>Sources</h3>
      <el-empty v-if="!answer.references?.length" description="No sources found." />
      <div v-for="ref in answer.references" :key="ref.recordId + ref.chunkText" class="record-card">
        <router-link :to="`/records/${ref.recordId}`">{{ ref.recordTitle }}</router-link>
        <p>{{ ref.chunkText }}</p>
        <el-tag>Similarity {{ ref.similarity }}</el-tag>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../api/http'

const question = ref('')
const answer = ref(null)
const loading = ref(false)

async function ask() {
  if (!question.value.trim() || loading.value) return
  loading.value = true
  try {
    answer.value = await http.post('/qa/ask', { question: question.value })
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>
