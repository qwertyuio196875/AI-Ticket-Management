package com.ticket.ticket.oss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 配置项（ticket 12，ADR-0014）。
 * <p>
 * 前缀 {@code aliyun.oss}，由 {@code OssConfig} 通过
 * {@code @EnableConfigurationProperties} 注册，配置来源：
 * <pre>{@code
 * aliyun:
 *   oss:
 *     enabled: ${ALIYUN_OSS_ENABLED:false}   # false 时走本地文件降级（LocalOssService）
 *     endpoint: ${ALIYUN_OSS_ENDPOINT:}
 *     access-key-id: ${ALIYUN_OSS_ACCESS_KEY_ID:}
 *     access-key-secret: ${ALIYUN_OSS_ACCESS_KEY_SECRET:}
 *     bucket-name: ${ALIYUN_OSS_BUCKET_NAME:}
 * }</pre>
 * <p>
 * <b>本地降级</b>：{@code enabled=false}（开发 / 测试机默认）时不用 OSS，
 * 文件落到 {@code localPath} 目录（{@link OssService} 的
 * {@link com.ticket.ticket.oss.impl.LocalOssService} 实现），
 * 保证无阿里云账号也能跑通附件全流程。
 */
@Data
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {

    /** OSS 区域端点，如 {@code oss-cn-hangzhou.aliyuncs.com} */
    private String endpoint;

    /** AccessKey Id */
    private String accessKeyId;

    /** AccessKey Secret */
    private String accessKeySecret;

    /** 存储桶名 */
    private String bucketName;

    /**
     * 是否启用真实 OSS。
     * <p>
     * {@code true} → 装配 {@code OSSClient} + {@code AliyunOssService}；
     * {@code false}（默认）→ 装配 {@code LocalOssService}（本地文件降级）。
     */
    private boolean enabled = false;

    /** 本地降级存储根目录（相对当前工作目录），默认 {@code ./tmp/oss/} */
    private String localPath = "./tmp/oss/";
}
