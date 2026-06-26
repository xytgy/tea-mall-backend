# 秒杀活动管理前端实现提示词

## 任务

根据后端 API 文档实现秒杀活动管理的前端功能。

## 参考文档

- API 文档：`docs/flash-sale-admin-api.md`
- 现有 API 文件参考：`frontend/src/api/admin.ts`
- 请求工具参考：`frontend/src/utils/request.ts`

## 实现要求

### 1. 创建 API 文件

创建 `frontend/src/api/flash-sale.ts`，包含：
- TypeScript 类型定义（FlashSaleCreateRequest, FlashSaleVO, FlashSaleProductVO 等）
- 6 个 API 方法：getFlashSaleListApi, createFlashSaleApi, getFlashSaleDetailApi, addFlashSaleProductApi, updateFlashSaleStatusApi, deleteFlashSaleApi

### 2. 创建秒杀活动管理页面

创建 `frontend/src/views/admin/flash-sale/` 目录，包含：

**index.vue（活动列表页）：**
- 调用 getFlashSaleListApi 获取所有活动
- 展示所有秒杀活动列表
- 支持按状态筛选（未开始/进行中/已结束）
- 每行显示：活动标题、时间、状态、操作按钮
- 操作按钮：查看详情、修改状态、删除
- 状态显示：0=未开始(蓝色), 1=进行中(绿色), 2=已结束(红色)

**create.vue（创建活动页）：**
- 表单字段：活动标题、开始时间、结束时间
- 商品列表：支持动态添加商品（商品ID、秒杀价、库存、限购数）
- 提交后跳转到详情页

**detail.vue（活动详情页）：**
- 调用 getFlashSaleDetailApi 获取活动详情
- 显示活动基本信息
- 商品列表表格：商品名称、原价、秒杀价、库存、限购数
- 支持添加新商品
- 支持修改活动状态
- 支持删除活动

### 3. 添加路由

在 `frontend/src/router/` 中添加秒杀活动管理路由。

### 4. 代码风格要求

- 使用 Vue 3 + TypeScript + Composition API
- 使用 Element Plus 组件库
- 遵循现有项目的代码风格
- API 调用使用 `frontend/src/utils/request.ts` 中的 request 实例

## API 接口速查

| 功能 | 方法 | 路径 |
|------|------|------|
| 获取列表 | GET | /admin/flash-sale/list |
| 创建活动 | POST | /admin/flash-sale/create |
| 获取详情 | GET | /admin/flash-sale/{id} |
| 添加商品 | POST | /admin/flash-sale/{id}/products |
| 修改状态 | PUT | /admin/flash-sale/{id}/status |
| 删除活动 | DELETE | /admin/flash-sale/{id} |
