package com.chinasofti.huateng.gatetxnpay.controller.app;

import com.chinasofti.huateng.gatetxnpay.service.GateTxnPayQueryService;
import com.chinasofti.huateng.model.app.QueryTransListReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.model.app.RequestUserAccInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestUserAccInfoResult;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * APP 场景入口（IF8A-*）。2026-09-14 从 {@code GateTxnPayController} 拆出，与运营后台
 * {@code controller/page} 和对账 {@code controller/internal} 三分，按**调用方**分文件。
 *
 * <p><b>类上前缀是 {@code /ci/gateTxnPay/app}，与拆分前逐字相同</b>：这些路径是 APP 侧
 * （经 fep-app-server / ticket-server 转发）的既有契约，<b>NEVER 借重构改任何一个字符</b>，
 * 也 NEVER 把某个端点挪到别的前缀下 —— 上游是硬编码 URL 调过来的。</p>
 *
 * <p>本类只做参数搬运与日志，业务在 service 层；全部为只读查询端点。
 * IF8A-26 补款下单已随补款功能整体迁移到 face-pay-server（2026-09-15）。</p>
 */
@RestController
@RequestMapping("/ci/gateTxnPay/app")
public class GateTxnPayAppController {
    private static final Logger log = LoggerFactory.getLogger(GateTxnPayAppController.class);

    @Autowired
    private GateTxnPayQueryService gateTxnPayQueryService;

    // ==================== IF8A-05 APP 交易记录列表 ====================

    @PostMapping("/requestTransList")
    public List<GateTxnPayListDTO> requestTransList(@RequestBody QueryTransListReqDTO request) {
        log.info("APP查询交易记录列表, 入参={}", request);
        List<GateTxnPayListDTO> list = gateTxnPayQueryService.selectTransList(
                request.getThirdUserId(),
                request.getCardIdList(),
                request.getCardType(),
                request.getCardTypeList(),
                request.getStartDate(),
                request.getEndDate(),
                request.getTicketCode(),
                request.getDebitRequestResult(),
                request.getOffset(),
                request.getLimit());
        log.info("APP查询交易记录列表完成, 返回{}条", list == null ? 0 : list.size());
        return list;
    }

    @PostMapping("/countTransList")
    public int countTransList(@RequestBody QueryTransListReqDTO request) {
        log.info("APP统计交易记录总数, 入参={}", request);
        int count = gateTxnPayQueryService.countTransList(
                request.getThirdUserId(),
                request.getCardIdList(),
                request.getCardType(),
                request.getCardTypeList(),
                request.getStartDate(),
                request.getEndDate(),
                request.getTicketCode(),
                request.getDebitRequestResult());
        log.info("APP统计交易记录总数完成, 返回={}", count);
        return count;
    }

    // ==================== IF8A-41 APP 账单统计 ====================

    /**
     * IF8A-41 账单统计：返回原价 / 实付 / 优惠 / 超时费四个合计与订单数。
     *
     * <p>只读接口。口径见 {@link com.chinasofti.huateng.model.app.TripDataDTO} 类注释；
     * 票种白名单与 {@code cardType → cardTypeList} 展开由 ticket-server 侧完成。</p>
     */
    @PostMapping("/requestTransStatistics")
    public RequestTransStatisticsResult requestTransStatistics(@RequestBody RequestTransStatisticsReqDTO request) {
        log.info("IF8A-41 APP查询账单统计, 入参={}", request);
        RequestTransStatisticsResult response = gateTxnPayQueryService.requestTransStatistics(request);
        log.info("IF8A-41 APP查询账单统计完成, 返回={}", response == null ? null : response.getTripData());
        return response;
    }

    // ==================== IF8A-34 APP 订单详情 ====================

    @PostMapping("/queryByOrderNo")
    public GateTxnPayListDTO queryByOrderNo(@RequestBody Map<String, String> request) {
        String orderNo = request != null ? request.get("orderNo") : null;
        log.info("APP查询订单详情, orderNo={}", orderNo);
        GateTxnPayListDTO dto = gateTxnPayQueryService.selectByOrderNo(orderNo);
        log.info("APP查询订单详情完成, 返回={}", dto != null ? dto.getOrderNo() : null);
        return dto;
    }

    // ==================== IF8A-35 APP 用户账务信息 ====================

    /**
     * IF8A-35 查询用户账务信息：返回未支付订单数与扣费失败订单数，供 APP 做欠费提醒。
     *
     * <p>只读接口，不改任何数据。统计范围是 {@code GATE_TXN_PAY} 近若干月
     * （{@code app.acc-info.query-months}），分档口径见
     * {@link com.chinasofti.huateng.model.app.RequestUserAccInfoResult} 类注释。</p>
     *
     * <p>调用方 MUST 先判断 retCode 再用两个数量；本接口结果**只用于展示**，
     * NEVER 拿它替代过闸或解约链路各自的欠费校验。</p>
     */
    @PostMapping("/requestUserAccInfo")
    public RequestUserAccInfoResult requestUserAccInfo(@RequestBody RequestUserAccInfoReqDTO request) {
        log.info("IF8A-35 查询用户账务信息, 入参={}", request);
        RequestUserAccInfoResult response = gateTxnPayQueryService.requestUserAccInfo(request);
        log.info("IF8A-35 查询用户账务信息返回={}", response);
        return response;
    }
}
