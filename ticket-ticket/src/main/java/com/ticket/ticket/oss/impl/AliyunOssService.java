package com.ticket.ticket.oss.impl;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
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
import java.net.URL;
import java.time.Duration;
import java.util.Date;

/**
 * 阿里云 OSS 附件存储实现（ticket 12，ADR-0014）。
 * <p>
 * 由 {@code OssConfig} 在 {@code aliyun.oss.enabled=true} 时装配；本类只做
 * OSS 对象读写，不落库（元数据由 {@code TicketAttachmentService} 负责）。
 * <p>
 * <b>key 格式</b>：{@code ticket/{yyyyMMdd}/{32位无横线uuid}.{原始文件名小写扩展名}}
 * —— 拼装规则统一收敛在 {@link OssObjectKey}（与本地降级实现共用）。
 * <p>
 * <b>失败语义</b>：{@code putObject / deleteObject} 抛
 * {@link OSSException}（服务端）/ {@link ClientException}（网络侧）/ IO 异常时，
 * 统一转 {@link BusinessException}（{@code A0201} 上传失败 / {@code A0202} 删除失败），
 * 由全局异常处理包装返回；此时附件元数据不落库（上传方 {@code Service} 已约定"先存后删"）。
 */
public class AliyunOssService implements OssService {

    private static final Logger log = LoggerFactory.getLogger(AliyunOssService.class);

    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    private final OSS client;
    private final OssProperties properties;

    public AliyunOssService(OSS client, OssProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String upload(MultipartFile file) {
        // 大小 / mime 白名单校验，失败抛 PARAM_INVALID（C0400）
        OssFileValidator.validate(file);
        String key = OssObjectKey.buildKey(OssObjectKey.extractExtension(file.getOriginalFilename()));
        try {
            client.putObject(properties.getBucketName(), key, file.getInputStream());
            log.info("OSS 上传成功: bucket={}, key={}, size={}B", properties.getBucketName(), key, file.getSize());
            return key;
        } catch (OSSException | ClientException | IOException e) {
            log.error("OSS 上传失败: bucket={}, key={}, size={}B", properties.getBucketName(), key, file.getSize(), e);
            throw BusinessException.of(BusinessExceptionCode.OSS_UPLOAD_FAILED);
        }
    }

    @Override
    public void delete(String fileKey) {
        try {
            client.deleteObject(properties.getBucketName(), fileKey);
            log.info("OSS 删除成功: bucket={}, key={}", properties.getBucketName(), fileKey);
        } catch (OSSException | ClientException e) {
            log.error("OSS 删除失败: bucket={}, key={}", properties.getBucketName(), fileKey, e);
            throw BusinessException.of(BusinessExceptionCode.OSS_DELETE_FAILED);
        }
    }

    @Override
    public String getSignedUrl(String fileKey, Duration ttl) {
        Duration effective = ttl == null ? DEFAULT_TTL : ttl;
        Date expiry = new Date(System.currentTimeMillis() + effective.toMillis());
        URL url = client.generatePresignedUrl(properties.getBucketName(), fileKey, expiry);
        return url.toString();
    }
}
