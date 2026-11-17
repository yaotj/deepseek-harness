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

@Component
class GateCodeStatusResolver {

    private static final Logger log = LoggerFactory.getLogger(GateCodeStatusResolver.class);

    private static final Map<String, QRCodeStatusEnum> ADVICE_OPT_TABLE = Map.of(
            AdviceOptEnum.SUPPLEMENT_ENTRY.getCode(), QRCodeStatusEnum.ENTRY,
            AdviceOptEnum.FREE_UPDATE_020.getCode(), QRCodeStatusEnum.UPDATE_FREE,
            AdviceOptEnum.FREE_UPDATE.getCode(), QRCodeStatusEnum.UPDATE_FREE,
            AdviceOptEnum.PAID_UPDATE.getCode(), QRCodeStatusEnum.UPDATE_PAY);

    private static final Map<String, QRCodeStatusEnum> EXCESS_FARE_TYPE_TABLE = Map.of(
            TrxTypeCodeEnum.ENTRY.getCode(), QRCodeStatusEnum.SELF_SERVICE_ENTRY,
            TrxTypeCodeEnum.EXIT.getCode(), QRCodeStatusEnum.SELF_SERVICE_EXIT);

    private static final Map<String, QRCodeStatusEnum> TRX_TYPE_TABLE = Map.of(
            TrxTypeCodeEnum.ENTRY.getCode(), QRCodeStatusEnum.ENTRY,
            TrxTypeCodeEnum.EXIT.getCode(), QRCodeStatusEnum.EXIT,
            TrxTypeCodeEnum.EXIT_OVERTIME.getCode(), QRCodeStatusEnum.EXIT_OVERTIME,
            TrxTypeCodeEnum.ENTRY_FAIL.getCode(), QRCodeStatusEnum.ENTRY_FAIL,
            TrxTypeCodeEnum.ABNORMAL.getCode(), QRCodeStatusEnum.ABNORMAL);


    private record ResolveLevel(String fieldName,
                                Function<GateTxnCodeStatusInput, String> reader,
                                Map<String, QRCodeStatusEnum> table,
                                boolean logUnmappedValue) {
    }

    private record GateTxnCodeStatusInput(String trxType, String excessFareType, String adviceOpt) {
    }

    private static final List<ResolveLevel> RESOLVE_LEVELS = List.of(
            new ResolveLevel("adviceOpt", GateTxnCodeStatusInput::adviceOpt, ADVICE_OPT_TABLE, false),
            new ResolveLevel("excessFareType", GateTxnCodeStatusInput::excessFareType, EXCESS_FARE_TYPE_TABLE, true),
            new ResolveLevel("trxType", GateTxnCodeStatusInput::trxType, TRX_TYPE_TABLE, false));

    @Value("${ticket.default-code-status:03}")
    private String defaultCodeStatus;

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
