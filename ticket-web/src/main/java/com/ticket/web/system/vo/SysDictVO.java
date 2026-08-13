package com.ticket.web.system.vo;

import com.ticket.system.entity.SysDict;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 数据字典 VO —— 前端展示用，屏蔽内部细节。
 */
@Schema(description = "数据字典展示对象")
public record SysDictVO(
        Long id,
        String dictType,
        String dictValue,
        String dictLabel,
        Integer sort,
        Integer status,
        String remark,
        LocalDateTime createTime
) {

    public static SysDictVO from(SysDict dict) {
        return new SysDictVO(
                dict.getId(),
                dict.getDictType(),
                dict.getDictValue(),
                dict.getDictLabel(),
                dict.getSort(),
                dict.getStatus(),
                dict.getRemark(),
                dict.getCreateTime());
    }
}
