# 秒杀活动管理 API 接口文档

## 概述

本文档描述秒杀活动管理的后端 API 接口，供前端开发使用。

**基础信息：**
- 基础路径：`/admin/flash-sale`
- 认证方式：Bearer Token（需管理员权限）
- 响应格式：统一 `Result<T>` 结构

```typescript
// 统一响应结构
interface Result<T> {
  success: boolean
  code: number
  message: string
  data: T
}
```

---

## 1. 获取秒杀活动列表

**请求：**
- 方法：`GET`
- 路径：`/admin/flash-sale/list`

**响应：**
```typescript
// data: 活动列表
type Response = Result<FlashSaleVO[]>

interface FlashSaleVO {
  id: number                    // 活动ID
  title: string                 // 活动标题
  startTime: string             // 开始时间
  endTime: string               // 结束时间
  status: number                // 状态：0未开始 1进行中 2已结束
  products?: FlashSaleProductVO[]  // 商品列表（列表接口不返回此字段）
}
```

**响应示例：**
```json
{
  "success": true,
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "title": "618限时抢",
      "startTime": "2026-06-18T00:00:00",
      "endTime": "2026-06-18T23:59:59",
      "status": 0
    },
    {
      "id": 2,
      "title": "双11特惠",
      "startTime": "2026-11-11T00:00:00",
      "endTime": "2026-11-11T23:59:59",
      "status": 1
    }
  ]
}
```

---

## 2. 创建秒杀活动

**请求：**
- 方法：`POST`
- 路径：`/admin/flash-sale/create`
- Content-Type：`application/json`

**请求体：**
```typescript
interface FlashSaleCreateRequest {
  title: string          // 活动标题，必填
  startTime: string      // 开始时间，ISO 8601 格式，必填
  endTime: string        // 结束时间，ISO 8601 格式，必填
  products?: FlashSaleProductItem[]  // 初始商品列表，可选
}

interface FlashSaleProductItem {
  productId: number      // 商品ID，必填
  flashPrice: number     // 秒杀价，必填，必须小于原价
  totalStock: number     // 秒杀库存，必填，必须大于0
  maxPerUser: number     // 每人限购数，可选，默认1
}
```

**请求示例：**
```json
{
  "title": "618限时抢",
  "startTime": "2026-06-18T00:00:00",
  "endTime": "2026-06-18T23:59:59",
  "products": [
    {
      "productId": 9,
      "flashPrice": 99.00,
      "totalStock": 100,
      "maxPerUser": 1
    }
  ]
}
```

**响应：**
```typescript
// data: 新创建的活动ID
type Response = Result<number>

// 成功示例
{
  "success": true,
  "code": 200,
  "message": "创建成功",
  "data": 1
}
```

**错误码：**
| code | message | 说明 |
|------|---------|------|
| 400 | 开始时间必须早于结束时间 | 时间校验失败 |
| 400 | 商品不存在 | 商品ID无效 |
| 400 | 秒杀价必须小于原价 | 价格校验失败 |
| 400 | 库存不能超过商品实际库存 | 库存校验失败 |
| 400 | 该商品已添加到此活动 | 重复添加商品 |

---

## 3. 获取秒杀活动详情

**请求：**
- 方法：`GET`
- 路径：`/admin/flash-sale/{id}`
- 路径参数：`id` - 活动ID

**响应：**
```typescript
interface FlashSaleVO {
  id: number                    // 活动ID
  title: string                 // 活动标题
  startTime: string             // 开始时间
  endTime: string               // 结束时间
  status: number                // 状态：0未开始 1进行中 2已结束
  products?: FlashSaleProductVO[]  // 商品列表
}

interface FlashSaleProductVO {
  id: number                    // 记录ID
  productId: number             // 商品ID
  productName: string           // 商品名称
  productImage: string          // 商品图片URL
  originalPrice: number         // 商品原价
  flashPrice: number            // 秒杀价
  totalStock: number            // 秒杀总库存
  remainingStock: number        // 剩余库存
  maxPerUser: number            // 每人限购数
}

// data: 活动详情
type Response = Result<FlashSaleVO>
```

**响应示例：**
```json
{
  "success": true,
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "title": "618限时抢",
    "startTime": "2026-06-18T00:00:00",
    "endTime": "2026-06-18T23:59:59",
    "status": 0,
    "products": [
      {
        "id": 1,
        "productId": 9,
        "productName": "特级高山龙井茶",
        "productImage": "https://example.com/image.jpg",
        "originalPrice": 298.00,
        "flashPrice": 99.00,
        "totalStock": 100,
        "remainingStock": 100,
        "maxPerUser": 1
      }
    ]
  }
}
```

**错误码：**
| code | message | 说明 |
|------|---------|------|
| 404 | 活动不存在 | 活动ID无效 |

---

## 4. 添加商品到秒杀活动

**请求：**
- 方法：`POST`
- 路径：`/admin/flash-sale/{id}/products`
- 路径参数：`id` - 活动ID
- Content-Type：`application/json`

**请求体：**
```typescript
interface FlashSaleAddProductRequest {
  productId: number      // 商品ID，必填
  flashPrice: number     // 秒杀价，必填
  totalStock: number     // 秒杀库存，必填
  maxPerUser: number     // 每人限购数，可选，默认1
}
```

