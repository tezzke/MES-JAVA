<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const loading = ref(false);
const form = reactive({ username: '', password: '' });

async function submit() {
  loading.value = true;
  try {
    await auth.login(form.username, form.password);
    await router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : '/');
  } catch {
    ElMessage.error('用户名或密码错误');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <template #header><div class="title">MES 生产管理系统</div></template>
      <el-form :model="form" label-position="top" @keyup.enter="submit">
        <el-form-item label="用户名"><el-input v-model="form.username" autofocus /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-button type="primary" :loading="loading" style="width:100%" @click="submit">登录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-page{height:100%;display:grid;place-items:center;background:radial-gradient(circle at 50% 20%,#17233b,var(--bg-page) 55%)}.login-card{width:380px}.title{text-align:center;font-size:20px;font-weight:700;color:var(--accent)}
</style>
