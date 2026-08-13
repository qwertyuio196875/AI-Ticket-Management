package com.ticket.ticket.oss;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 附件上传前置校验（ticket 12 AC：文件大小 ≤ 50MB + mime 白名单）。
 * <p>
 * 两个 {@link OssService} 实现（{@link com.ticket.ticket.oss.impl.AliyunOssService} /
 * {@link com.ticket.ticket.oss.impl.LocalOssService}）在 {@code upload} 开头
 * 统一调用 {@link #validate(MultipartFile)}，避免同一套规则在两条路径各写一份。
 * <p>
 * 校验失败一律抛 {@link BusinessException}（{@code PARAM_INVALID}，{@code C0400}），
 * 由全局异常处理包装返回前端，message 全部中文。
 * <p>
 * <b>mime 白名单</b>：图片（jpeg/png/gif/webp/bmp）+ 常见办公文档（pdf/word/excel/zip/txt）。
 * 前端 type 校验是体验优化，后端校验才是安全边界 —— 此处以
 * {@code MultipartFile.getContentType()} 为准。
 */
public final class OssFileValidator {

    /** 单文件上限：50MB（与前端限制一致） */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    /** 允许上传的 mime 类型白名单 */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp",
            "application/pdf",
            "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/zip", "application/x-zip-compressed"
    );

    private OssFileValidator() {
    }

    /**
     * 校验待上传文件：空文件 / 超 50MB / 非白名单 mime 一律拒绝。
     *
     * @param file 待上传文件
     * @throws BusinessException {@code PARAM_INVALID}（{@code C0400}）当校验不通过
     */
    public static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "文件大小不能超过 50MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "不支持的文件类型：" + contentType);
        }
    }
}
