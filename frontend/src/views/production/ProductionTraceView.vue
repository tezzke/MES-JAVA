<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { productionApi, type TraceEvent } from '../../api/production';
import { formatTime } from '../../utils/format';
import PageHeader from '../../components/PageHeader.vue';
import { PAGE_COPY } from '../../navigation';
const query=reactive({batchNo:'',barcode:''});const rows=ref<TraceEvent[]>([]);const total=ref(0);const page=ref(1);const size=ref(20);const loading=ref(false);
async function load(){loading.value=true;try{const result=await productionApi.trace({batchNo:query.batchNo||undefined,barcode:query.barcode||undefined,page:page.value,size:size.value});rows.value=result.items;total.value=result.total;}finally{loading.value=false;}}
function search(){page.value=1;void load();}onMounted(load);
</script>
<template><div class="page"><PageHeader :title="PAGE_COPY['/production/trace'].title" :subtitle="PAGE_COPY['/production/trace'].subtitle" /><div class="panel action-panel"><el-input v-model="query.batchNo" placeholder="批次号" clearable style="width:220px"/><el-input v-model="query.barcode" placeholder="条码" clearable style="width:260px"/><el-button type="primary" @click="search">查询</el-button></div><div class="panel table-panel"><el-table v-loading="loading" :data="rows"><el-table-column prop="workOrderId" label="工单 ID"/><el-table-column prop="taskId" label="任务 ID"/><el-table-column prop="batchNo" label="批次"/><el-table-column prop="barcode" label="条码"/><el-table-column prop="eventType" label="事件"/><el-table-column prop="goodQuantity" label="合格"/><el-table-column prop="badQuantity" label="不良"/><el-table-column label="发生时间" width="180"><template #default="{row}">{{formatTime(row.occurredAt)}}</template></el-table-column></el-table><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" layout="total, sizes, prev, pager, next" @change="load"/></div></div></template>
