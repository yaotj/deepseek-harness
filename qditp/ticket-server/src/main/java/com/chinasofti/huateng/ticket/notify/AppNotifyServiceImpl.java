package com.chinasofti.huateng.ticket.notify;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/** APP 通知服务默认实现 —— 只做异步提交与分派，不含任何报文组装或 HTTP 细节。 */
@Service
public class AppNotifyServiceImpl implements AppNotifyService {

    private static final Logger log = LoggerFactory.getLogger(AppNotifyServiceImpl.class);

    private final Executor appNotifyExecutor;
    private final IndustryDataNotifier industryDataNotifier;
    private final AlipayTripNotifier alipayTripNotifier;
    private final CountingTicketTimesNotifier countingTicketTimesNotifier;

    public AppNotifyServiceImpl(@Qualifier("appNotifyExecutor") Executor appNotifyExecutor,
                                IndustryDataNotifier industryDataNotifier,
                                AlipayTripNotifier alipayTripNotifier,
                                CountingTicketTimesNotifier countingTicketTimesNotifier) {
        this.appNotifyExecutor = appNotifyExecutor;
        this.industryDataNotifier = industryDataNotifier;
        this.alipayTripNotifier = alipayTripNotifier;
        this.countingTicketTimesNotifier = countingTicketTimesNotifier;
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

    @Override
    public void notifyCountingTicketTimes(NotifyVerifyResultReqDTO request, int times) {
        appNotifyExecutor.execute(() -> {
            try {
                countingTicketTimesNotifier.notifyCountingTimes(request, times);
            } catch (Exception e) {
                log.error("异步推送多日票次数扣减通知异常, request={}", request, e);
            }
        });
    }
}
