package com.chinasofti.huateng.fep.dev.gate;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.fep.dev.constant.FepDevErrorCodeEnum;
import com.chinasofti.huateng.fep.dev.device.DeviceUserIdCodec;
import com.chinasofti.huateng.fep.dev.model.NotifyVerifyResultDeviceReqDTO;
import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * IF1A-01 闸机检票通知的<b>接入层转发</b>：报文规范化 → 转 ticket-server → 原样透传响应。
 *
 * <p><b>2026-09-14：出站扣费编排整体迁出到 ticket-server</b>（ADR-D62）。搬走的是
 * {@code shouldPay}（扣费决策）、{@code sendGateTxnPay}（调 gate-txn-pay-server）、
 * {@code AlipayIndustryDetailAssembler}（支付宝 21 键）、{@code GateTxnPayRequestAssembler}
 * （扣费入参）、{@code CardTypeResolver}（按 cardId 补真实卡类型）与那个专属线程池，
 * 现在都在 {@code ticket-server} 的 {@code ticket.gate} 包里。</p>
 *
 * <p><b>NEVER 把它们挪回本模块</b>，三条各自独立的理由：</p>
 * <ol>
 *   <li>扣费入参里有 12 个字段来自 ticket-server 的响应，留在这里等于把它们跨进程传出来再传回去；</li>
 *   <li>{@code CardTypeResolver} 与 ticket-server 的 {@code GateCardTypeEnricher#applyActualCardType}
 *       是同一个 account RPC 的两份调用，后者还是前者的超集，本模块那份的结果会被它无条件覆盖；</li>
 *   <li>扣费决策读的 {@code adviceOpt} 由 ticket-server 自己生产（BOM 补站链路），
 *       本模块无法区分「ticket-server 告诉我的」与「闸机上送的」，只能依赖
 *       「真实 AGM 不上送该字段」这条经验事实。</li>
 * </ol>
 *
 * <p>本类现在<b>不依赖账户域、支付域、参数域</b>，唯一出向是 ticket-server。
 * 留在这里的只有一件接入层本职：{@link DeviceUserIdCodec} 把设备上送的十六进制
 * {@code itpUserId} 归一成十进制并按渠道补位 —— 那是**设备侧编码**，MUST 在入口做完，
 * 否则下游每一个消费方都要各自认两种形态。</p>
 */
@Component
public class GateTransactionHandler {
    private static final Logger log = LoggerFactory.getLogger(GateTransactionHandler.class);

    /**
     * 闸机读写器处理成功的 {@code handleResultCode} 取值。
     *
     * <p><b>这是设备侧的码值，与本模块的 {@link FepDevErrorCodeEnum} 不是同一套命名空间</b>
     * （后者 SUCCESS 是 {@code "0000"}），因此 NEVER 把它塞进那个枚举。</p>
     */
    private static final String DEVICE_HANDLE_SUCCESS = "000";

    private final TicketClient ticketClient;
    private final DeviceUserIdCodec deviceUserIdCodec;

    public GateTransactionHandler(TicketClient ticketClient, DeviceUserIdCodec deviceUserIdCodec) {
        this.ticketClient = ticketClient;
        this.deviceUserIdCodec = deviceUserIdCodec;
    }

    /**
     * IF1A-01 闸机检票通知：规范化报文后转 ticket-server，响应原样透传。
     *
     * <p>ticket-server 返回非 {@code 0000} 时直接透传，本层不解释、不改写错误码 ——
     * 闸机侧要按具体码值决定开不开门，任何归一化都会丢掉那个语义。</p>
     *
     * <p><b>2026-09-14：`handleResultCode != "000"` 的短路判定从 {@code FepAgmController} 下沉到这里</b>
     * （§3.3「controller 禁止写业务逻辑」——「读写器失败就不通知票务」是业务决策，不是参数校验）。
     * <b>它 MUST 是本方法的第一个判断、排在 cardId 校验之前</b>：原实现在 Controller 里就是这个顺序，
     * 「读写器失败 + cardId 为空」的报文原先返 {@code SUCCESS/接收成功}，
     * 挪到 cardId 校验之后会变成 {@code INVALID_PARAM}，那是行为变更。</p>
     */
    public NotifyVerifyResultRespDTO notifyVerifyResult(NotifyVerifyResultDeviceReqDTO request) {
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();
        if (request != null && !DEVICE_HANDLE_SUCCESS.equals(request.getHandleResultCode())) {
            log.warn("IF1A-01 闸机检票通知, 读写器返回非成功状态，跳过业务处理, "
                            + "deviceId={}, handleResultCode={}, cardId={}, trxType={}, handleDateTime={}",
                    request.getDeviceId(), request.getHandleResultCode(),
                    request.getCardId(), request.getTrxType(), request.getHandleDateTime());
            response.setRetCode(FepDevErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg("接收成功");
            return response;
        }
        if (request == null || !StringUtils.hasText(request.getCardId())) {
            response.setRetCode(FepDevErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("cardId不能为空");
            return response;
        }

        NotifyVerifyResultReqDTO ticketRequest = toTicketRequest(request);

        // 用户号按渠道补位：长度由 IssueChannelCodeEnum.thirdUserIdLength 给出（ADR-D70），
        // 本层只把渠道码递下去，NEVER 在这里判长度、也 NEVER 在本层再加任何按渠道分叉的业务逻辑。
        String issueChannelCode = request.getIssueChannelCode();
        ticketRequest.setItpUserId(deviceUserIdCodec.normalize(request.getItpUserId(), issueChannelCode));

        log.info("IF1A-01 调用 ticket-server 闸机检票通知, cardId={}, trxType={}, issueChannelCode={}, alipay={}",
                request.getCardId(), request.getTrxType(), issueChannelCode,
                IssueChannelCodeEnum.isAlipay(issueChannelCode));
        NotifyVerifyResultRespDTO ticketResponse = ticketClient.notifyVerifyResult(ticketRequest);
        log.info("IF1A-01 调用 ticket-server 闸机检票通知, 返回={}", JSON.toJSONString(ticketResponse));
        if (ticketResponse == null) {
            log.warn("IF1A-01 ticket-server 无响应, cardId={}", request.getCardId());
            response.setRetCode(FepDevErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg("闸机检票通知服务异常");
            return response;
        }
        return ticketResponse;
    }

    /**
     * 设备侧入向契约 → ticket-server 对内契约的**逐字段搬运**。
     *
     * <p><b>这里 NEVER 出现任何取值判断、默认值或格式转换。</b>它存在的唯一目的是让两个契约
     * 各自能独立演进（见 {@link NotifyVerifyResultDeviceReqDTO} 的类注释）；一旦在这里加了
     * 「若空则填 X」之类的逻辑，就等于把业务规则藏进搬运代码里，下次排查「这个字段哪来的」
     * 会先怀疑设备、再怀疑 ticket-server，最后才想到中间这一层。
     * 唯一的例外是 {@code itpUserId} —— 它在调用点由 {@code DeviceUserIdCodec} 归一后覆盖，
     * 那是**设备侧编码**，本方法照原样搬、由调用点负责改写。</p>
     *
     * <p><b>加字段 MUST 同时改三处</b>：本方法、{@link NotifyVerifyResultDeviceReqDTO}、
     * {@code model.ticket.NotifyVerifyResultReqDTO}。漏搬一个字段编译不报错、下游收到 null，
     * 因此有 {@code NotifyVerifyResultDtoParityTest} 用反射盯字段名集合。</p>
     */
    private NotifyVerifyResultReqDTO toTicketRequest(NotifyVerifyResultDeviceReqDTO device) {
        NotifyVerifyResultReqDTO ticket = new NotifyVerifyResultReqDTO();
        ticket.setDeviceId(device.getDeviceId());
        ticket.setItpUserId(device.getItpUserId());
        ticket.setTrxType(device.getTrxType());
        ticket.setIssueChannelCode(device.getIssueChannelCode());
        ticket.setSignChannelCode(device.getSignChannelCode());
        ticket.setCardId(device.getCardId());
        ticket.setCardType(device.getCardType());
        ticket.setHandleDateTime(device.getHandleDateTime());
        ticket.setHandleStationCode(device.getHandleStationCode());
        ticket.setTrxAmount(device.getTrxAmount());
        ticket.setOvertimeAmount(device.getOvertimeAmount());
        ticket.setLastTicketStatus(device.getLastTicketStatus());
        ticket.setHandleResultCode(device.getHandleResultCode());
        ticket.setLastHandleStationCode(device.getLastHandleStationCode());
        ticket.setLastHandleDateTime(device.getLastHandleDateTime());
        ticket.setTicketTransSeq(device.getTicketTransSeq());
        ticket.setExcessFareType(device.getExcessFareType());
        ticket.setAdviceOpt(device.getAdviceOpt());
        ticket.setReserve1(device.getReserve1());
        ticket.setReserve2(device.getReserve2());
        ticket.setCompanionFlag(device.getCompanionFlag());
        ticket.setPaymentVendor(device.getPaymentVendor());
        ticket.setRequestSignSeq(device.getRequestSignSeq());
        ticket.setChannelType(device.getChannelType());
        return ticket;
    }
}
