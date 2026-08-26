<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { agreementContent } from '../../config/agreement-content'

// 协议文档页：一页两用（用户协议 / 隐私政策），URL query type 区分
// 登录页《用户协议》《隐私政策》跳转至此
// 正文与运营者信息在 src/config/agreement-content.ts（含个人信息，不入库；
// 协作者 clone 后 postinstall 自动生成占位版 agreement-content.ts）

type AgreementType = 'terms' | 'privacy'
const type = ref<AgreementType>('privacy')

const sections = computed(() =>
  type.value === 'terms' ? agreementContent.termsSections : agreementContent.privacySections
)

onLoad((options) => {
  if (options?.type === 'terms' || options?.type === 'privacy') {
    type.value = options.type
  }
  uni.setNavigationBarTitle({ title: type.value === 'terms' ? '用户协议' : '隐私政策' })
})
</script>

<template>
  <view class="agreement-container">
    <view class="doc-header">
      <text class="doc-title">{{ type === 'terms' ? '用户协议' : '隐私政策' }}</text>
      <text class="doc-updated">更新日期：{{ agreementContent.lastUpdated }}</text>
    </view>

    <view v-for="(section, i) in sections" :key="i" class="section">
      <text class="section-title">{{ i + 1 }}. {{ section.title }}</text>
      <view v-for="(p, j) in section.paragraphs" :key="j" class="section-paragraph">
        <text class="section-text">{{ p }}</text>
      </view>
    </view>

    <view class="doc-footer">
      <text class="footer-text">运营者：{{ agreementContent.operatorName }}</text>
      <text class="footer-text">联系邮箱：{{ agreementContent.contactEmail }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.agreement-container {
  min-height: 100vh;
  background: $bg;
  padding: 32rpx 40rpx calc(env(safe-area-inset-bottom, 0px) + 60rpx);
}

.doc-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 40rpx;
}

.doc-title {
  font-size: 40rpx;
  font-weight: 800;
  color: $text-primary;
  margin-bottom: 12rpx;
}

.doc-updated {
  font-size: 24rpx;
  color: $text-muted;
}

.section {
  background: $card-bg;
  border-radius: 20rpx;
  padding: 28rpx 32rpx;
  margin-bottom: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 700;
  color: $text-primary;
}

.section-paragraph {
  display: flex;
}

.section-text {
  font-size: 26rpx;
  color: $text-secondary;
  line-height: 1.7;
}

.doc-footer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  margin-top: 20rpx;
}

.footer-text {
  font-size: 24rpx;
  color: $text-muted;
}
</style>
