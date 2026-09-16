package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import org.springframework.stereotype.Component;

/**
 * 闸机检票报文（{@code NotifyVerifyResultReqDTO}）的公共骨架组装。
 *
 * <p>审查项 U004：IF5A-03（{@code CardDataUpdateHandler}）与 IF8A-04（{@code ExcessFareHandler}）
 * 各有一段 19 行的 setter 序列，其中 12 个字段取值完全相同，改一处漏另一处过去只能靠人记。
 * 现在共同字段收在 {@link #newBaseRequest} 里，两条链路各自只补自己特有的那几个。
 * <b>NEVER 再把公共字段拷回处理器。</b></p>
 *
 * <p>两条链路真正不同的地方（MUST 保持差异，NEVER 强行统一）：</p>
 * <ul>
 *   <li>{@code deviceId}：IF5A-03 是 BOM 操作员号（{@code operaterId}），
 *       IF8A-04 是 {@code 车站码 + "36" + "01"} 的虚拟设备号；</li>
 *   <li>{@code trxType}：IF5A-03 由 {@code adviceOpt} 推导，IF8A-04 直接用 {@code upgradeAreaType}；</li>
 *   <li>{@code reserve1} / {@code adviceOpt}：只有 IF5A-03 借 {@code RESERVE1} 落 {@code adviceOpt}
 *       以便对账侧区分「BOM 补站」与「真实检票」（{@code QRCODE_TXN_DETAIL} 无 {@code ADVICE_OPT} 列）；</li>
 *   <li>{@code excessFareType}：只有 IF8A-04 上送。</li>
 * </ul>
 *
 * <p><b>{@code overtimeAmount} 恒为 {@code "0"}</b>：ITP 侧不算超时费，超时费只由闸机 / AGM
 * 通过 {@code trxType=03} 上送（见 {@code GateTicketHandler}）。</p>
 */
@Component
class SupplementGateRequestAssembler {

    /**
     * 组装两条补站链路共有的报文字段。
     *
     * <p>{@code itpUserId} 走 {@link SupplementCodec#normalizeDeviceThirdUserId}，
     * 补位长度由 {@code IssueChannelCodeEnum.thirdUserIdLength} 按 {@code issueChannelCode} 给出
     * （ADR-D70 起长度收口在 {@code model}，本类与 fep-dev 侧问同一个方法），
     * <b>转换失败时该字段为 null（审查项 C006）</b>，明细里留空、属可见缺失。
     * 2026-09-14（ADR-D63）此处原本是「编十六进制」，见该方法注释。</p>
     */
    NotifyVerifyResultReqDTO newBaseRequest(QRCodeStatus currentStatus, String cardId, String decimalItpUserId,
                                            String trxType, String handleDateTime, String handleStationCode,
                                            String cardType, String signChannelCode) {
        NotifyVerifyResultReqDTO request = new NotifyVerifyResultReqDTO();
        String issueChannelCode = SupplementCodec.defaultString(
                currentStatus.getChannel(), SupplementCodec.ISSUE_CHANNEL_DEFAULT);
        request.setItpUserId(SupplementCodec.normalizeDeviceThirdUserId(
                decimalItpUserId, issueChannelCode));
        request.setTrxType(trxType);
        request.setIssueChannelCode(issueChannelCode);
        request.setSignChannelCode(signChannelCode);
        request.setCardId(cardId);
        request.setCardType(cardType);
        request.setHandleDateTime(handleDateTime);
        request.setHandleStationCode(handleStationCode);
        request.setOvertimeAmount(SupplementCodec.AMOUNT_ZERO);
        request.setLastTicketStatus(SupplementCodec.defaultString(
                currentStatus.getCodeStatus(), QRCodeStatusEnum.SJT_ISSUE.getCode()));
        request.setHandleResultCode(SupplementCodec.HANDLE_RESULT_SUCCESS);
        request.setLastHandleStationCode(currentStatus.getLastTxnStation());
        request.setLastHandleDateTime(currentStatus.getLastTxnTime());
        request.setTicketTransSeq(currentStatus.getTxnSeq() == null
                ? SupplementCodec.AMOUNT_ZERO : currentStatus.getTxnSeq());
        request.setTrxAmount(SupplementCodec.AMOUNT_ZERO);
        request.setReserve1(null);
        request.setReserve2(null);
        return request;
    }
}
