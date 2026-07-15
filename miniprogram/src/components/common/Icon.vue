<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  name: string
  size?: number | string
  color?: string
}

const props = withDefaults(defineProps<Props>(), {
  size: 20,
  color: 'currentColor'
})

// Lucide 图标 SVG 映射（只导入需要的图标，减小体积）
const iconPaths: Record<string, string> = {
  search: 'M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196 7.5 7.5 0 0015.607 15.607z',
  plus: 'M12 4.5v15m7.5-7.5h-15',
  x: 'M6 18L18 6M6 6l12 12',
  'file-text': 'M14.5 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V7.5L14.5 2zM14 2v6h6M16 13H8M16 17H8M10 9H8',
  database: 'M12 2C6.48 2 2 4.02 2 6.5v11C2 19.98 6.48 22 12 22s10-2.02 10-4.5v-11C22 4.02 17.52 2 12 2z',
  eye: 'M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8zm11 3a3 3 0 100-6 3 3 0 000 6z',
  harddrive: 'M22 12H2M22 12a10 10 0 11-20 0 10 10 0 0120 0zM6 12h.01M10 12h.01M14 12h.01M18 12h.01',
  'arrow-left': 'M19 12H5M19 12l-7-7m7 7l-7 7',
  send: 'M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z',
  bot: 'M12 8V4H8M16 8V4H8M12 16v4M8 12h8M12 2v2M12 20v2M2 12h2M20 12h2',
  user: 'M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2M12 11a4 4 0 100-8 4 4 0 000 8z',
  pin: 'M12 17v5M9 10.76a2 2 0 01-1.11 1.79l-1.78.9A2 2 0 005 15.24V17h14v-1.76a2 2 0 00-1.11-1.79l-1.78-.9A2 2 0 0115 10.76V6a2 2 0 00-2-2h-2a2 2 0 00-2 2v4.76z',
  trash: 'M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2M10 11v6M14 11v6',
  edit: 'M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z',
  download: 'M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M7 10l5 5 5-5M12 15V3',
  refresh: 'M1 4v6h6M23 20v-6h-6M20.49 9A9 9 0 005.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 013.51 15',
  'chevron-down': 'M6 9l6 6 6-6',
  'chevron-up': 'M18 15l-6-6-6 6',
  'chevron-left': 'M15 18l-6-6 6-6',
  'chevron-right': 'M9 18l6-6-6-6',
  folder: 'M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z',
  check: 'M20 6L9 17l-5-5',
  clock: 'M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10zM12 6v6l4 2',
  alert: 'M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10zM12 8v4M12 16h.01',
  loader: 'M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83',
  message: 'M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z',
  upload: 'M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M17 8l-5-5-5 5M12 3v12',
  brain: 'M9.5 2a2.5 2.5 0 015 2v1h.5a1.5 1.5 0 011.5 1.5v2.5a1.5 1.5 0 001.5 1.5H18a2.5 2.5 0 010 5H9.5a2.5 2.5 0 010-5h.5v-5a2.5 2.5 0 00-2.5-2.5H4.5A1.5 1.5 0 003 5.5V8a1.5 1.5 0 001.5 1.5H5v5a2.5 2.5 0 002.5 2.5h5a2.5 2.5 0 000-5h-.5V6.5A1.5 1.5 0 0112 5V4a2.5 2.5 0 00-2.5-2z',
}

const iconSvg = computed(() => {
  const path = iconPaths[props.name]
  if (!path) return ''
  const size = typeof props.size === 'number' ? `${props.size}px` : props.size
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="${props.color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="${path}"/></svg>`
})
</script>

<template>
  <view class="icon" v-html="iconSvg" />
</template>

<style scoped>
.icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
</style>
