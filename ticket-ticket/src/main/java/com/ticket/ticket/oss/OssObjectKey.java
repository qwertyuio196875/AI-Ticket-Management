package com.ticket.ticket.oss;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 附件对象存储 key 生成帮助类（ticket 12，ADR-0014）。
 * <p>
 * 两个存储实现（{@link com.ticket.ticket.oss.impl.AliyunOssService} /
 * {@link com.ticket.ticket.oss.impl.LocalOssService}）共用的 key 拼装逻辑：
 * <pre>{@code
 * ticket/{yyyyMMdd}/{32位无横线uuid}[.{小写扩展名}]
 * }</pre>
 * <ul>
 *     <li>按天分目录 —— 便于冷热分层与定期清理</li>
 *     <li>32 位无横线 UUID —— 防重名、不可预测</li>
 *     <li>扩展名取原始文件名最后一个 {@code '.'} 之后的部分并转小写；
 *         无 {@code '.'} 或 {@code '.'} 结尾视为无扩展名（不带 {@code .ext}）</li>
 * </ul>
 * <p>
 * <b>与 localPath 的关系</b>：本类只产出相对 key（{@code ticket/...} 开头），
 * 本地降级实现（LocalOssService）在调用处自行拼接 {@code localPath} 前缀。
 */
public final class OssObjectKey {

    /** key 顶层目录前缀 */
    private static final String KEY_PREFIX = "ticket/";

    /** 日期段格式：yyyyMMdd */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private OssObjectKey() {
    }

    /**
     * 组装对象 key：{@code ticket/{yyyyMMdd}/{uuid32}[.{ext}]}。
     *
     * @param extension 小写扩展名（不含点）；为空串时 key 不带 {@code .ext}
     * @return 对象 key
     */
    public static String buildKey(String extension) {
        String date = LocalDate.now().format(DATE_FORMAT);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return extension == null || extension.isEmpty()
                ? KEY_PREFIX + date + "/" + uuid
                : KEY_PREFIX + date + "/" + uuid + "." + extension;
    }

    /**
     * 从原始文件名提取小写扩展名。
     *
     * @param originalFilename 上传文件名，可为 null
     * @return 小写扩展名（不含点）；无扩展名 / 文件名以 {@code '.'} 结尾时返回空串
     */
    public static String extractExtension(String originalFilename) {
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
