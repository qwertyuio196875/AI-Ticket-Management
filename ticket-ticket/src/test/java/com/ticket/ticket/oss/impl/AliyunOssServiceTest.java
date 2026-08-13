package com.ticket.ticket.oss.impl;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.OSSException;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.ticket.oss.OssProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AliyunOssService} 单元测试（ticket 12 AC：mock {@code OSSClient}）。
 * <p>
 * 纯 JUnit 5 + Mockito，不启动 Spring 上下文，毫秒级运行。
 * <p>
 * <b>覆盖</b>：
 * <ul>
 *     <li>{@code upload}：返回 {@code ticket/{yyyyMMdd}/{uuid32}.{ext}} 格式 key，
 *         {@code putObject} 使用正确 bucket；OSS 异常 → {@code A0201 "OSS上传失败"}</li>
 *     <li>{@code getSignedUrl}：ttl 为 null 时默认 1 小时过期，返回签名 URL</li>
 *     <li>{@code delete}：调用 {@code deleteObject}；OSS 异常 → {@code A0202 "OSS删除失败"}</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AliyunOssServiceTest {

    private static final String BUCKET = "ticket-bucket";
    private static final String ENDPOINT = "oss-cn-hangzhou.aliyuncs.com";
    private static final String AK_ID = "test-ak";
    private static final String AK_SECRET = "test-sk";
    /** 32 位无横线 UUID + 小写扩展名 */
    private static final Pattern KEY_PATTERN = Pattern.compile("ticket/\\d{8}/[0-9a-f]{32}\\.pdf");

    @Mock
    OSSClient client;
    @Mock
    MultipartFile file;

    private AliyunOssService newService() {
        OssProperties props = new OssProperties();
        props.setEndpoint(ENDPOINT);
        props.setAccessKeyId(AK_ID);
        props.setAccessKeySecret(AK_SECRET);
        props.setBucketName(BUCKET);
        return new AliyunOssService(client, props);
    }

    @Test
    @DisplayName("upload 成功：返回 ticket/{yyyyMMdd}/{uuid32}.{ext} 格式 key，putObject 用正确 bucket")
    void upload_returns_formatted_key() throws IOException {
        when(file.getOriginalFilename()).thenReturn("报销单.PDF");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        AliyunOssService service = newService();

        String key = service.upload(file);

        assertThat(key).matches(KEY_PATTERN);
        verify(client).putObject(eq(BUCKET), eq(key), any(InputStream.class));
    }

    @Test
    @DisplayName("upload 无扩展名文件名：key 不带 .ext")
    void upload_filename_without_extension() throws IOException {
        when(file.getOriginalFilename()).thenReturn("README");
        when(file.getContentType()).thenReturn("text/plain");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        AliyunOssService service = newService();

        String key = service.upload(file);

        assertThat(key).matches(Pattern.compile("ticket/\\d{8}/[0-9a-f]{32}"));
        verify(client).putObject(eq(BUCKET), eq(key), any(InputStream.class));
    }

    @Test
    @DisplayName("upload putObject 抛 OSSException：BusinessException code=A0201，message=OSS上传失败")
    void upload_oss_exception_throws_A0201() throws IOException {
        when(file.getOriginalFilename()).thenReturn("a.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        doThrow(new OSSException("boom")).when(client).putObject(anyString(), anyString(), any(InputStream.class));
        AliyunOssService service = newService();

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("OSS上传失败")
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.OSS_UPLOAD_FAILED.getCode());
    }

    @Test
    @DisplayName("getSignedUrl ttl 为 null：默认 1 小时过期，返回签名 URL")
    void getSignedUrl_null_ttl_defaults_one_hour() throws Exception {
        String fileKey = "ticket/20260812/abc.pdf";
        when(client.generatePresignedUrl(anyString(), anyString(), any(Date.class)))
                .thenReturn(new URL("https://ticket-bucket.oss-cn-hangzhou.aliyuncs.com/ticket/20260812/abc.pdf?sign"));
        AliyunOssService service = newService();

        String url = service.getSignedUrl(fileKey, null);

        ArgumentCaptor<Date> expiryCaptor = ArgumentCaptor.forClass(Date.class);
        verify(client).generatePresignedUrl(eq(BUCKET), eq(fileKey), expiryCaptor.capture());
        long diffMs = expiryCaptor.getValue().getTime() - System.currentTimeMillis();
        // 容差 10 秒：验证过期时间 ≈ now + 1h
        assertThat(diffMs).isBetween(3600_000L - 10_000L, 3600_000L + 10_000L);
        assertThat(url).isEqualTo("https://ticket-bucket.oss-cn-hangzhou.aliyuncs.com/ticket/20260812/abc.pdf?sign");
    }

    @Test
    @DisplayName("delete 成功：deleteObject 被调用")
    void delete_success() {
        AliyunOssService service = newService();

        service.delete("ticket/20260812/abc.pdf");

        verify(client).deleteObject(BUCKET, "ticket/20260812/abc.pdf");
    }

    @Test
    @DisplayName("delete 抛 OSSException：BusinessException code=A0202，message=OSS删除失败")
    void delete_oss_exception_throws_A0202() {
        doThrow(new OSSException("boom")).when(client).deleteObject(anyString(), anyString());
        AliyunOssService service = newService();

        assertThatThrownBy(() -> service.delete("ticket/20260812/abc.pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("OSS删除失败")
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.OSS_DELETE_FAILED.getCode());
    }
}
