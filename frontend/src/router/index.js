import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import RecordList from '../views/RecordList.vue'
import RecordEdit from '../views/RecordEdit.vue'
import RecordDetail from '../views/RecordDetail.vue'
import QaAsk from '../views/QaAsk.vue'
import QaHistory from '../views/QaHistory.vue'
import QaDetail from '../views/QaDetail.vue'
import Timeline from '../views/Timeline.vue'
import Graph from '../views/Graph.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: Login, meta: { guest: true, guestOnly: true } },
    { path: '/register', component: Register, meta: { guest: true, guestOnly: true } },
    { path: '/', redirect: '/records' },
    { path: '/dashboard', redirect: '/records' },
    { path: '/records', component: RecordList, meta: { requiresAuth: true } },
    { path: '/new-record', component: RecordEdit, meta: { requiresAuth: true } },
    { path: '/records/new', redirect: '/new-record' },
    { path: '/records/:id/edit', component: RecordEdit, meta: { requiresAuth: true } },
    { path: '/records/:id', component: RecordDetail, meta: { requiresAuth: true } },
    { path: '/ai-chat', component: QaAsk, meta: { requiresAuth: true } },
    { path: '/qa', redirect: '/ai-chat' },
    { path: '/qa/history', component: QaHistory, meta: { requiresAuth: true } },
    { path: '/qa/history/:id', component: QaDetail, meta: { requiresAuth: true } },
    { path: '/timeline', component: Timeline, meta: { requiresAuth: true } },
    { path: '/knowledge-graph', component: Graph, meta: { requiresAuth: true } },
    { path: '/graph', redirect: to => ({ path: '/knowledge-graph', query: to.query }) }
  ]
})

router.beforeEach(async to => {
  const auth = useAuthStore()
  if (auth.token && !auth.user) {
    try {
      await auth.fetchMe()
    } catch (error) {
      auth.clearSession(error)
    }
  }
  if (to.meta.requiresAuth && !auth.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && auth.token) {
    return '/'
  }
})

export default router
