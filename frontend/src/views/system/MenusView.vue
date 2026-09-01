<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { systemApi, type Menu } from '../../api/management';
import { useAuthStore } from '../../stores/auth';
import PageHeader from '../../components/PageHeader.vue';
import { PAGE_COPY } from '../../navigation';

const auth=useAuthStore(); const rows=ref<Menu[]>([]); const dialog=ref(false); const editingId=ref<number|null>(null);
const form=reactive<Omit<Menu,'id'>>({parentId:null,name:'',path:'',permissionCode:null,sortOrder:0,enabled:true});
async function load(){ rows.value=(await systemApi.menus()).sort((a,b)=>a.sortOrder-b.sortOrder); }
function open(row?:Menu){editingId.value=row?.id??null;Object.assign(form,{parentId:row?.parentId??null,name:row?.name??'',path:row?.path??'',permissionCode:row?.permissionCode??null,sortOrder:row?.sortOrder??0,enabled:row?.enabled??true});dialog.value=true;}
async function save(){await systemApi.saveMenu(editingId.value,form);dialog.value=false;ElMessage.success('菜单已保存');await load();}
async function remove(row:Menu){await ElMessageBox.confirm(`确认删除菜单 ${row.name}？`,'删除确认',{type:'warning'});await systemApi.deleteMenu(row.id);await load();}
onMounted(load);
</script>
<template>
  <div class="page">
    <PageHeader :title="PAGE_COPY['/system/menus'].title" :subtitle="PAGE_COPY['/system/menus'].subtitle" />
    <div class="panel action-panel"><el-button v-if="auth.has('MENU_WRITE')" type="primary" @click="open()">新增菜单</el-button><el-button @click="load">刷新</el-button></div>
    <div class="panel table-panel"><el-table :data="rows">
      <el-table-column prop="name" label="名称"/><el-table-column prop="path" label="路径"/><el-table-column prop="permissionCode" label="权限码"/><el-table-column prop="parentId" label="父菜单 ID"/><el-table-column prop="sortOrder" label="排序" width="80"/>
      <el-table-column label="启用" width="80"><template #default="{row}"><el-tag :type="row.enabled?'success':'info'">{{row.enabled?'是':'否'}}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="150"><template #default="{row}"><el-button v-if="auth.has('MENU_WRITE')" link type="primary" @click="open(row)">编辑</el-button><el-button v-if="auth.has('MENU_WRITE')" link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
    </el-table></div>
    <el-dialog v-model="dialog" :title="editingId?'编辑菜单':'新增菜单'" width="560">
      <el-form :model="form" label-width="100"><el-form-item label="父菜单 ID"><el-input-number v-model="form.parentId" :min="1" clearable/></el-form-item><el-form-item label="名称"><el-input v-model="form.name" maxlength="100"/></el-form-item><el-form-item label="路径"><el-input v-model="form.path" maxlength="255"/></el-form-item><el-form-item label="权限码"><el-input v-model="form.permissionCode" maxlength="100" clearable/></el-form-item><el-form-item label="排序"><el-input-number v-model="form.sortOrder"/></el-form-item><el-form-item label="启用"><el-switch v-model="form.enabled"/></el-form-item></el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
