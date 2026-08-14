<script setup lang="ts">
/**
 * 设备状态标签:统一的状态配色 + 文案(与 3D 场景颜色一致)。
 */
import { computed } from 'vue';
import type { DeviceStatus } from '../api/types';
import { statusColor, statusText } from '../utils/format';

const props = defineProps<{ status: DeviceStatus }>();
const color = computed(() => statusColor[props.status]);
</script>

<template>
  <span class="status-tag" :style="{ color, borderColor: color, background: color + '1a' }">
    <span class="dot" :style="{ background: color }"></span>
    {{ statusText[status] }}
  </span>
</template>

<style scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px;
  font-size: 12px;
  border: 1px solid;
  border-radius: 999px;
  line-height: 18px;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
</style>
