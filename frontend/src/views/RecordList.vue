<template>
  <div class="recordings-page">
    <div class="page-head">
      <div>
        <input ref="fileInput" type="file" accept=".md" style="display:none" @change="uploadMarkdown" />
        <el-button :loading="uploading" @click="chooseFile">Upload Markdown</el-button>
        <el-button type="primary" @click="$router.push('/records/new')">New Record</el-button>
      </div>
    </div>
    <div class="toolbar">
      <el-input v-model="q" placeholder="Search title or content" clearable style="width:320px" />
      <el-select v-model="type" placeholder="Record type" clearable style="width:160px">
        <el-option v-for="x in types" :key="x" :label="x" :value="x" />
      </el-select>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div v-if="loading" class="record-card">Loading...</div>
    <el-empty v-else-if="!filtered.length" description="No notes yet. Create your first record." />
    <div v-for="r in filtered" :key="r.id" class="record-card">
      <router-link :to="`/records/${r.id}`"><h2>{{ r.title }}</h2></router-link>
      <p class="muted">{{ firstThreeLines(r.content) }}</p>
      <el-tag class="tag">{{ r.recordType }}</el-tag>
      <el-tag v-for="tag in r.tags || []" :key="tag" class="tag" type="success">{{ tag }}</el-tag>
    </div>
  </div>
</template>
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { http } from '../api/http'
const route = useRoute()
const rows = ref([])
const q = ref(route.query.q || '')
const type = ref('')
const fileInput = ref(null)
const uploading = ref(false)
const loading = ref(true)
const error = ref('')
const types = ['学习', '项目', '创作', '情绪', '生活', '成就']
onMounted(load)
watch(() => route.query.q, value => q.value = value || '')
watch(q, load)
const filtered = computed(() => rows.value.filter(r => !type.value || r.recordType === type.value))
async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await http.get('/records', { params: { q: q.value || undefined } })
    rows.value = Array.isArray(data) ? data : []
  } catch (e) {
    rows.value = []
    error.value = e.message
  } finally {
    loading.value = false
  }
}
function chooseFile() {
  fileInput.value?.click()
}
async function uploadMarkdown(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  const form = new FormData()
  form.append('file', file)
  uploading.value = true
  try {
    await http.post('/records/upload', form)
    ElMessage.success('Markdown 上传成功')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    uploading.value = false
  }
}

function firstThreeLines(content) {
  const lines = (content || '').split(/\r?\n/)
  const linePreview = lines.slice(0, 3).join('\n').trim()
  const preview = linePreview.length > 140 ? linePreview.slice(0, 140).trimEnd() : linePreview
  const isTruncated = lines.length > 3 || preview.length < linePreview.length
  return isTruncated && preview ? `${preview}...` : preview
}
</script>
