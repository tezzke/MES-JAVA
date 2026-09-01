<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { AxiosError } from 'axios';
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
  } catch (error) {
    const status = error instanceof AxiosError ? error.response?.status : undefined;
    if (status === 403) {
      return;
    }
    ElMessage.error('工号或密码不正确');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-frame">
      <div class="hero">
        <div class="kicker">MANUFACTURING EXECUTION</div>
        <h1>制造执行系统</h1>
        <p>把车间现场、工单执行与质量追溯收在同一块中控台上。</p>
        <ul>
          <li>设备状态与工艺参数实时可视</li>
          <li>工单下达、扫码开工、报工闭环</li>
          <li>条码与批次全程可追溯</li>
        </ul>
      </div>
      <el-card class="login-card" shadow="never">
        <div class="card-title">工位登录</div>
        <div class="card-sub">使用已授权的操作员账号进入系统</div>
        <el-form :model="form" label-position="top" @keyup.enter="submit">
          <el-form-item label="工号">
            <el-input v-model="form.username" autofocus placeholder="请输入工号" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
          </el-form-item>
          <el-button type="primary" :loading="loading" class="submit" @click="submit">进入系统</el-button>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100%;
  display: grid;
  place-items: center;
  background:
    radial-gradient(900px 420px at 12% 0%, rgba(37, 99, 235, 0.06), transparent 55%),
    var(--bg-page);
}
.login-frame {
  width: min(920px, calc(100vw - 48px));
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
  background: #ffffff;
  box-shadow: var(--shadow-panel);
}
.hero {
  padding: 42px 40px;
  background: #f7f8fa;
  border-right: 1px solid var(--border);
}
.kicker {
  color: var(--accent);
  letter-spacing: 0.14em;
  font-size: 12px;
}
.hero h1 {
  margin: 10px 0 12px;
  font-size: 30px;
  color: var(--text-main);
}
.hero p,
.hero li {
  color: var(--text-sub);
  line-height: 1.7;
}
.hero ul {
  margin: 22px 0 0;
  padding-left: 18px;
}
.login-card {
  border: 0;
  border-radius: 0;
  padding: 18px 10px 8px;
  box-shadow: none;
}
.card-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-main);
}
.card-sub {
  margin: 6px 0 22px;
  color: var(--text-sub);
  font-size: 12px;
}
.submit {
  width: 100%;
  height: 40px;
  margin-top: 6px;
}
@media (max-width: 800px) {
  .login-frame {
    grid-template-columns: 1fr;
  }
  .hero {
    display: none;
  }
}
</style>
