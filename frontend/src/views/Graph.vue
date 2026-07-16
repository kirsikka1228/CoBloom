<template>
  <div>
    <div class="page-head">
      <h1 class="page-title">{{ title }}</h1>
      <p class="muted">点击笔记节点查看原文；概念和实体节点展示笔记中的知识关系。</p>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div v-if="loading" class="record-card">Loading...</div>
    <el-empty v-else-if="empty" description="No graph data yet. Create records first." />
    <div ref="el" class="graph-box"></div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { http } from '../api/http'
import blue1 from '../assets/graph_notes/blue_1.png'
import blue2 from '../assets/graph_notes/blue_2.png'
import blue3 from '../assets/graph_notes/blue_3.png'
import green1 from '../assets/graph_notes/green_1.png'
import green2 from '../assets/graph_notes/green_2.png'
import green3 from '../assets/graph_notes/green_3.png'
import yellow1 from '../assets/graph_notes/yellow_1.png'
import yellow2 from '../assets/graph_notes/yellow_2.png'
import yellow3 from '../assets/graph_notes/yellow_3.png'

const el = ref(null)
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const error = ref('')
const empty = ref(false)
let chart
const categories = ['note', 'concept', 'entity']
const categoryLabels = {
  note: 'Note',
  concept: 'Concept',
  entity: 'Entity'
}
const title = computed(() => route.query.noteId ? 'Local Graph' : 'Knowledge Graph')

onMounted(async () => {
  if (!el.value) return
  chart = echarts.init(el.value)
  chart.on('click', params => {
    const node = params.data
    if (node?.type === 'note' && node.recordId) router.push(`/records/${node.recordId}`)
  })
  addEventListener('resize', resize)
  await load()
})
watch(() => route.query.noteId, load)
onUnmounted(() => { removeEventListener('resize', resize); chart?.dispose() })

async function load() {
  if (!chart) return
  loading.value = true
  error.value = ''
  empty.value = false
  try {
    const url = route.query.noteId ? `/graph/note/${route.query.noteId}` : '/graph'
    const data = await http.get(url)
    const nodes = Array.isArray(data?.nodes) ? data.nodes.filter(n => categories.includes(n.type)) : []
    const nodeIds = new Set(nodes.map(n => n.id))
    const edges = Array.isArray(data?.edges)
      ? data.edges.filter(e => nodeIds.has(e.source) && nodeIds.has(e.target))
      : []
    empty.value = !nodes.length
    if (empty.value) {
      chart.clear()
      return
    }
    const colors = getGraphColors()
    chart.setOption({
      tooltip: {
        backgroundColor: colors.surface,
        borderColor: colors.border,
        textStyle: { color: colors.fg, fontFamily: colors.font },
        formatter: params => {
          const node = params.data || {}
          const type = categoryLabels[node.type] || node.type || '节点'
          const relationCount = Number(node.relationCount || 0)
          const sourceCount = Number(node.sourceCount || 0)
          return `<strong>${escapeHtml(node.name || node.label || '')}</strong><br>`
            + `${type} · ${relationCount} 条关系 · ${sourceCount} 个来源`
        }
      },
      legend: [{
        data: categories,
        formatter: name => categoryLabels[name] || name,
        textStyle: { color: colors.fg2, fontFamily: colors.font }
      }],
      series: [{
        type: 'graph',
        layout: 'force',
        roam: true,
        label: {
          show: true,
          color: colors.label,
          fontFamily: colors.font,
          fontWeight: 700,
          fontSize: 13
        },
        lineStyle: { color: colors.edge, opacity: 0.38, width: 1 },
        force: { repulsion: 220, edgeLength: 108, gravity: 0.06, friction: 0.18 },
        categories: categories.map(name => ({ name })),
        data: nodes.map(n => ({
          ...n,
          ...nodeVisual(n, colors),
          cursor: n.type === 'note' && n.recordId ? 'pointer' : 'default',
        })),
        links: edges.map(e => edgeVisual(e, colors))
      }]
    }, true)
  } catch (e) {
    error.value = e.message
    chart.clear()
  } finally {
    loading.value = false
  }
}

function resize() { chart?.resize() }

function cssVar(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

function getGraphColors() {
  const fg = cssVar('--fg') || '#1f1b18'
  const fg2 = cssVar('--fg-2') || fg
  const border = 'rgb(128 118 103 / 42%)'
  const surface = cssVar('--surface') || '#fffdf7'
  return {
    fg,
    fg2,
    border,
    surface,
    label: '#3f382f',
    edge: '#77736d',
    font: '"Segoe Print", "Bradley Hand ITC", "Comic Sans MS", "Microsoft YaHei", cursive',
    noteSymbols: [blue1, blue2, blue3],
    conceptSymbols: [green1, green2, green3],
    entitySymbols: [yellow1, yellow2, yellow3]
  }
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value))
}

function nodeVisual(node, colors) {
  const relationCount = Number.isFinite(Number(node?.relationCount)) ? Number(node.relationCount) : 0
  const tiers = {
    note: { base: 44, step: 2.8, min: 44, max: 56, symbols: colors.noteSymbols, fontSize: 14 },
    concept: { base: 32, step: 2.4, min: 32, max: 44, symbols: colors.conceptSymbols, fontSize: 13 },
    entity: { base: 28, step: 2.2, min: 28, max: 40, symbols: colors.entitySymbols, fontSize: 12 }
  }
  const config = tiers[node?.type] || tiers.concept
  const symbolSize = clamp(config.base + Math.min(relationCount, 4) * config.step, config.min, config.max)
  const name = String(node?.name || node?.label || '')
  const useInsideLabel = symbolSize >= 49 && name.length <= 10
  return {
    symbol: `image://${selectNodeSymbol(node, config.symbols)}`,
    symbolSize,
    label: {
      show: true,
      color: colors.label,
      fontFamily: colors.font,
      fontWeight: 700,
      fontSize: config.fontSize,
      position: useInsideLabel ? 'inside' : 'right',
      distance: useInsideLabel ? 0 : 8,
      align: useInsideLabel ? 'center' : 'left',
      verticalAlign: 'middle'
    }
  }
}

function edgeVisual(edge, colors) {
  const weight = Number.isFinite(Number(edge?.weight)) ? Number(edge.weight) : 1
  const confidence = Number.isFinite(Number(edge?.confidence)) ? Number(edge.confidence) : 0.5
  return {
    source: edge.source,
    target: edge.target,
    label: { show: false },
    lineStyle: {
      color: colors.edge,
      width: clamp(0.95 + (weight - 1) * 0.08, 0.9, 1.2),
      opacity: clamp(0.3 + confidence * 0.1, 0.3, 0.4),
      curveness: 0.04
    }
  }
}

function selectNodeSymbol(node, symbols) {
  const variants = Array.isArray(symbols) && symbols.length ? symbols : [blue1]
  const stableId = String(node?.id || node?.knowledgeNodeId || node?.name || '')
  const index = stableVariantIndex(stableId, variants.length)
  return variants[index]
}

function stableVariantIndex(value, length) {
  let hash = 0
  for (const character of value) {
    hash = ((hash * 31) + character.codePointAt(0)) >>> 0
  }
  return hash % length
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
}
</script>
