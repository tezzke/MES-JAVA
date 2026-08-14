<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { systemApi, type AuditEvent } from '../../api/management';
import { formatTime } from '../../utils/format';
const rows=ref<AuditEvent[]>([]);const total=ref(0);const page=ref(1);const size=ref(20);const loading=ref(false);
async function load(){loading.value=true;try{const result=await systemApi.audits(page.value,size.value);rows.value=result.items;total.value=result.total;}finally{loading.value=false;}}
onMounted(load);
</script>
<template>
  <div class="page"><div class="panel table-panel">
    <el-table v-loading="loading" :data="rows"><el-table-column label="时间" width="180"><template #default="{row}">{{formatTime(row.occurredAt)}}</template></el-table-column><el-table-column prop="actor" label="操作人"/><el-table-column prop="action" label="动作"/><el-table-column prop="targetType" label="对象类型"/><el-table-column prop="targetId" label="对象 ID"/><el-table-column prop="result" label="结果"/><el-table-column prop="clientIp" label="客户端 IP"/><el-table-column prop="correlationId" label="关联 ID" min-width="180"/></el-table>
    <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" layout="total, sizes, prev, pager, next" @change="load"/>
  </div></div>
</template>
