<template>
  <div v-if="isGuest" class="auth-shell"><router-view /></div>
  <el-container v-else :class="['app-shell', { 'paper-route-shell': isPaperRoute }]">
    <header class="system-bar">
      <div class="system-brand">
        <strong>CoBloom</strong>
      </div>
      <div class="system-user">
        <span>{{ auth.user?.nickname || auth.user?.username }}</span>
        <el-button link @click="logout">Logout</el-button>
      </div>
    </header>
    <el-aside width="126px" class="sidebar">
      <div class="brand">CoBloom</div>
      <div class="brand-sub">个人知识库与智能问答</div>
      <el-menu router :default-active="activeMenu" class="dock-menu">
        <el-menu-item index="/records" :aria-current="activeMenu === '/records' ? 'page' : undefined">
          <span class="dock-label">Record</span>
        </el-menu-item>
        <el-menu-item index="/new-record" :aria-current="activeMenu === '/new-record' ? 'page' : undefined">
          <span class="dock-label">New</span>
        </el-menu-item>
        <el-menu-item index="/ai-chat" :aria-current="activeMenu === '/ai-chat' ? 'page' : undefined">
          <span class="dock-label">Chat</span>
        </el-menu-item>
        <el-menu-item index="/timeline" :aria-current="activeMenu === '/timeline' ? 'page' : undefined">
          <span class="dock-label">Timeline</span>
        </el-menu-item>
        <el-menu-item index="/knowledge-graph" :aria-current="activeMenu === '/knowledge-graph' ? 'page' : undefined">
          <span class="dock-label">Graph</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main>
      <div :class="['desktop-window main-window', { 'paper-board-window': isPaperRoute }]">
        <img
          v-if="currentBoardTitle"
          :key="routeKind"
          :class="['recordings-board-title', boardTitleClass]"
          :src="currentBoardTitle.src"
          :alt="currentBoardTitle.alt"
          draggable="false"
        />
        <div class="window-titlebar">
          <span class="window-close">×</span>
          <span class="window-rule"></span>
          <span class="window-title">Learning Vector Notes</span>
          <span class="window-rule"></span>
        </div>
        <div :class="['window-body', { 'paper-board-body': isPaperRoute }]">
          <router-view v-slot="{ Component }">
            <component :is="Component" :class="pageClass" />
          </router-view>
        </div>
      </div>
    </el-main>
  </el-container>
</template>

<script setup>
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import recordingsTitle from './assets/images/recordings-title.png'
import newTitle from './assets/images/New_Title.png'
import chatTitle from './assets/images/Chat_Title.png'
import timelineTitle from './assets/images/Timeline_Title.png'
import graphTitle from './assets/images/Graph_Title.png'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const isGuest = computed(() => Boolean(route.meta.guest) || route.path === '/login' || route.path === '/register')
const sectionBoardTitles = {
  records: { src: recordingsTitle, alt: 'Recordings' },
  recordDetail: { src: recordingsTitle, alt: 'Recordings' },
  recordEdit: { src: newTitle, alt: 'Edit Record' },
  new: { src: newTitle, alt: 'New' },
  chat: { src: chatTitle, alt: 'Chat' },
  timeline: { src: timelineTitle, alt: 'Timeline' },
  graph: { src: graphTitle, alt: 'Graph' }
}
function classifyPath(path) {
  if (path === '/' || path === '/dashboard') return 'records'
  if (path === '/records') return 'records'
  if (/^\/records\/[^/]+$/.test(path)) return 'recordDetail'
  if (/^\/records\/[^/]+\/edit$/.test(path)) return 'recordEdit'
  if (path === '/new-record' || path === '/records/new') return 'new'
  if (path.startsWith('/ai-chat') || path.startsWith('/qa')) return 'chat'
  if (path.startsWith('/timeline')) return 'timeline'
  if (path.startsWith('/knowledge-graph') || path.startsWith('/graph')) return 'graph'
  return 'unknown'
}
const routeKind = computed(() => {
  const currentPath = route.path && route.path !== '/' ? route.path : window.location.pathname
  const currentKind = classifyPath(currentPath)
  if (currentKind !== 'unknown') return currentKind
  return classifyPath(route.path)
})
const currentSection = computed(() => routeKind.value)
const currentBoardTitle = computed(() => {
  if (isGuest.value) return null
  return sectionBoardTitles[routeKind.value] || null
})
const isPaperRoute = computed(() => !isGuest.value)
const boardTitleClass = computed(() => ({
  records: 'is-recordings-title',
  recordDetail: 'is-recordings-title',
  recordEdit: 'is-section-title',
  new: 'is-section-title',
  chat: 'is-section-title',
  timeline: 'is-section-title',
  graph: 'is-section-title'
})[routeKind.value] || 'is-title-pending')
const pageClass = computed(() => {
  return ({
    records: 'recordings-page',
    recordDetail: 'recordings-page record-detail-page-shell',
    recordEdit: 'recordings-page record-editor-page',
    new: 'recordings-page record-editor-page',
    chat: 'recordings-page qa-page',
    timeline: 'recordings-page timeline-page',
    graph: 'recordings-page graph-page'
  })[routeKind.value] || ''
})
const activeMenu = computed(() => {
  return ({
    new: '/new-record',
    records: '/records',
    recordDetail: '/records',
    recordEdit: '/records',
    chat: '/ai-chat',
    timeline: '/timeline',
    graph: '/knowledge-graph'
  })[routeKind.value] || route.path
})

onMounted(() => window.addEventListener('cobloom:auth-invalid', handleAuthInvalid))
onUnmounted(() => window.removeEventListener('cobloom:auth-invalid', handleAuthInvalid))

function handleAuthInvalid() {
  auth.clearSession()
  if (route.path !== '/login') {
    router.replace({ path: '/login', query: { redirect: route.fullPath } })
  }
}

async function logout() {
  await auth.logout()
  router.push('/login')
}
</script>
