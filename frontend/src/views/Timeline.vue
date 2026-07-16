<template>
  <div>
    <div class="page-head"><h1 class="page-title">Timeline</h1></div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div v-if="loading" class="record-card">Loading...</div>
    <el-empty v-else-if="!rows.length" description="No timeline entries yet. Create a record first." />
    <el-timeline v-else>
      <el-timeline-item v-for="r in rows" :key="r.id" :timestamp="r.time" placement="top">
        <div class="record-card" @click="$router.push(`/records/${r.id}`)">
          <h2>{{ r.title }}</h2>
          <el-tag class="tag">{{ r.recordType }}</el-tag>
          <p class="timeline-record-preview">{{ firstThreeLines(r.content) }}</p>
          <el-tag v-for="tag in r.tags" :key="tag" class="tag" type="success">{{ tag }}</el-tag>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { http } from '../api/http'

const rows = ref([])
const loading = ref(false)
const error = ref('')

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await http.get('/timeline')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function firstThreeLines(content) {
  const lines = (content || '').split(/\r?\n/)
  const preview = lines.slice(0, 3).join('\n').trim()
  return lines.length > 3 && preview ? `${preview}...` : preview
}
</script>
