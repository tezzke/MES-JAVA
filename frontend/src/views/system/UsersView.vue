<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { systemApi, type Role, type UserProfile } from '../../api/management';
import { useAuthStore } from '../../stores/auth';
import PageHeader from '../../components/PageHeader.vue';
import { PAGE_COPY } from '../../navigation';

const auth = useAuthStore();
const rows = ref<UserProfile[]>([]);
const roles = ref<Role[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const editingId = ref<number | null>(null);
const dialog = ref(false);
const roleDialog = ref(false);
const roleUser = ref<UserProfile | null>(null);
const selectedRoleIds = ref<number[]>([]);
const form = reactive({ username: '', displayName: '', password: '', enabled: true });

async function load() {
  loading.value = true;
  try {
    const [result, roleList] = await Promise.all([systemApi.users(page.value, size.value), systemApi.roles()]);
    rows.value = result.items; total.value = result.total; roles.value = roleList;
  } finally { loading.value = false; }
}
function open(row?: UserProfile) {
  editingId.value = row?.id ?? null;
  Object.assign(form, { username: row?.username ?? '', displayName: row?.displayName ?? '', password: '', enabled: row?.enabled ?? true });
  dialog.value = true;
}
async function save() {
  const creating = editingId.value == null;
  const username = form.username;
  await systemApi.saveUser(editingId.value, form);
  ElMessage.success('人员账号已保存'); dialog.value = false; await load();
  if (creating) {
    const created = rows.value.find((row) => row.username === username);
    if (created && created.roles.length === 0) {
      ElMessage.warning('新账号尚未分配岗位，登录后无法进入车间功能，请先分配岗位权限');
      authorize(created);
    }
  }
}
async function remove(row: UserProfile) {
  await ElMessageBox.confirm(`确认删除人员账号 ${row.username}？`, '删除确认', { type: 'warning' });
  await systemApi.deleteUser(row.id); await load();
}
async function toggle(row: UserProfile) {
  await systemApi.saveUser(row.id, { username: row.username, displayName: row.displayName, password: '', enabled: !row.enabled });
  await load();
}
function authorize(row: UserProfile) {
  roleUser.value = row;
  selectedRoleIds.value = roles.value.filter((role) => row.roles.includes(role.code)).map((role) => role.id);
  roleDialog.value = true;
}
async function saveRoles() {
  if (!roleUser.value) return;
  await systemApi.assignUserRoles(roleUser.value.id, selectedRoleIds.value);
  ElMessage.success('岗位已分配'); roleDialog.value = false; await load();
}
onMounted(load);
</script>

<template>
  <div class="page">
    <PageHeader :title="PAGE_COPY['/system/users'].title" :subtitle="PAGE_COPY['/system/users'].subtitle" />
    <div class="panel action-panel">
      <el-button v-if="auth.has('USER_WRITE')" type="primary" @click="open()">新增人员</el-button>
      <el-button @click="load">刷新</el-button>
    </div>
    <div class="panel table-panel">
      <el-table v-loading="loading" :data="rows">
        <el-table-column prop="username" label="工号" />
        <el-table-column prop="displayName" label="姓名" />
        <el-table-column label="状态"><template #default="{row}"><el-tag :type="row.enabled?'success':'info'">{{ row.enabled?'在岗':'停用' }}</el-tag></template></el-table-column>
        <el-table-column label="岗位"><template #default="{row}">{{ row.roles.join(', ') || '-' }}</template></el-table-column>
        <el-table-column label="操作" width="300">
          <template #default="{row}">
            <el-button v-if="auth.has('USER_WRITE')" link type="primary" @click="open(row)">编辑</el-button>
            <el-button v-if="auth.has('USER_WRITE')" link :type="row.enabled?'warning':'success'" @click="toggle(row)">{{ row.enabled?'停用':'启用' }}</el-button>
            <el-button v-if="auth.has('USER_AUTHORIZE')" link type="primary" @click="authorize(row)">分配岗位</el-button>
            <el-button v-if="auth.has('USER_WRITE')" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
    </div>
    <el-dialog v-model="dialog" :title="editingId?'编辑人员':'新增人员'" width="520">
      <el-form :model="form" label-width="90px">
        <el-form-item label="工号"><el-input v-model="form.username" maxlength="64" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.displayName" maxlength="100" /></el-form-item>
        <el-form-item :label="editingId?'新密码':'密码'"><el-input v-model="form.password" type="password" show-password maxlength="128" /></el-form-item>
        <el-form-item label="在岗"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="roleDialog" title="分配岗位" width="480">
      <el-checkbox-group v-model="selectedRoleIds"><el-checkbox v-for="role in roles" :key="role.id" :value="role.id">{{ role.name }}（{{ role.code }}）</el-checkbox></el-checkbox-group>
      <template #footer><el-button @click="roleDialog=false">取消</el-button><el-button type="primary" @click="saveRoles">保存</el-button></template>
    </el-dialog>
  </div>
</template>
