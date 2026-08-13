package com.ticket.ticket.oss.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.ticket.ticket.oss.OssProperties;
import com.ticket.ticket.oss.OssService;
import com.ticket.ticket.oss.impl.AliyunOssService;
import com.ticket.ticket.oss.impl.LocalOssService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 附件存储 Bean 装配（ticket 12，ADR-0014）。
 * <p>
 * 依据 {@code aliyun.oss.enabled} 二选一装配（条件互斥）：
 * <ul>
 *     <li>{@code enabled=true}  → {@code OSSClient}（{@code destroyMethod="shutdown"} 随应用关闭释放）
 *         + {@link AliyunOssService}（真实阿里云 OSS）</li>
 *     <li>{@code enabled=false}（默认，{@code matchIfMissing=true}）→ {@link LocalOssService}
 *         （本地文件降级，无阿里云账号的开发 / 测试环境）</li>
 * </ul>
 * <p>
 * 两个 Service Bean 的返回类型都声明为 {@link OssService}，业务方只依赖接口，
 * 不感知当前走哪条存储路径（存储切换仅改配置，不动代码）。
 */
@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssConfig {

    /**
     * 阿里云 OSS 客户端 —— 仅 {@code aliyun.oss.enabled=true} 时装配。
     * <p>
     * {@code destroyMethod = "shutdown"}：Spring 容器关闭时自动关闭 OSSClient
     * 内部线程池 / 连接池（AC：client closed on app shutdown）。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "aliyun.oss", name = "enabled", havingValue = "true")
    public OSS ossClient(OssProperties properties) {
        return new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret());
    }

    /** 真实 OSS 存储实现 —— 与 {@link #localOssService} 条件互斥 */
    @Bean
    @ConditionalOnProperty(prefix = "aliyun.oss", name = "enabled", havingValue = "true")
    public OssService aliyunOssService(OSS ossClient, OssProperties properties) {
        return new AliyunOssService(ossClient, properties);
    }

    /** 本地文件降级实现 —— 默认装配（{@code enabled} 缺失 / 为 false） */
    @Bean
    @ConditionalOnProperty(prefix = "aliyun.oss", name = "enabled", havingValue = "false", matchIfMissing = true)
    public OssService localOssService(OssProperties properties) {
        return new LocalOssService(properties);
    }
}
