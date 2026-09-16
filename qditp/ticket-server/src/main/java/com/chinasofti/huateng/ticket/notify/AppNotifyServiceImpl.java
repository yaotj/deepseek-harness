package com.chinasofti.huateng.ticket.notify;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/**
 * APP 通知服务默认实现 —— <b>只做异步提交与分派，不含任何报文组装或 HTTP 细节</b>。
 *
 * <p>2026-09-14 由 425 行拆成一壳 + 三协作者（ADR-D61）。原类里住着**两条完全不同的外发链路**：
 * <ul>
 *   <li>{@link IndustryDataNotifier} —— 调 industry-data-server 生码 + 推 APP 网关，<b>MUST 显式判 retCode</b>
 *       （实测对方返 7004 时 HTTP 仍是 200）</li>
 *   <li>{@link AlipayTripNotifier} —— 查线路 + 推支付宝，<b>只判 HTTP 2xx</b>（对方无业务码约定）</li>
 *   <li>{@link NotifyFormRequestFactory} —— 两条链路共用的 form-data 骨架（8 个字段，均属对外契约）</li>
 * </ul>
 * 两条链路只共享骨架与线程池，**判定口径却相反** —— 混在一个类里最容易把一侧的判定「顺手统一」到另一侧。
 *
 * <p><b>两个方法的形状 MUST 保持一致：提交到 {@code appNotifyExecutor} + 任务内 catch 全部异常只记日志。</b>
 * 异步任务抛出去没有任何人接（既不回滚也不重试），漏掉 catch 只会让异常彻底无声。
 * <b>NEVER 把任何一条改回同步</b> —— {@code pushAlipayTripData} 曾是同步的，外部 HTTPS 往返最坏 20s
 * 直接加在过闸应答路径上，闸机一超时就重发同一笔，而两条链路都**没有幂等键**，重发即重复推送。
 *
 * <p><b>NEVER 在本类里加业务判断</b>（开关、渠道选 URL、retCode 判定都在各自的 Notifier 里）：
 * 本类存在的唯一理由是「异步边界」与「对 {@code gate} 包只暴露一个 {@link AppNotifyService} 接口」。
 */
@Service
public class AppNotifyServiceImpl implements AppNotifyService {

    private static final Logger log = LoggerFactory.getLogger(AppNotifyServiceImpl.class);

    private final Executor appNotifyExecutor;
    private final IndustryDataNotifier industryDataNotifier;
    private final AlipayTripNotifier alipayTripNotifier;

    public AppNotifyServiceImpl(@Qualifier("appNotifyExecutor") Executor appNotifyExecutor,
                                IndustryDataNotifier industryDataNotifier,
                                AlipayTripNotifier alipayTripNotifier) {
        this.appNotifyExecutor = appNotifyExecutor;
        this.industryDataNotifier = industryDataNotifier;
        this.alipayTripNotifier = alipayTripNotifier;
    }

    @Override
    public void notifyVerifyResult(NotifyVerifyResultReqDTO request, QRCodeStatus qrCodeStatus, String gateCardType) {
        appNotifyExecutor.execute(() -> {
            try {
                industryDataNotifier.notifyVerifyResult(request, qrCodeStatus, gateCardType);
            } catch (Exception e) {
                log.error("异步推送 APP 行业数据异常, request={}", request, e);
            }
        });
    }

    @Override
    public void pushAlipayTripData(NotifyVerifyResultReqDTO request) {
        appNotifyExecutor.execute(() -> {
            try {
                alipayTripNotifier.pushTripData(request);
            } catch (Exception e) {
                log.error("推送行程数据给支付宝异常, request={}", request, e);
            }
        });
    }
}
