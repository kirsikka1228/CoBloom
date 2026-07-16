<template>
  <div v-if="record" class="record-detail-page">
    <div class="record-detail-page-head">
      <div class="record-detail-page-title">
        <span>Record Detail</span>
      </div>
      <div class="record-detail-actions">
        <el-button class="handwritten-action" @click="$router.back()">Back</el-button>
        <el-button class="handwritten-action" @click="$router.push(`/records/${record.id}/edit`)">Edit</el-button>
        <el-button class="handwritten-action" :loading="graphLoading" @click="openGraph">Open Graph</el-button>
        <el-button type="danger" plain @click="remove">Delete</el-button>
      </div>
    </div>

    <el-alert v-if="graphError" :title="graphError" type="error" show-icon :closable="false" />

    <div class="record-detail-layout">
      <article class="record-sheet">
        <header class="record-detail-title">
          <p class="record-kicker">{{ record.recordType || 'Record' }}</p>
          <h1>{{ record.title }}</h1>
          <p class="record-meta">
            {{ record.createdAt }}
          </p>
          <div class="record-detail-tags" aria-label="Record tags and keywords">
            <el-tag v-for="tag in record.tags" :key="`tag-${tag}`" class="tag">{{ tag }}</el-tag>
            <el-tag v-for="kw in record.keywords" :key="`kw-${kw}`" class="tag" type="success">{{ kw }}</el-tag>
          </div>
        </header>

        <section class="record-reading" aria-label="Record content">
          <div class="record-detail-content" v-html="html"></div>
        </section>
      </article>

      <aside class="record-detail-side" aria-label="Record helpers">
        <section class="record-detail-ai-card" aria-labelledby="ai-notes-title">
          <div class="record-detail-panel-head">
            <h2 id="ai-notes-title">AI Notes</h2>
            <div class="ai-toolbar">
              <el-button
                class="handwritten-action"
                :loading="summaryLoading"
                :disabled="keywordsLoading || summaryLoading"
                @click="summary"
              >
                Make Summary
              </el-button>
              <el-button
                class="handwritten-action"
                :loading="keywordsLoading"
                :disabled="summaryLoading || keywordsLoading"
                @click="keywords"
              >
                Pick Keywords
              </el-button>
            </div>
          </div>

          <div
            v-if="record.summary"
            class="record-detail-summary"
            :class="{ 'is-collapsed': !summaryExpanded }"
          >
            <template v-if="summaryExpanded">
              <section v-for="section in summarySections" :key="section.key" class="record-detail-summary-section">
                <h3>{{ displaySummaryTitle(section) }}</h3>
                <p>{{ section.content }}</p>
              </section>
            </template>
            <section v-else class="record-detail-summary-section record-detail-summary-section-preview">
              <h3>{{ displaySummaryTitle(collapsedSummarySection) }}</h3>
              <p class="record-detail-summary-preview">{{ collapsedSummarySection.content }}</p>
            </section>
          </div>
          <button
            v-if="record.summary"
            class="record-detail-expand"
            :class="{ 'is-expanded': summaryExpanded }"
            type="button"
            @click="summaryExpanded = !summaryExpanded"
          >
            点击展开
          </button>
          <el-empty v-if="!record.summary && !record.feedback?.length" description="No AI notes yet." />
          <div v-for="f in record.feedback" :key="f.id" class="record-detail-feedback-card">
            <el-tag>{{ feedbackLabel(f.feedbackType) }}</el-tag>
            <p>{{ f.content }}</p>
          </div>
        </section>

        <section class="record-detail-related-panel" aria-labelledby="related-title">
          <h2 id="related-title">Related Records</h2>
          <el-empty v-if="!recs.length" description="No related records yet." />
          <button
            v-for="r in recs"
            :key="r.id"
            class="record-detail-related-card"
            type="button"
            @click="$router.push(`/records/${r.id}`)"
          >
            <strong>{{ r.title }}</strong>
            <span class="record-detail-score">Score {{ r.score }}</span>
            <p>{{ firstThreeLines(r.content) }}</p>
          </button>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../api/http'
