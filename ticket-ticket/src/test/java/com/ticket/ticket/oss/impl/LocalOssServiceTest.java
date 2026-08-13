package com.ticket.ticket.oss.impl;

import com.ticket.ticket.oss.OssProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link LocalOssService} 单元测试（ticket 12 AC：本地文件降级）。
 * <p>
 * 纯 JUnit 5 + Mockito + {@code @TempDir}（真实目录，无外部依赖），毫秒级运行。
 * <p>
 * <b>覆盖</b>：
 * <ul>
 *     <li>{@code upload}：文件写入 {@code {localPath}/ticket/{yyyyMMdd}/{uuid32}.{ext}}，
 *         返回含 {@code localPath} 前缀的本地 key</li>
 *     <li>{@code getSignedUrl}：直接返回本地文件路径（降级模式无签名机制）</li>
 *     <li>{@code delete}：删除后文件不存在</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LocalOssServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    MultipartFile file;

    private LocalOssService newService() {
        OssProperties props = new OssProperties();
        props.setLocalPath(tempDir.toString());
        return new LocalOssService(props);
    }

    @Test
    @DisplayName("upload：文件写入 localPath 下且内容一致，返回含 localPath 前缀的 key")
    void upload_writes_file_and_returns_local_key() throws IOException {
        byte[] content = "本地附件内容".getBytes(StandardCharsets.UTF_8);
        when(file.getOriginalFilename()).thenReturn("截图.png");
        when(file.getContentType()).thenReturn("image/png");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        LocalOssService service = newService();

        String key = service.upload(file);

        Pattern pattern = Pattern.compile(Pattern.quote(tempDir.toString())
                + "/ticket/\\d{8}/[0-9a-f]{32}\\.png");
        assertThat(key).startsWith(tempDir.toString()).matches(pattern.pattern());
        Path written = Path.of(key);
        assertThat(Files.exists(written)).isTrue();
        assertThat(Files.readAllBytes(written)).isEqualTo(content);
    }

    @Test
    @DisplayName("getSignedUrl：直接返回本地文件路径（降级模式无签名）")
    void getSignedUrl_returns_local_path() {
        LocalOssService service = newService();
        String key = tempDir.resolve("ticket/20260813/abc.pdf").toString();

        String url = service.getSignedUrl(key, null);

        assertThat(url).isEqualTo(key);
    }

    @Test
    @DisplayName("delete：删除后文件不存在")
    void delete_removes_file() throws IOException {
        Path target = tempDir.resolve("ticket/20260813/abc.pdf");
        Files.createDirectories(target.getParent());
        Files.write(target, new byte[0]);
        LocalOssService service = newService();

        service.delete(target.toString());

        assertThat(Files.exists(target)).isFalse();
    }
}
