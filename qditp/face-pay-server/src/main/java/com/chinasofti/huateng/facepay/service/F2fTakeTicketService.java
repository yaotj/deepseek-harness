package com.chinasofti.huateng.facepay.service;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.DeviceRetCode;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestActiveTicketReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestTakeTicketAuthReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TvmResponses;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 扫码取票：IF8A-15 激活取票订单 + IF2A-08 取票鉴权查询。
 *
 * <p>手机在 TVM 上扫二维码 → {@code requestActiveTicket} 把订单绑到该台设备并写入二维码三要素
 * → TVM 用同样三要素调 {@code requestTakeTicketAuth} 取回订单详情后出票。</p>
 *
 * <h2>两处修掉的旧缺陷</h2>
 * <ul>
 *   <li><b>激活改成条件更新抢锁。</b>旧实现是无条件 {@code UPDATE ... WHERE ORDER_NO=?}，
 *       两台设备同时扫同一个码会双双成功，订单被后写的那台覆盖，前一台随后取票鉴权查不到。
 *       现在 WHERE 带 {@code ACTIVATE_DEVICE_ID IS NULL}，返回 0 即已被占用，回 2008。</li>
 *   <li><b>鉴权查询不再依赖「多查一条就报失败」。</b>旧实现 {@code selectByDeviceAndQrcode}
 *       返回 List，命中多条时回「查询到的订单数量过多，失败」；三要素本应唯一，
 *       {@code selectByQrcode} 直接取一行。</li>
 * </ul>
 *
 * <p><b>2026-09-16 按用户明确要求调整「查不到取票订单」的响应形态</b>：两条 {@code 2003} 分支
 * 由「只有 retCode/retMsg 的纯错误体」改为「与成功响应同形、8 个业务键值全为 JSON null」，
 * 码与文案仍是 {@code 2003 无激活的订单}。详见 {@link TvmResponses#takeTicketAuthNoActiveOrder}。</p>
 *
 * <p>本类无网络调用，也不带 {@code @Transactional}——两个方法各自只有一条写 SQL，
 * 靠条件更新本身保证原子性，加事务没有收益。</p>
 */
@Service
public class F2fTakeTicketService {

    private static final Logger log = LoggerFactory.getLogger(F2fTakeTicketService.class);

    /** 订单已支付、可激活的唯一前置状态。 */
    private static final String STATUS_PAID = F2fOrderStatus.PAID.name();

    /** {@code ACTIVATE_FLAG} 已激活。 */
    private static final String ACTIVATED = "1";

    private final F2fOrderMapper orderMapper;

    private final F2fPaymentMapper paymentMapper;

    public F2fTakeTicketService(F2fOrderMapper orderMapper, F2fPaymentMapper paymentMapper) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
    }

    /**
     * IF8A-15 激活取票订单。
     *
     * <p>文案逐字照搬旧实现；只有「已被其他设备激活」这一支从旧的 2999「激活失败」
     * 改成 2008 订单已锁定——设备侧靠码值区分「该重扫」还是「换一台机器」，
     * 2999 无法区分。</p>
     */
    public JSONObject requestActiveTicket(RequestActiveTicketReqDTO request) {
        String orderNo = request.getOrderNo();
        F2fOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.info("激活取票订单 订单不存在, orderNo={}", orderNo);
            return TvmResponses.fail(DeviceRetCode.ORDER_NO_ERROR, "订单号错误,没有找到匹配的订单");
        }
        if (!STATUS_PAID.equals(order.getOrderStatus())) {
            log.info("激活取票订单 订单不在已支付状态, orderNo={}, status={}", orderNo, order.getOrderStatus());
            return TvmResponses.fail(DeviceRetCode.FAIL, "该订单非支付成功，不可激活");
        }
        int activated = orderMapper.activateForDevice(orderNo, request.getDeviceId(),
                request.getQrcodeGenDate(), request.getRandomFact(), LocalDateTime.now());
        if (activated == 0) {
            log.warn("激活取票订单 未抢到（已被其他设备激活或状态已变）, orderNo={}, deviceId={}",
                    orderNo, request.getDeviceId());
            return TvmResponses.fail(DeviceRetCode.ORDER_LOCKED, "该订单已被其他设备激活");
        }
        log.info("激活取票订单成功, orderNo={}, deviceId={}", orderNo, request.getDeviceId());
        return TvmResponses.success();
    }

    /**
     * IF2A-08 取票鉴权查询。设备凭二维码三要素取回订单详情。
     *
     * <p>查不到 → 2003 无激活的订单；查到但未激活也回 2003。<b>两支的响应体都与成功响应同形</b>
     * （8 个业务键齐全、值为 JSON null），见 {@link TvmResponses#takeTicketAuthNoActiveOrder}。</p>
     *
     * <p>此处 NEVER 回退成旧 collect-pay 的 {@code failData(空订单)}：那是 {@code 2999} 码，
     * 且票数会变成字符串 {@code "null"}、设备侧可能误出 1 张票。</p>
     */
    public JSONObject requestTakeTicketAuth(RequestTakeTicketAuthReqDTO request) {
        F2fOrder order = orderMapper.selectByQrcode(
                request.getDeviceId(), request.getQrcodeGenDate(), request.getRandomFact());
        if (order == null) {
            log.info("取票鉴权 没有找到匹配的取票订单, deviceId={}, qrcodeGenDate={}",
                    request.getDeviceId(), request.getQrcodeGenDate());
            return TvmResponses.takeTicketAuthNoActiveOrder();
        }
        if (!ACTIVATED.equals(order.getActivateFlag())) {
            log.info("取票鉴权 订单未激活, orderNo={}, activateFlag={}",
                    order.getOrderNo(), order.getActivateFlag());
            return TvmResponses.takeTicketAuthNoActiveOrder();
        }
        log.info("取票鉴权 命中激活订单, orderNo={}", order.getOrderNo());
        return TvmResponses.takeTicketAuthSuccess(order.getOrderNo(), order.getActivateDeviceId(),
                order.getEntryStationCode(), order.getExitStationCode(), order.getTicketPrice(),
                order.getTicketNum(), order.getSingleTicketType(), channelCodeOf(order.getOrderNo()));
    }

    /** 渠道码要等支付回调或查询结果才有值，可能为 null——旧实现同样回 null，不兜底。 */
    private String channelCodeOf(String orderNo) {
        F2fPayment last = paymentMapper.selectLastAttempt(orderNo);
        return last == null ? null : last.getPayChannelCode();
    }
}
