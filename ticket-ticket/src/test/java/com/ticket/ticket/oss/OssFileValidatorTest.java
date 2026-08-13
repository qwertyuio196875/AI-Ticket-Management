package com.ticket.ticket.oss;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link OssFileValidator} 单元测试（ticket 12 AC：文件大小 ≤ 50MB + mime 白名单）。
 * <p>
 * 纯 JUnit 5 + Mockito，不启动 Spring 上下文，毫秒级运行。
 * <p>
 * <b>覆盖</b>：
 * <ul>
 *     <li>空文件（{@code null} 引用 + {@code isEmpty()}）→ 拒绝</li>
 *     <li>超过 50MB → 拒绝</li>
 *     <li>非白名单 mime → 拒绝</li>
 *     <li>白名单 mime → 通过</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OssFileValidatorTest {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    @Mock
    MultipartFile file;

    @Test
    @DisplayName("null 文件被拒绝：抛 PARAM_INVALID（文件不能为空）")
    void null_file_rejected() {
        assertThatThrownBy(() -> OssFileValidator.validate(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文件不能为空")
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.PARAM_INVALID.getCode());
    }

    @Test
    @DisplayName("空文件被拒绝：抛 PARAM_INVALID（文件不能为空）")
    void empty_file_rejected() {
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> OssFileValidator.validate(file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文件不能为空")
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.PARAM_INVALID.getCode());
    }

    @Test
    @DisplayName("超过 50MB 被拒绝：抛 PARAM_INVALID（文件大小不能超过 50MB）")
    void oversize_file_rejected() {
        when(file.getSize()).thenReturn(MAX_FILE_SIZE + 1);

        assertThatThrownBy(() -> OssFileValidator.validate(file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文件大小不能超过 50MB")
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.PARAM_INVALID.getCode());
    }

    @Test
    @DisplayName("非白名单 mime 被拒绝：抛 PARAM_INVALID（不支持的文件类型）")
    void disallowed_mime_rejected() {
        when(file.getContentType()).thenReturn("application/x-msdownload");

        assertThatThrownBy(() -> OssFileValidator.validate(file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不支持的文件类型：application/x-msdownload")
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.PARAM_INVALID.getCode());
    }

    @Test
    @DisplayName("白名单 mime 通过：不抛异常")
    void allowed_mime_passes() {
        when(file.getContentType()).thenReturn("image/png");

        assertThatCode(() -> OssFileValidator.validate(file))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("恰好 50MB 通过：等于上限不拒绝")
    void exactly_max_size_passes() {
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getSize()).thenReturn(MAX_FILE_SIZE);

        assertThatCode(() -> OssFileValidator.validate(file))
                .doesNotThrowAnyException();
    }
}
