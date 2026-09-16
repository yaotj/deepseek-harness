package com.chinasofti.huateng.collectpay.service.impl;

import com.chinasofti.huateng.collectpay.entity.TvmAppOrder;
import com.chinasofti.huateng.collectpay.mapper.TvmAppOrderMapper;
import com.chinasofti.huateng.collectpay.mapper.TvmOrderPreMapper;
import com.chinasofti.huateng.collectpay.service.AppPayOrderInternalService;
import com.chinasofti.huateng.collectpay.utils.DateUtils;
import com.chinasofti.huateng.model.collectpay.AppPayOrderCloseReqDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderRegisterReqDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderResultRespDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link AppPayOrderInternalService} 实现。字段口径与幂等约束见接口注释。
 */
@Slf4j
@Service
public class AppPayOrderInternalServiceImpl implements AppPayOrderInternalService {

    private static final String RET_SUCCESS = "0000";
    private static final String RET_INVALID_PARAM = "8001";

    /** 待支付，取值来自 {@code ItpStatusEnum.PAYING}；{@code requestPayInfo} 只对这个状态发起预下单。 */
    private static final String PAY_STATUS_UNPAID = "0";

    /** 登记时固定写 1，因为 {@code requestPayInfo} 的金额是 {@code TICKET_PRICE × TICKET_NUM}。 */
    private static final String FIXED_TICKET_NUM = "1";

    /** 未发起支付 / 未激活。{@code requestPreActiveOrderList} 只捞 {@code ACTIVATE_FLAG='1'}， */
    /** 因此写 {@code '0'} 就不会混进乘客的单程票列表里。 */
    private static final String FLAG_NO = "0";

    private final TvmAppOrderMapper tvmAppOrderMapper;
    private final TvmOrderPreMapper tvmOrderPreMapper;

    public AppPayOrderInternalServiceImpl(TvmAppOrderMapper tvmAppOrderMapper,
                                          TvmOrderPreMapper tvmOrderPreMapper) {
        this.tvmAppOrderMapper = tvmAppOrderMapper;
        this.tvmOrderPreMapper = tvmOrderPreMapper;
    }

