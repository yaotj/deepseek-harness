package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.enums.TrxTypeCodeEnum;
import com.chinasofti.huateng.model.ticket.enums.AdviceOptEnum;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** IF1A-01 目标状态码解析器 —— 从 {@code trxType} / {@code excessFareType} / {@code adviceOpt} 三个入向字段推导 {@code QRCODE_STATUS.CODE_STATUS} 的下一个取值。 */
@Component
class GateCodeStatusResolver {

    private static final Logger log = LoggerFactory.getLogger(GateCodeStatusResolver.class);

    /** IF5A-03 建议操作 → 目标状态。 */
    private static final Map<String, QRCodeStatusEnum> ADVICE_OPT_TABLE = Map.of(
            AdviceOptEnum.SUPPLEMENT_ENTRY.getCode(), QRCodeStatusEnum.ENTRY,
            AdviceOptEnum.FREE_UPDATE_020.getCode(), QRCodeStatusEnum.UPDATE_FREE,
            AdviceOptEnum.FREE_UPDATE.getCode(), QRCodeStatusEnum.UPDATE_FREE,
            AdviceOptEnum.PAID_UPDATE.getCode(), QRCodeStatusEnum.UPDATE_PAY);

    /** IF8A-04 APP 自助补站类型 → 目标状态。 */
    private static final Map<String, QRCodeStatusEnum> EXCESS_FARE_TYPE_TABLE = Map.of(
            TrxTypeCodeEnum.ENTRY.getCode(), QRCodeStatusEnum.SELF_SERVICE_ENTRY,
            TrxTypeCodeEnum.EXIT.getCode(), QRCodeStatusEnum.SELF_SERVICE_EXIT);

    /** 闸机交易类型 → 目标状态。 */
    private static final Map<String, QRCodeStatusEnum> TRX_TYPE_TABLE = Map.of(
            TrxTypeCodeEnum.ENTRY.getCode(), QRCodeStatusEnum.ENTRY,
            TrxTypeCodeEnum.EXIT.getCode(), QRCodeStatusEnum.EXIT,
            TrxTypeCodeEnum.EXIT_OVERTIME.getCode(), QRCodeStatusEnum.EXIT_OVERTIME,
            TrxTypeCodeEnum.ENTRY_FAIL.getCode(), QRCodeStatusEnum.ENTRY_FAIL,
            TrxTypeCodeEnum.ABNORMAL.getCode(), QRCodeStatusEnum.ABNORMAL);

    /**
     * 一级解析：从入向报文取一个字段、查一张表。
     *
     * @param fieldName 日志用的字段名
     * @param reader 字段读取器
     * @param table 该字段的映射表
     * @param logUnmappedValue 字段有值但表里没有时是否记一条 INFO。
     */
    private record ResolveLevel(String fieldName,
                                Function<GateTxnCodeStatusInput, String> reader,
                                Map<String, QRCodeStatusEnum> table,
                                boolean logUnmappedValue) {
    }

    /** 入向三字段的载体，只为让 {@link ResolveLevel#reader} 能按名取值，不参与业务。 */
    private record GateTxnCodeStatusInput(String trxType, String excessFareType, String adviceOpt) {
    }

    /** 列表顺序即优先级， */
    private static final List<ResolveLevel> RESOLVE_LEVELS = List.of(
            new ResolveLevel("adviceOpt", GateTxnCodeStatusInput::adviceOpt, ADVICE_OPT_TABLE, false),
            new ResolveLevel("excessFareType", GateTxnCodeStatusInput::excessFareType, EXCESS_FARE_TYPE_TABLE, true),
            new ResolveLevel("trxType", GateTxnCodeStatusInput::trxType, TRX_TYPE_TABLE, false));

    /** 状态码解析兜底值。 */
    @Value("${ticket.default-code-status:03}")
    private String defaultCodeStatus;

    /** 根据交易类型解析下一状态码。 */
    public String resolveCodeStatus(String trxType, String excessFareType, String adviceOpt) {
        log.debug("IF1A-01 解析状态码, trxType={}, excessFareType={}, adviceOpt={}",
                trxType, excessFareType, adviceOpt);

        GateTxnCodeStatusInput input = new GateTxnCodeStatusInput(trxType, excessFareType, adviceOpt);
        for (ResolveLevel level : RESOLVE_LEVELS) {
            String value = level.reader().apply(input);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            QRCodeStatusEnum status = level.table().get(value);
            if (status != null) {
                log.debug("IF1A-01 状态码解析结果={}({}), {}={}",
                        status.getCode(), status.getDesc(), level.fieldName(), value);
                return status.getCode();
            }
            if (level.logUnmappedValue()) {
                log.info("IF1A-01 自助补站类型未映射专属状态，回落 trxType 分支, {}={}", level.fieldName(), value);
            }
        }

        String fallback = QRCodeStatusEnum.fromCode(defaultCodeStatus).getCode();
        log.warn("IF1A-01 状态码解析未匹配任何规则, trxType={}, 使用默认值={}", trxType, fallback);
        return fallback;
    }
}
