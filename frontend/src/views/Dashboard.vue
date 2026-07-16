<template>
  <div>
    <div class="page-head">
      <h1 class="page-title">Dashboard</h1>
      <div class="toolbar">
        <el-button type="primary" @click="$router.push('/records/new')">新建记录</el-button>
        <el-button @click="$router.push('/qa')">AI 问答</el-button>
        <el-button @click="$router.push('/graph')">知识图谱</el-button>
        <el-button @click="$router.push('/timeline')">时间线</el-button>
      </div>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div v-if="loading" class="record-card">加载中...</div>
    <template v-else>
      <div class="grid stats">
        <div class="stat"><span>记录总数</span><strong>{{ data.recordCount || 0 }}</strong></div>
        <div class="stat"><span>标签数量</span><strong>{{ data.tagCount || 0 }}</strong></div>
        <div class="stat"><span>问答次数</span><strong>{{ data.qaCount || 0 }}</strong></div>
      </div>
      <el-row :gutter="16" style="margin-top:18px">
        <el-col :span="14">
          <h2>最近记录</h2>
          <el-empty v-if="!data.recentRecords.length" description="暂无最近记录，请先创建内容" />
          <div v-for="r in data.recentRecords" :key="r.id" class="record-card" @click="$router.push(`/records/${r.id}`)">
            <strong>{{ r.title }}</strong>
            <p class="muted">{{ firstThreeLines(r.content) }}</p>
            <el-tag v-for="tag in r.tags || []" :key="tag" class="tag" size="small">{{ tag }}</el-tag>
          </div>
        </el-col>
        <el-col :span="10">
          <h2>最近 AI 反馈</h2>
          <el-empty v-if="!data.recentFeedback.length" description="暂无 AI 反馈" />
          <div v-for="f in data.recentFeedback" :key="f.id" class="record-card">
            <el-tag type="success">{{ label(f.feedbackType) }}</el-tag>
            <p>{{ f.content }}</p>
          </div>
        </el-col>
      </el-row>
    </template>
  </div>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { http } from '../api/http'
const data = reactive({ recentRecords: [], recentFeedback: [] })
const loading = ref(false)
const error = ref('')
onMounted(load)
async function load() {
  loading.value = true
  error.value = ''
  try {
    const next = await http.get('/dashboard')
    Object.assign(data, {
      recordCount: next?.recordCount || 0,
      tagCount: next?.tagCount || 0,
      qaCount: next?.qaCount || 0,
      recentRecords: Array.isArray(next?.recentRecords) ? next.recentRecords : [],
      recentFeedback: Array.isArray(next?.recentFeedback) ? next.recentFeedback : []
    })
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
function label(type) {
  return ({ gentle: '温柔鼓励型', rational: '理性复盘型', creative: '创作欣赏型' })[type] || type
}
function firstThreeLines(content) {
  const lines = (content || '').split(/\r?\n/)
  const linePreview = lines.slice(0, 3).join('\n').trim()
  const preview = linePreview.length > 140 ? linePreview.slice(0, 140).trimEnd() : linePreview
  const isTruncated = lines.length > 3 || preview.length < linePreview.length
  return isTruncated && preview ? `${preview}...` : preview
}
</script>
