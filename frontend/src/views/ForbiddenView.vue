<script setup lang="ts">
import { useRouter } from 'vue-router';
const router = useRouter();
</script>

<template>
  <div class="result-page">
    <el-result icon="warning" title="403" sub-title="当前账号没有访问该功能的权限">
      <template #extra><el-button type="primary" @click="router.push('/')">返回首页</el-button></template>
    </el-result>
  </div>
</template>

<style scoped>.result-page{height:100%;display:grid;place-items:center}</style>
