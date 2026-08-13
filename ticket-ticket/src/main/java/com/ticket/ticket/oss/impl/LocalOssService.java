package com.ticket.ticket.oss.impl;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.ticket.oss.OssFileValidator;
import com.ticket.ticket.oss.OssObjectKey;
import com.ticket.ticket.oss.OssProperties;
import com.ticket.ticket.oss.OssService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * 本地文件降级存储实现（ticket 12 AC：无阿里云账号时开发环境可用）。
 * <p>
 * 由 {@code OssConfig} 在 {@code aliyun.oss.enabled=false}（默认）时装配。
 * 文件落到 {@code OssProperties.localPath} 目录，key 形如
 * {@code {localPath}/ticket/{yyyyMMdd}/{uuid32}.{ext}}（含 localPath 前缀，
 * 与业务侧"file_url 存 key"的约定保持一致）。
 * <p>
 * <b>与真实 OSS 的差异</b>：
 * <ul>
 *     <li>{@code getSignedUrl} 直接返回本地文件路径 —— 本地降级模式无签名机制，
 *         仅开发 / 测试环境使用，生产环境必须启用真实 OSS</li>
 *     <li>{@code upload} 失败 / {@code delete} 失败同样抛 {@code A0201} / {@code A0202}，
 *         与 {@link AliyunOssService} 语义一致，调用方无需感知实现差异</li>
 * </ul>
 */
public class LocalOssService implements OssService {

    private static final Logger log = LoggerFactory.getLogger(LocalOssService.class);

    private final OssProperties properties;

    public LocalOssService(OssProperties properties) {
        this.properties = properties;
    }

    @Override
    public String upload(MultipartFile file) {
        // 大小 / mime 白名单校验，失败抛 PARAM_INVALID（C0400）
        OssFileValidator.validate(file);
        String key = localPrefix() + OssObjectKey.buildKey(OssObjectKey.extractExtension(file.getOriginalFilename()));
        Path target = Path.of(key);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("本地附件上传成功: {}", key);
            return key;
        } catch (IOException e) {
            log.error("本地附件上传失败: {}", key, e);
            throw BusinessException.of(BusinessExceptionCode.OSS_UPLOAD_FAILED);
        }
    }

    @Override
    public void delete(String fileKey) {
        try {
            boolean deleted = Files.deleteIfExists(Path.of(fileKey));
            log.info("本地附件删除: key={}, deleted={}", fileKey, deleted);
        } catch (IOException e) {
            log.error("本地附件删除失败: {}", fileKey, e);
            throw BusinessException.of(BusinessExceptionCode.OSS_DELETE_FAILED);
        }
    }

    /**
     * 本地降级模式无签名机制 —— 直接返回本地文件路径（仅开发 / 测试环境使用）。
     * <p>
     * 注意：返回值是 <b>本地文件系统路径</b>（{@code file_url} 列本地模式存的
     * 含 {@code localPath} 前缀的路径本身），<b>不是 HTTP URL</b>，浏览器不能直接访问；
     * 生产环境必须启用真实 OSS（{@code aliyun.oss.enabled=true}），此时
     * {@code file_url} 存对象 key（{@code ticket/...}），本实现不得上线。
     * <p>
     * <b>file_url 双语义</b>：OSS 模式存对象 key、本地降级模式存含
     * {@code localPath} 前缀的本地路径，两种语义由 {@code enabled} 开关决定，
     * 业务层（{@code TicketAttachmentService}）无需感知。
     */
    @Override
    public String getSignedUrl(String fileKey, Duration ttl) {
        return fileKey;
    }

    /**
     * 本地根目录（含结尾分隔符）—— 与 {@link OssObjectKey} 产出的相对 key 拼接。
     * <p>
     * 用 {@code '/'} 拼接，跨平台一致；{@link Path#of(String)} 在 Windows 上
     * 同样能解析混合分隔符路径。
     */
    private String localPrefix() {
        return properties.getLocalPath().endsWith("/") || properties.getLocalPath().endsWith("\\")
                ? properties.getLocalPath()
                : properties.getLocalPath() + "/";
    }
}
