package com.ticket.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.system.entity.SysUser;
import com.ticket.system.mapper.SysUserMapper;
import com.ticket.ticket.entity.TicketInfo;
import com.ticket.ticket.entity.TicketLog;
import com.ticket.ticket.mapper.TicketInfoMapper;
import com.ticket.ticket.mapper.TicketLogMapper;
import com.ticket.ticket.service.TicketLogService;
import com.ticket.ticket.vo.TicketLogVO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link TicketLogService} 实现（ticket 14 工单时间线）。
 * <p>
 * 纯读路径：事件写入全部由各业务 Service 在同事务内完成（ADR-0012），
 * 本类只做聚合查询 + 操作人昵称拼装，不产生任何写操作。
 * <p>
 * <b>与 {@link TicketCommentServiceImpl#loadNicknames} 同一模式</b>：
 * 按 {@code operatorId} 集合批量查 {@code sys_user} 拼 {@code nickname}，
 * 避免 N+1 SQL；单用户查不到时 {@code operatorName} 保持 {@code null}
 * （系统事件如 {@code AI_CALLED} 的 {@code operatorId} 本身为 null）。
 */
@Service
public class TicketLogServiceImpl implements TicketLogService {

    private final TicketLogMapper ticketLogMapper;
    private final TicketInfoMapper ticketInfoMapper;
    private final SysUserMapper sysUserMapper;

    public TicketLogServiceImpl(TicketLogMapper ticketLogMapper,
                                TicketInfoMapper ticketInfoMapper,
                                SysUserMapper sysUserMapper) {
        this.ticketLogMapper = ticketLogMapper;
        this.ticketInfoMapper = ticketInfoMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public List<TicketLogVO> list(Long ticketId) {
        if (ticketId == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "工单 id 不能为空");
        }
        // 工单存在性校验 —— 与详情 / 状态机读路径一致（T0101），
        // 避免对不存在工单做无意义的事件查询
        TicketInfo ticket = ticketInfoMapper.selectById(ticketId);
        if (ticket == null) {
            throw BusinessException.of(BusinessExceptionCode.TICKET_NOT_FOUND);
        }

        // 排序：create_time ASC, id ASC —— 时间升序 + 同刻按落库顺序稳定展示
        LambdaQueryWrapper<TicketLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketLog::getTicketId, ticketId)
                .orderByAsc(TicketLog::getCreateTime)
                .orderByAsc(TicketLog::getId);
        List<TicketLog> rows = ticketLogMapper.selectList(wrapper);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量拼装操作人昵称（避免 N+1）
        Set<Long> operatorIds = rows.stream()
                .map(TicketLog::getOperatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> nicknameMap = loadNicknames(operatorIds);

        return rows.stream().map(l -> toVO(l, nicknameMap)).collect(Collectors.toList());
    }

    // ---------- 内部辅助 ----------

    private TicketLogVO toVO(TicketLog entity, Map<Long, String> nicknameMap) {
        TicketLogVO vo = new TicketLogVO();
        vo.setId(entity.getId());
        vo.setTicketId(entity.getTicketId());
        // 枚举名直接返，前端按字典渲染中文（与 TicketVO.status 枚举返回同一约定）
        vo.setEventType(entity.getEventType() == null ? null : entity.getEventType().name());
        vo.setOperatorId(entity.getOperatorId());
        if (entity.getOperatorId() != null) {
            vo.setOperatorName(nicknameMap.get(entity.getOperatorId()));
        }
        vo.setContent(entity.getContent());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /**
     * 批量加载操作人昵称，避免 N+1 SQL（与评论 creatorName 拼装同模式）。
     * 查不到的用户返回 null —— 调用方 {@code toVO} 仅在 operatorId 非空时取值。
     */
    private Map<Long, String> loadNicknames(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysUser::getId, userIds);
        List<SysUser> users = sysUserMapper.selectList(wrapper);
        Map<Long, String> map = new HashMap<>(users.size() * 2);
        for (SysUser u : users) {
            map.put(u.getId(), u.getNickname());
        }
        return map;
    }
}
