package com.ticket.ticket.oss;

import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/**
 * 附件存储服务抽象（ticket 12，ADR-0014）。
 * <p>
 * 业务模块（{@code TicketAttachmentService}）只依赖本接口，不感知底层实现：
 * <ul>
 *     <li>{@link com.ticket.ticket.oss.impl.AliyunOssService} —— 真实阿里云 OSS
 *         （{@code aliyun.oss.enabled=true} 时装配）</li>
 *     <li>{@link com.ticket.ticket.oss.impl.LocalOssService} —— 本地文件降级
 *         （默认，无阿里云账号的开发 / 测试环境）</li>
 * </ul>
 * <p>
 * <b>key 约定</b>：上传返回的 {@code fileKey} 形如
 * {@code ticket/{yyyyMMdd}/{uuid32}.{ext}}，同时作为数据库 {@code file_url} 字段值
 * 与后续 {@link #delete} / {@link #getSignedUrl} 的入参 —— 不暴露底层存储路径细节。
 * <p>
 * <b>签名 URL</b>：私有 bucket 用 {@link #getSignedUrl(String, Duration)} 生成
 * 一次性访问地址（默认有效期 1 小时），前端凭此下载 / 预览附件。
 */
public interface OssService {

    /**
     * 上传附件。
     * <p>
     * 实现开头统一调用 {@link OssFileValidator#validate(MultipartFile)} 做
     * 大小（≤ 50MB）与 mime 白名单校验；校验失败抛 {@code PARAM_INVALID}。
     *
     * @param file 待上传文件
     * @return 存储 key（如 {@code ticket/20260812/xxxx.pdf}）
     */
    String upload(MultipartFile file);

    /**
     * 删除附件对象（软删元数据前的物理删除，失败时元数据保留可重试）。
     *
     * @param fileKey 上传时返回的存储 key
     */
    void delete(String fileKey);

    /**
     * 生成签名访问 URL（私有 bucket 用）。
     *
     * @param fileKey 上传时返回的存储 key
     * @param ttl     有效期；{@code null} 时默认 1 小时
     * @return 带签名的可访问 URL
     */
    String getSignedUrl(String fileKey, Duration ttl);

    /**
     * 生成签名访问 URL —— 便捷重载，ttl 取默认 1 小时。
     *
     * @param fileKey 上传时返回的存储 key
     * @return 带签名的可访问 URL
     */
    default String getSignedUrl(String fileKey) {
        return getSignedUrl(fileKey, null);
    }
}
