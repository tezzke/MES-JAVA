<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { systemApi, type Role } from '../../api/management';
import { useAuthStore } from '../../stores/auth';

const auth = useAuthStore();
const rows = ref<Role[]>([]);
const dialog = ref(false);
const permissionDialog = ref(false);
const editingId = ref<number | null>(null);
const selected = ref<string[]>([]);
const form = reactive({ code: '', name: '' });
const permissions = ref<string[]>([]);
async function load() {
  [rows.value, permissions.value] = await Promise.all([
    systemApi.roles(),
    systemApi.permissions(),
  ]);
}
function open(row?: Role) { editingId.value=row?.id??null; Object.assign(form,{code:row?.code??'',name:row?.name??''}); dialog.value=true; }
async function save() { await systemApi.saveRole(editingId.value,form); dialog.value=false; ElMessage.success('角色已保存'); await load(); }
async function remove(row: Role) { await ElMessageBox.confirm(`确认删除角色 ${row.name}？`,'删除确认',{type:'warning'}); await systemApi.deleteRole(row.id); await load(); }
function authorize(row: Role) { editingId.value=row.id; selected.value=[...row.permissions]; permissionDialog.value=true; }
async function savePermissions() { if(editingId.value==null)return; await systemApi.assignRolePermissions(editingId.value,selected.value); permissionDialog.value=false; await load(); }
onMounted(load);
</script>

<template>
  <div class="page">
    <div class="panel action-panel"><el-button v-if="auth.has('ROLE_WRITE')" type="primary" @click="open()">新增角色</el-button><el-button @click="load">刷新</el-button></div>
    <div class="panel table-panel">
      <el-table :data="rows">
        <el-table-column prop="code" label="角色编码" /><el-table-column prop="name" label="角色名称" />
        <el-table-column label="权限"><template #default="{row}">{{ row.permissions.join(', ') || '-' }}</template></el-table-column>
        <el-table-column label="操作" width="220"><template #default="{row}">
          <el-button v-if="auth.has('ROLE_WRITE')" link type="primary" @click="open(row)">编辑</el-button>
          <el-button v-if="auth.has('ROLE_AUTHORIZE')" link type="primary" @click="authorize(row)">分配权限</el-button>
          <el-button v-if="auth.has('ROLE_WRITE')" link type="danger" @click="remove(row)">删除</el-button>
        </template></el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="dialog" :title="editingId?'编辑角色':'新增角色'" width="480">
      <el-form :model="form" label-width="90"><el-form-item label="角色编码"><el-input v-model="form.code" maxlength="64" /></el-form-item><el-form-item label="角色名称"><el-input v-model="form.name" maxlength="100" /></el-form-item></el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="permissionDialog" title="分配权限" width="720">
      <el-checkbox-group v-model="selected" class="permission-grid"><el-checkbox v-for="permission in permissions" :key="permission" :value="permission">{{ permission }}</el-checkbox></el-checkbox-group>
      <template #footer><el-button @click="permissionDialog=false">取消</el-button><el-button type="primary" @click="savePermissions">保存</el-button></template>
    </el-dialog>
  </div>
</template>
<style scoped>.permission-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}</style>
