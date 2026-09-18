package com.chinasofti.huateng.ticket.industry;

import com.chinasofti.huateng.model.app.RequestIndustryDataReqDTO;
import com.chinasofti.huateng.model.app.RequestNoSignalDataReqDTO;
import org.springframework.util.StringUtils;

/**
 * 行业数据生码的**内部**入参：只有三项，且已 trim（ADR-D142）。
 *
 * <p>它存在的唯一理由是**消掉成对重载**。IF8A-03 与 IF8D-03 的入向 DTO
 * （{@link RequestIndustryDataReqDTO} / {@link RequestNoSignalDataReqDTO}）字段同名、语义相同，
 * 但两者都是能被 {@code parseBizData} 解析的**对外契约**，按 `docs/domain` 那条判据
 * <b>NEVER 让它们共享父类或互相引用</b>。于是原实现里「查用户 / 查码状态 / 校验」各写了两份
 * 逐字副本（`validateRequest` 两份 15 行只差方法签名一行）。
 *
 * <p>正确的解法是在**边界上**把两个对外 DTO 各自翻译成这一个内部 record，
 * 之后整条链路只认它 —— 对外契约不动，内部逻辑只剩一份。
 * <b>NEVER 给本 record 加字段去迁就某一条链路</b>：一旦两条链路真的需要不同入参，
 * 那就是它们不该共用编排的信号。
 */
public record IndustryDataQuery(String thirdUserId, String cardId, String cardType) {

    /** 校验不通过时返回给上游的提示，通过则返回 null。 */
    public static String validate(String thirdUserId, String cardId, String cardType) {
        if (!StringUtils.hasText(thirdUserId)) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(cardId)) {
            return "cardId不能为空";
        }
        if (!StringUtils.hasText(cardType)) {
            return "cardType不能为空";
        }
        return null;
    }

    /** IF8A-03 在线码入参 → 内部入参。 */
    public static IndustryDataQuery from(RequestIndustryDataReqDTO request) {
        return new IndustryDataQuery(request.getThirdUserId().trim(),
                request.getCardId().trim(),
                request.getCardType().trim());
    }

    /** IF8D-03 离线码入参 → 内部入参。 */
    public static IndustryDataQuery from(RequestNoSignalDataReqDTO request) {
        return new IndustryDataQuery(request.getThirdUserId().trim(),
                request.getCardId().trim(),
                request.getCardType().trim());
    }
}
