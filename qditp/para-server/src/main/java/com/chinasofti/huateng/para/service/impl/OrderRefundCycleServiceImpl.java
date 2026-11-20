package com.chinasofti.huateng.para.service.impl;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.ticket.OrderRefundCycle;
import com.chinasofti.huateng.para.mapper.ticket.OrderRefundCycleMapper;
import com.chinasofti.huateng.para.service.OrderRefundCycleService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Service
public class OrderRefundCycleServiceImpl implements OrderRefundCycleService {
    private static final int MAX_REFUND_PERIOD_DAYS = 3650;
    private final OrderRefundCycleMapper orderRefundCycleMapper;

    public OrderRefundCycleServiceImpl(OrderRefundCycleMapper orderRefundCycleMapper) {
        this.orderRefundCycleMapper = orderRefundCycleMapper;
    }

    @Override
    public ResultVO<PageInfo<OrderRefundCycle>> page(String ticketType, Integer pageNum, Integer pageSize) {
        PageInfo<OrderRefundCycle> page = PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> orderRefundCycleMapper.selectPage(trimToNull(ticketType)));
        return ResultMapper.ok(page);
    }

    @Override
    public ResultVO<Void> create(OrderRefundCycle request) {
        String validationMessage = validate(request, true);
        if (validationMessage != null) {
            return ResultMapper.illegalParams(validationMessage);
        }
        request.setTicketType(request.getTicketType().trim());
        request.setRemark(trimToNull(request.getRemark()));
        try {
            orderRefundCycleMapper.insert(request);
            return ResultMapper.ok();
        } catch (Exception exception) {
            if (!isDuplicateKeyViolation(exception)) throw exception;
            return ResultMapper.error("该票卡类型已配置退款周期");
        }
    }


    private boolean isDuplicateKeyViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ResultVO<Void> update(String ticketType, OrderRefundCycle request) {
        if (!StringUtils.hasText(ticketType)) {
            return ResultMapper.illegalParams("票卡类型不能为空");
        }
        String validationMessage = validate(request, false);
        if (validationMessage != null) {
            return ResultMapper.illegalParams(validationMessage);
        }
        request.setTicketType(ticketType.trim());
        request.setRemark(trimToNull(request.getRemark()));
        return orderRefundCycleMapper.update(request) > 0 ? ResultMapper.ok() : ResultMapper.error("未找到待修改的退款周期配置");
    }

    @Override
    public ResultVO<Void> delete(String ticketType) {
        if (!StringUtils.hasText(ticketType)) {
            return ResultMapper.illegalParams("票卡类型不能为空");
        }
        return orderRefundCycleMapper.deleteByTicketType(ticketType.trim()) > 0 ? ResultMapper.ok() : ResultMapper.error("未找到待删除的退款周期配置");
    }

    @Override
    public ResultVO<List<String>> listTicketTypes() {
        List<String> types = orderRefundCycleMapper.selectDistinctTicketTypes();
        return ResultMapper.ok(types != null ? types : Collections.emptyList());
    }

    private String validate(OrderRefundCycle request, boolean requireTicketType) {
        if (request == null) {
            return "请求参数不能为空";
        }
        if (requireTicketType && !StringUtils.hasText(request.getTicketType())) {
            return "票卡类型不能为空";
        }
        if (requireTicketType && request.getTicketType().trim().length() > 8) {
            return "票卡类型不能超过8个字符";
        }
        if (request.getAutoRefundPeriod() == null || request.getAutoRefundPeriod() < 0 || request.getAutoRefundPeriod() > MAX_REFUND_PERIOD_DAYS) {
            return "自动退款周期必须在 0 到 3650 天之间";
        }
        if (request.getRemark() != null && request.getRemark().length() > 500) {
            return "备注不能超过500个字符";
        }
        return null;
    }

    private int safePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int safePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
