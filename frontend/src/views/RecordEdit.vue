<template>
  <div>
    <div class="page-head">
      <h1 class="page-title">{{ id ? 'Edit Record' : 'New Record' }}</h1>
      <el-button @click="$router.back()">Back</el-button>
    </div>
    <el-form :model="form" label-position="top">
      <el-row :gutter="14">
        <el-col :span="10">
          <el-form-item label="Title">
            <el-input v-model="form.title" />
          </el-form-item>
        </el-col>
        <el-col :span="5">
          <el-form-item label="Type">
            <el-select v-model="form.recordType">
              <el-option v-for="x in types" :key="x" :label="x" :value="x" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="Tags">
        <el-select
          v-model="form.tags"
          multiple
          filterable
          allow-create
          default-first-option
          style="width:100%"
          placeholder="Type and press Enter to create tags"
        />
      </el-form-item>
      <div class="grid editor-grid">
        <el-form-item label="Markdown">
          <el-input v-model="form.content" type="textarea" :rows="18" placeholder="# Today's note" />
        </el-form-item>
        <el-form-item label="Preview">
          <div class="markdown-preview" v-html="preview"></div>
        </el-form-item>
      </div>
      <el-button class="handwritten-save" type="primary" :loading="saving" @click="save">Save Record</el-button>
    </el-form>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { http } from '../api/http'
import { renderSafeMarkdown } from '../utils/markdown'

const route = useRoute()
const router = useRouter()
const id = route.params.id
const types = ['Learning', 'Project', 'Creative', 'Emotion', 'Life', 'Achievement']
const form = reactive({ title: '', content: '', recordType: 'Learning', tags: [] })
const saving = ref(false)
const preview = computed(() => renderSafeMarkdown(form.content))

onMounted(async () => {
  if (!id) return
  const record = await http.get(`/records/${id}`)
  Object.assign(form, {
    title: record?.title || '',
    content: record?.content || '',
    recordType: record?.recordType || 'Learning',
    tags: Array.isArray(record?.tags) ? record.tags : []
  })
})

async function save() {
  if (saving.value) return
  saving.value = true
  try {
    const payload = {
      title: form.title,
      content: form.content,
      recordType: form.recordType,
      tags: form.tags
    }
    const saved = id ? await http.put(`/records/${id}`, payload) : await http.post('/records', payload)
    ElMessage.success('Record saved')
    router.push(`/records/${saved.id}`)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}
</script>