**请求示例：**
```json
{
  "productId": 10,
  "flashPrice": 68.00,
  "totalStock": 50,
  "maxPerUser": 2
}
```

**响应：**
```typescript
// data: null
type Response = Result<null>

// 成功示例
{
  "success": true,
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

**错误码：**
| code | message | 说明 |
|------|---------|------|
| 404 | 活动不存在 | 活动ID无效 |
| 400 | 只能向未开始的活动添加商品 | 活动状态不是"未开始" |
| 400 | 商品不存在 | 商品ID无效 |
| 400 | 秒杀价必须小于原价 | 价格校验失败 |
| 400 | 库存不能超过商品实际库存 | 库存校验失败 |
| 400 | 该商品已添加到此活动 | 重复添加商品 |

---

## 5. 修改秒杀活动状态

**请求：**
- 方法：`PUT`
- 路径：`/admin/flash-sale/{id}/status`
- 路径参数：`id` - 活动ID
- Content-Type：`application/json`

**请求体：**
```typescript
interface FlashSaleStatusRequest {
  status: number  // 状态值，必填：0未开始 1进行中 2已结束
}
```

**请求示例：**
```json
{
  "status": 1
}
```

**响应：**
```typescript
// data: null
type Response = Result<null>

// 成功示例
{
  "success": true,
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

**错误码：**
| code | message | 说明 |
|------|---------|------|
| 404 | 活动不存在 | 活动ID无效 |
| 400 | 已结束的活动不能修改状态 | 活动已结束 |

---

## 6. 删除秒杀活动

**请求：**
- 方法：`DELETE`
- 路径：`/admin/flash-sale/{id}`
- 路径参数：`id` - 活动ID

**响应：**
```typescript
// data: null
type Response = Result<null>

// 成功示例
{
  "success": true,
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

**错误码：**
| code | message | 说明 |
|------|---------|------|
| 404 | 活动不存在 | 活动ID无效 |

---

## 状态说明

| status | 含义 | 说明 |
|--------|------|------|
| 0 | 未开始 | 活动创建后默认状态，可添加商品 |
| 1 | 进行中 | 活动正在进行，用户可参与秒杀 |
| 2 | 已结束 | 活动已结束，不可修改状态 |

**状态流转规则：**
- 未开始(0) → 进行中(1)：手动设置或自动（根据时间）
- 进行中(1) → 已结束(2)：手动设置或自动（根据时间）
- 已结束(2)：终态，不可修改

---

## 前端实现要点

### 1. API 文件结构

```typescript
// frontend/src/api/flash-sale.ts

import request, { type Result } from '../utils/request'

// 类型定义
export interface FlashSaleCreateRequest {
  title: string
  startTime: string
  endTime: string
  products?: FlashSaleProductItem[]
}

export interface FlashSaleProductItem {
  productId: number
  flashPrice: number
  totalStock: number
  maxPerUser: number
}

export interface FlashSaleAddProductRequest {
  productId: number
  flashPrice: number
  totalStock: number
  maxPerUser: number
}

export interface FlashSaleStatusRequest {
  status: number
}

export interface FlashSaleVO {
  id: number
  title: string
  startTime: string
  endTime: string
  status: number
  products?: FlashSaleProductVO[]
}

export interface FlashSaleProductVO {
  id: number
  productId: number
  productName: string
  productImage: string
  originalPrice: number
  flashPrice: number
  totalStock: number
  remainingStock: number
  maxPerUser: number
}

// API 方法
export const getFlashSaleListApi = () => {
  return request.get<Result<FlashSaleVO[]>>('/admin/flash-sale/list')
}

export const createFlashSaleApi = (data: FlashSaleCreateRequest) => {
  return request.post<Result<number>>('/admin/flash-sale/create', data)
}

export const getFlashSaleDetailApi = (id: number) => {
  return request.get<Result<FlashSaleVO>>(`/admin/flash-sale/${id}`)
}

export const addFlashSaleProductApi = (id: number, data: FlashSaleAddProductRequest) => {
  return request.post<Result<null>>(`/admin/flash-sale/${id}/products`, data)
}

export const updateFlashSaleStatusApi = (id: number, data: FlashSaleStatusRequest) => {
  return request.put<Result<null>>(`/admin/flash-sale/${id}/status`, data)
}

export const deleteFlashSaleApi = (id: number) => {
  return request.delete<Result<null>>(`/admin/flash-sale/${id}`)
}
```

### 2. 页面功能建议

| 页面 | 功能 |
|------|------|
| 活动列表 | 展示所有秒杀活动，支持状态筛选 |
| 创建活动 | 表单：标题、时间、商品选择 |
| 活动详情 | 展示活动信息和商品列表，支持添加商品、修改状态 |
| 删除确认 | 二次确认弹窗 |

### 3. 状态显示

```typescript
const statusMap: Record<number, { label: string; type: string }> = {
  0: { label: '未开始', type: 'info' },
  1: { label: '进行中', type: 'success' },
  2: { label: '已结束', type: 'danger' }
}
```