    /**
     * 两张表同一个本地事务。全程无 RPC，因此可以安全地包事务（AGENTS.md §5.2）。
     * <b>NEVER</b> 在本方法内新增任何远端调用。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppPayOrderRespDTO register(AppPayOrderRegisterReqDTO request) {
        String invalid = validateRegister(request);
        if (invalid != null) {
            log.warn("内部接口-登记APP订单参数校验失败, request={}, msg={}", request, invalid);
            return AppPayOrderRespDTO.reject(RET_INVALID_PARAM, invalid,
                    request == null ? null : request.getOrderNo());
        }

        String orderNo = request.getOrderNo().trim();
        TvmAppOrder exists = tvmAppOrderMapper.selectByOrderNo(orderNo);
        if (exists != null) {
            log.info("内部接口-登记APP订单幂等跳过，该单已登记, orderNo={}, payStatus={}", orderNo, exists.getPayStatus());
            return AppPayOrderRespDTO.success(orderNo, "已登记");
        }

        try {
            tvmAppOrderMapper.insert(buildAppOrder(request, orderNo));
            tvmOrderPreMapper.insert(buildPreOrder(request, orderNo));
        } catch (Exception e) {
            if (!isIntegrityViolation(e)) {
                throw e;
            }
            // 上面的 selectByOrderNo 是 check-then-act，并发重放会在主键上撞出来。
            // 撞了说明另一条并发请求已经登记成功，按幂等返成功而不是让对方进补偿队列重推。
            // 判定 MUST 沿 cause 链：本模块开了 tracing，观测切面会把异常换类型（AGENTS.md §5.2）。
            log.warn("内部接口-登记APP订单主键冲突，按并发幂等处理, orderNo={}", orderNo, e);
            return AppPayOrderRespDTO.success(orderNo, "已登记");
        }

        log.info("内部接口-登记APP订单完成, orderNo={}, userId={}, totalAmount={}, transType={}",
                orderNo, request.getUserId(), request.getTotalAmount(), request.getTransType());
        return AppPayOrderRespDTO.success(orderNo, "成功");
    }

    @Override
    public AppPayOrderRespDTO closeUnpaid(AppPayOrderCloseReqDTO request) {
        String orderNo = request == null ? null : StringUtils.trimToNull(request.getOrderNo());
        if (orderNo == null) {
            log.warn("内部接口-关闭APP待支付订单参数缺失, request={}", request);
            return AppPayOrderRespDTO.reject(RET_INVALID_PARAM, "orderNo不能为空", null);
        }
        int closed = tvmAppOrderMapper.closeUnpaidByOrderNo(orderNo, request.getMsg());
        // 影响 0 行是正常结果：行不存在、或已支付 / 已失败。关单的语义是「保证乘客付不了」，
        // 目标已达成即返成功。NEVER 把 0 行当失败返回——调用方会当成可重试，白重推。
        log.info("内部接口-关闭APP待支付订单完成, orderNo={}, closed={}", orderNo, closed);
        return AppPayOrderRespDTO.success(orderNo, closed > 0 ? "成功" : "该订单已非待支付状态");
    }

    @Override
    public AppPayOrderResultRespDTO queryPayResult(String orderNo) {
        String key = StringUtils.trimToNull(orderNo);
        if (key == null) {
            AppPayOrderResultRespDTO resp = new AppPayOrderResultRespDTO();
            resp.setRetCode(RET_INVALID_PARAM);
            resp.setRetMsg("orderNo不能为空");
            resp.setFound(false);
            return resp;
        }
        TvmAppOrder order = tvmAppOrderMapper.selectByOrderNo(key);
        if (order == null) {
            return AppPayOrderResultRespDTO.notFound(key);
        }
        AppPayOrderResultRespDTO resp = new AppPayOrderResultRespDTO();
        resp.setRetCode(RET_SUCCESS);
        resp.setRetMsg("成功");
        resp.setFound(true);
        resp.setOrderNo(key);
        resp.setPayStatus(order.getPayStatus());
        resp.setPayAmount(order.getPayAmount());
        resp.setPayTime(order.getPayTime());
        resp.setPayChannelCode(order.getPayChannelCode());
        resp.setMerchantOrderNo(order.getMerchantOrderNo());
        resp.setPaymentInfo(order.getPaymentInfo());
        return resp;
    }

    // ==================== 私有辅助 ====================

    private String validateRegister(AppPayOrderRegisterReqDTO request) {
        if (request == null) {
            return "请求体不能为空";
        }
        if (StringUtils.isBlank(request.getOrderNo())) {
            return "orderNo不能为空";
        }
        if (StringUtils.isBlank(request.getUserId())) {
            return "userId不能为空";
        }
        if (StringUtils.isBlank(request.getTotalAmount())) {
            return "totalAmount不能为空";
        }
        // RSV2 为空会被 refundAppNotTakeTickets 当成购票未取票全额退款，
        // 因此在入口就拒绝，NEVER 在这里给个默认值放行——默认值等于替调用方定资损口径。
        if (StringUtils.isBlank(request.getSupplementFlag())) {
            return "supplementFlag不能为空，它是挡住购票未取票自动退款的开关";
        }
        // TRANS_TYPE 决定 payNotice 的分派，为空时回调查不到前置单、状态永远停在待支付。
        if (StringUtils.isBlank(request.getTransType())) {
            return "transType不能为空，支付中心回调按它分派";
        }
        return null;
    }

    private TvmAppOrder buildAppOrder(AppPayOrderRegisterReqDTO request, String orderNo) {
        TvmAppOrder order = new TvmAppOrder();
        order.setOrderNo(orderNo);
        order.setUserId(request.getUserId());
        // 三列都写全额：requestPayInfo 只读 TICKET_PRICE 与 TICKET_NUM，
        // payNotice 与对账读 PAY_AMOUNT，页面展示读 totalPrice。
        order.setTicketPrice(request.getTotalAmount());
        order.setTicketNum(FIXED_TICKET_NUM);
        order.setTotalPrice(request.getTotalAmount());
        order.setPayAmount(request.getTotalAmount());
        order.setTicketType(request.getTicketType());
        order.setPayStatus(PAY_STATUS_UNPAID);
        order.setMsg(request.getMsg());
        order.setRequestPayFlag(FLAG_NO);
        order.setActivateFlag(FLAG_NO);
        order.setRsv1(request.getCardId());
        order.setRsv2(request.getSupplementFlag());
        order.setCreateTime(DateUtils.getNowTime());
        return order;
    }

    private Map<String, Object> buildPreOrder(AppPayOrderRegisterReqDTO request, String orderNo) {
        Map<String, Object> preMap = new HashMap<>();
        preMap.put("orderNo", orderNo);
        preMap.put("transType", request.getTransType());
        preMap.put("transAmount", request.getTotalAmount());
        preMap.put("deviceId", request.getDeviceId());
        preMap.put("createTime", DateUtils.getNowTime());
        return preMap;
    }

    /**
     * 沿 {@code getCause()} 链判定是否为完整性冲突。
     *
     * <p><b>NEVER 只看最外层类名</b>：本模块开了 tracing，{@code resource/micro/web} 的观测切面
     * 历史上会把异常重新包一层，只 {@code catch (DuplicateKeyException)} 会静默落空
     * （AGENTS.md §5.2 / ADR-D53）。</p>
     */
    private boolean isIntegrityViolation(Throwable e) {
        Throwable cursor = e;
        while (cursor != null) {
            if (cursor instanceof DataIntegrityViolationException) {
                return true;
            }
            cursor = cursor.getCause() == cursor ? null : cursor.getCause();
        }
        return false;
    }
}
