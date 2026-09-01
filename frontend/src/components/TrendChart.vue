<script setup lang="ts">
/**
 * 趋势曲线组件:ECharts 折线图的薄封装。
 * 传入 [时间戳, 数值] 序列即可渲染,自动跟随容器尺寸变化。
 */
import * as echarts from 'echarts';
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';

const props = defineProps<{
  /** 曲线数据:[ISO 时间, 值] */
  data: Array<[string, number]>;
  /** 曲线名称(图例/悬浮提示) */
  name: string;
  /** 工程单位 */
  unit?: string;
}>();

const container = ref<HTMLDivElement>();
let chart: echarts.ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;

function render() {
  if (!chart) return;
  chart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 56, right: 20, top: 30, bottom: 40 },
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v: unknown) => `${Number(v).toFixed(2)} ${props.unit ?? ''}`,
    },
    xAxis: {
      type: 'time',
      axisLine: { lineStyle: { color: '#c9cdd3' } },
      axisLabel: { color: '#6b7280' },
    },
    yAxis: {
      type: 'value',
      scale: true,
      name: props.unit,
      splitLine: { lineStyle: { color: '#eef0f3' } },
      axisLabel: { color: '#6b7280' },
    },
    dataZoom: [
      { type: 'inside' },
      { type: 'slider', height: 18, bottom: 8, borderColor: '#e6e8eb' },
    ],
    series: [
      {
        name: props.name,
        type: 'line',
        showSymbol: false,
        smooth: true,
        lineStyle: { color: '#2563eb', width: 1.8 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(37,99,235,0.16)' },
            { offset: 1, color: 'rgba(37,99,235,0)' },
          ]),
        },
        data: props.data,
      },
    ],
  });
}

onMounted(() => {
  chart = echarts.init(container.value!);
  render();
  // 容器尺寸变化时自适应(抽屉/窗口缩放)
  resizeObserver = new ResizeObserver(() => chart?.resize());
  resizeObserver.observe(container.value!);
});

watch(() => props.data, render, { deep: false });

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  chart?.dispose();
});
</script>

<template>
  <div ref="container" class="chart"></div>
</template>

<style scoped>
.chart {
  width: 100%;
  height: 100%;
  min-height: 260px;
}
</style>
