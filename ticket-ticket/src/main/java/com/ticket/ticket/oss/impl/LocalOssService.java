package com.ticket.ticket.oss.impl;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.ticket.oss.OssFileValidator;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

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

    private static final String KEY_PREFIX = "ticket/";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OssProperties properties;

    public LocalOssService(OssProperties properties) {
        this.properties = properties;
    }

    @Override
    public String upload(MultipartFile file) {
        // 大小 / mime 白名单校验，失败抛 PARAM_INVALID（C0400）
        OssFileValidator.validate(file);
        String key = buildKey(file.getOriginalFilename());
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
     * Javadoc 说明：业务侧拿到的是文件系统绝对路径，生产环境不得以该实现上线。
     */
    @Override
    public String getSignedUrl(String fileKey, Duration ttl) {
        return fileKey;
    }

    /**
     * 组装本地 key：{@code {localPath}/ticket/{yyyyMMdd}/{uuid32}[.{ext}]}。
     * <p>
     * 用 {@code '/'} 拼接，跨平台一致；{@link Path#of(String)} 在 Windows 上
     * 同样能解析混合分隔符路径。
     */
    private String buildKey(String originalFilename) {
        String base = properties.getLocalPath().endsWith("/") || properties.getLocalPath().endsWith("\\")
                ? properties.getLocalPath()
                : properties.getLocalPath() + "/";
        String date = LocalDate.now().format(DATE_FORMAT);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String ext = extractExtension(originalFilename);
        return ext.isEmpty()
                ? base + KEY_PREFIX + date + "/" + uuid
                : base + KEY_PREFIX + date + "/" + uuid + "." + ext;
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