import { renderSafeMarkdown } from '../utils/markdown'

const route = useRoute()
const router = useRouter()
const record = ref(null)
const recs = ref([])
const graphLoading = ref(false)
const graphError = ref('')
const summaryLoading = ref(false)
const keywordsLoading = ref(false)
const summaryExpanded = ref(false)

const html = computed(() => renderSafeMarkdown(record.value?.content))
const summarySections = computed(() => splitSummary(record.value?.summary || ''))
const collapsedSummarySection = computed(() => summarySections.value[0] || {
  key: 'core',
  title: '核心主题',
  content: record.value?.summary || ''
})

onMounted(load)

async function load() {
  summaryExpanded.value = false
  record.value = await http.get(`/records/${route.params.id}`)
  const recommendations = await http.get(`/records/${route.params.id}/recommendations`)
  recs.value = Array.isArray(recommendations)
    ? recommendations.filter(r => Number(r.score) > 3)
    : []
}

async function summary() {
  if (summaryLoading.value) return
  summaryLoading.value = true
  try {
    record.value = await http.post(`/records/${record.value.id}/ai/summary`)
    summaryExpanded.value = false
    ElMessage.success('AI summary generated')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    summaryLoading.value = false
  }
}

async function keywords() {
  if (keywordsLoading.value) return
  keywordsLoading.value = true
  try {
    record.value = await http.post(`/records/${record.value.id}/ai/keywords`)
    ElMessage.success('Keywords extracted')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    keywordsLoading.value = false
  }
}

async function openGraph() {
  graphLoading.value = true
  graphError.value = ''
  try {
    await router.push({ path: '/graph', query: { noteId: record.value.id } })
  } catch (e) {
    graphError.value = e.message
    ElMessage.error(e.message)
  } finally {
    graphLoading.value = false
  }
}

async function remove() {
  await ElMessageBox.confirm('Delete this record? Related chunks, feedback, and references will also be removed.')
  await http.delete(`/records/${record.value.id}`)
  ElMessage.success('Record deleted')
  router.push('/records')
}

function feedbackLabel(type) {
  return ({ gentle: 'Gentle', rational: 'Rational', creative: 'Creative' })[type] || type
}

function firstThreeLines(content) {
  return (content || '').split(/\r?\n/).slice(0, 3).join('\n')
}

function displaySummaryTitle(section) {
  return section?.key === 'core' ? '核心主题' : section?.title
}

function splitSummary(summary) {
  const labels = [
    { key: 'core', title: '核心部分', aliases: ['核心部分', '核心主题', '核心内容', '核心'] },
    { key: 'conclusion', title: '主要结论', aliases: ['主要结论', '结论'] },
    { key: 'keywords', title: '技术关键词', aliases: ['技术关键词', '关键词'] }
  ]
  const sections = Object.fromEntries(labels.map(item => [item.key, []]))
  let currentKey = 'core'

  for (const rawLine of (summary || '').split(/\r?\n/)) {
    const line = rawLine.trim()
    if (!line) continue

    const heading = summaryHeading(line, labels)
    if (heading) {
      currentKey = heading.key
      if (heading.rest) sections[currentKey].push(heading.rest)
      continue
    }

    sections[currentKey].push(line)
  }

  return labels.map(item => ({
    key: item.key,
    title: item.title,
    content: sections[item.key].join('\n').trim() || '暂无内容'
  }))
}

function summaryHeading(line, labels) {
  const clean = line
    .replace(/^#{1,6}\s*/, '')
    .replace(/^\*\*(.*)\*\*$/, '$1')
    .trim()

  for (const item of labels) {
    const alias = item.aliases.find(name => clean === name || clean.startsWith(`${name}:`) || clean.startsWith(`${name}：`))
    if (!alias) continue
    return {
      key: item.key,
      rest: clean.slice(alias.length).replace(/^[:：]\s*/, '').trim()
    }
  }

  return null
}
</script>
