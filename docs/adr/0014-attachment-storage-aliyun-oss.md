# 附件存储：阿里云 OSS

工单附件直接使用**阿里云 OSS**（Object Storage Service）存储：

- **SDK**：`aliyun-sdk-oss`（官方 Java SDK，Maven 引入）
- **配置**：`application.yml` 配 endpoint / accessKeyId / accessKeySecret / bucketName
- **上传流程**：前端 → 后端 `OssService.upload(file)` → 返回访问 URL → 入库 `ticket_attachment.file_url`
- **元数据**：`ticket_attachment` 表存 `file_url / file_name / size / mime_type / uploader_id / upload_time`
- **访问 URL**：私有 bucket 用签名 URL（有效期 1 小时）；公开 bucket 用公开读 URL

## 为什么

阿里云 OSS 是国内对象存储的事实标准，国内云服务器访问稳定。官方 SDK 简单易用，对实习项目"真用过云服务"是加分项。

## 简化

- 不做预签名直传（前端→OSS 直传需要前端拿签名，加复杂度）
- 不做病毒扫描
- 不做分片上传（大文件场景用不到）

## 面试怎么说

"我用阿里云 OSS 存工单附件，通过官方 Java SDK 上传，配了 accessKey 和 endpoint。私有 bucket 用签名 URL 访问，保证安全性"。

## 影响

- `pom.xml` 加 `aliyun-sdk-oss` 依赖
- `OssService` 封装 `upload / getSignedUrl / delete`
- 测试环境（无 OSS 账号）用本地文件系统 fallback，正式环境接 OSS
- 阿里云账号需自己注册 + 实名 + 创建 Bucket + 拿到 accessKey