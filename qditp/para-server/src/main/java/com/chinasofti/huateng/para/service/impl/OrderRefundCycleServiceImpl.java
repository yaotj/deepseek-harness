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

@Service
/** 退款周期参数校验与维护实现，周期范围由服务层和表约束双重保证。 */
public class OrderRefundCycleServiceImpl implements OrderRefundCycleService {
    private static final int MAX_REFUND_PERIOD_DAYS = 3650;
    private final OrderRefundCycleMapper orderRefundCycleMapper;

    public OrderRefundCycleServiceImpl(OrderRefundCycleMapper orderRefundCycleMapper) {
        this.orderRefundCycleMapper = orderRefundCycleMapper;
    }

    /** 票卡类型为唯一配置键，查询使用精确匹配。 */
    @Override
    public ResultVO<PageInfo<OrderRefundCycle>> page(String ticketType, Integer pageNum, Integer pageSize) {
        PageInfo<OrderRefundCycle> page = PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> orderRefundCycleMapper.selectPage(trimToNull(ticketType)));
        return ResultMapper.ok(page);
    }

    /** 先规范化输入，再将唯一键冲突转为明确提示。 */
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

    /**
     * 判断异常链上是否存在 {@link DuplicateKeyException}，即唯一约束冲突。
     *
     * <p><b>MUST</b> 逐层遍历 cause，<b>NEVER</b> 直接 {@code catch (DuplicateKeyException)}：
     * {@code MapperAspectToTrace}（{@code resource/micro/web/src/main/java/com/chinasofti/huateng/
     * micro/monitor/trace/MapperAspectToTrace.java:51}）把 mapper 抛出的任何异常统一包成
     * {@code RuntimeException}，单层类型判断在 {@code management.tracing.enabled=true}
     * 的模块（para-server 即是）捕不到，唯一键冲突会漏成全局异常处理器的 UUID retCode。</p>
     */
    private boolean isDuplicateKeyViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }

    /** 更新以路径票卡类型为准，防止请求体修改配置键。 */
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

    /** 删除不存在的配置时返回业务错误，避免误以为删除成功。 */
    @Override
    public ResultVO<Void> delete(String ticketType) {
        if (!StringUtils.hasText(ticketType)) {
            return ResultMapper.illegalParams("票卡类型不能为空");
        }
        return orderRefundCycleMapper.deleteByTicketType(ticketType.trim()) > 0 ? ResultMapper.ok() : ResultMapper.error("未找到待删除的退款周期配置");
    }

    private String validate(OrderRefundCycle request, boolean requireTicketType) {
        // 该范围与 TBL_ORDER_REFUND_CYCLE 的 CHECK 约束保持一致。
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
