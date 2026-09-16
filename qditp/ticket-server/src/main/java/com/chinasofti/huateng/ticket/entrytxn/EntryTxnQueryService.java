package com.chinasofti.huateng.ticket.entrytxn;

import com.chinasofti.huateng.model.ticket.QueryFirstEntryTxnReqDTO;
import com.chinasofti.huateng.model.ticket.QueryFirstEntryTxnResult;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.mapper.QRCodeTxnDetailMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 进站交易只读查询（源表 {@code QRCODE_TXN_DETAIL}，只查 {@code TRX_TYPE='01'}）。
 *
 * <p>两个方法都是**给模块外算账用的辅助查询**，与乘车码状态机无关，因此不放 {@code ridestatus} 包：
 * <ul>
 *   <li>{@code queryEntryDevice} — 调用方 {@code fep-dev-server} 的 {@code GateTransactionHandler}，
 *       补 IF1A-01 上送缺失的进站设备码</li>
 *   <li>{@code queryFirstEntryTxn} — 调用方 {@code gate-txn-pay-server} 的 {@code FareCalculator}，
 *       离线码出站时按同序列号首笔进站重算票价</li>
 * </ul>
 *
 * <p><b>2026-09-14 从 {@code query} 包迁出到本包（ADR-D60）。</b>动因是耦合方向：本类是
 * {@code @Service}、被 {@code controller/ci/app/TicketRideStatusController} 直接注入，而
 * {@code query} 包对外的唯一门面是 {@code TicketTransService}（IF8A-05/34/41 APP 账单）——
 * 本类既不属于那三个入口、也不该经由那个门面暴露，留在 {@code query} 里就成了「包外绕过门面
 * 抓另一个包的类」。迁出后它是**本包自己的门面**，包内不再有其它类，依赖方向自洽。
 * <b>NEVER 把这两个方法并进 {@code TicketTransService}</b>：那个门面的语义是「APP 账单查询」，
 * 混入闸机辅助查询会让「谁该依赖它」重新变得说不清。
 *
 * <p>对外 URL 仍是 {@code /ci/app/queryEntryDevice} 与 {@code /ci/app/queryFirstEntryTxn}
 * （见 {@code rpc/TicketClient}），历次搬迁**只换实现位置、NEVER 动路径**。
 */
@Service
public class EntryTxnQueryService {

    private static final Logger log = LoggerFactory.getLogger(EntryTxnQueryService.class);

    private final QRCodeTxnDetailMapper qrCodeTxnDetailMapper;

    public EntryTxnQueryService(QRCodeTxnDetailMapper qrCodeTxnDetailMapper) {
        this.qrCodeTxnDetailMapper = qrCodeTxnDetailMapper;
    }

    /**
     * 查询最近一次进站设备编号。
     *
     * <p>查不到或异常时返回 {@code null}：调用方只是拿它补一个可选字段，
     * 让本方法抛异常会把整条过闸链路带崩。
     *
     * <p><b>已知取舍（2026-09-14 审查记录）</b>：返回值是裸 {@code String}，
     * 「无进站记录」与「查库失败」**同形为 {@code null}**，上游
     * {@code fep-dev-server} 无法区分，库故障期间只能靠本方法的 ERROR 日志发现。
     * 要区分二者需把返回类型改成带 {@code retCode} 的结果对象，那会同时改动
     * {@code rpc/TicketClient} 与 {@code fep-dev-server}，属跨模块契约变更，
     * **MUST 先与调用方约定后再改**，NEVER 只改本侧。
     */
    public String queryEntryDevice(String cardId) {
        if (!StringUtils.hasText(cardId)) {
            return null;
        }
        try {
            QRCodeTxnDetail entryDetail = qrCodeTxnDetailMapper.selectLatestEntryByCardId(cardId);
            return entryDetail != null ? entryDetail.getDeviceId() : null;
        } catch (Exception e) {
            log.error("查询进站设备异常, cardId={}", cardId, e);
            return null;
        }
    }

    /**
     * 查询同序列号的首笔进站交易。
     *
     * <p>离线码出站重算票价依赖本结果，查不到时 MUST 明确返 {@code 8004} 让上游走异常分支，
     * NEVER 返回空对象冒充成功。
     */
    public QueryFirstEntryTxnResult queryFirstEntryTxn(QueryFirstEntryTxnReqDTO request) {
        QueryFirstEntryTxnResult result = new QueryFirstEntryTxnResult();
        if (request == null || !StringUtils.hasText(request.getCardId())
                || !StringUtils.hasText(request.getTicketTransSeq())) {
            result.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("cardId和ticketTransSeq不能为空");
            return result;
        }
        QRCodeTxnDetail detail = qrCodeTxnDetailMapper.selectFirstEntryBySequence(
                request.getCardId().trim(), request.getTicketTransSeq().trim());
        if (detail == null) {
            result.setRetCode(TicketErrorCodeEnum.ENTRY_TXN_NOT_FOUND.getCode());
            result.setRetMsg(TicketErrorCodeEnum.ENTRY_TXN_NOT_FOUND.getMsg());
            return result;
        }
        result.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
        result.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
        result.setCardId(detail.getCardId());
        result.setTicketTransSeq(detail.getTicketTransSeq());
        result.setHandleDateTime(detail.getHandleDateTime());
        result.setHandleStationCode(detail.getHandleStationCode());
        return result;
    }
}
