<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'
import { useUserStore } from './stores/user'

onLaunch(() => {
  // 初始化用户状态（从本地存储恢复token等）
  const userStore = useUserStore()
  userStore.init()
})

onShow(() => {
})

onHide(() => {
})
</script>

<style lang="scss">
/* 全局样式 */
page {
  background-color: #f5f5f5;
}

.container {
  padding: 20rpx;
}
</style>
