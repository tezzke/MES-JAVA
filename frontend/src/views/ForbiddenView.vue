<script setup lang="ts">
import { useRouter } from 'vue-router';
const router = useRouter();
</script>

<template>
  <div class="result-page">
    <el-result icon="warning" title="无权访问" sub-title="当前工号没有该功能的岗位权限。请联系班组长或管理员在「人员账号」中分配岗位（例如生产操作员）。">
      <template #extra><el-button type="primary" @click="router.push('/')">返回产线总览</el-button></template>
    </el-result>
  </div>
</template>

<style scoped>.result-page{height:100%;display:grid;place-items:center}</style>
