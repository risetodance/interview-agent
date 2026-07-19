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

// scroll-view 横向 padding 在小程序端会导致内容宽度计算异常，内容（尤其 grid 右列）溢出视口右边缘被裁，
// 表现为"手机端右侧遮挡"。统一给 scroll-view 及其子元素 border-box，让 padding 计入宽度，避免溢出。
scroll-view {
  box-sizing: border-box;
}

.container {
  padding: 20rpx;
}
</style>
