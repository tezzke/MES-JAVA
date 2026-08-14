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
      axisLine: { lineStyle: { color: '#33415e' } },
      axisLabel: { color: '#7c8db5' },
    },
    yAxis: {
      type: 'value',
      scale: true,
      name: props.unit,
      splitLine: { lineStyle: { color: '#1e2a44' } },
      axisLabel: { color: '#7c8db5' },
    },
    dataZoom: [
      { type: 'inside' },
      { type: 'slider', height: 18, bottom: 8, borderColor: '#1e2a44' },
    ],
    series: [
      {
        name: props.name,
        type: 'line',
        showSymbol: false,
        smooth: true,
        lineStyle: { color: '#38bdf8', width: 1.6 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(56,189,248,0.25)' },
            { offset: 1, color: 'rgba(56,189,248,0)' },
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
