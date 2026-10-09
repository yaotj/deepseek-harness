package com.chinasofti.huateng.model.app;

/**
 * 用户主动发起免密失败订单重试扣费请求（APP 接口 requestPayFailOrder）。
 *
 * <p>请求体由 fep-app 的 {@code ItpCommonFormRequest.bizData} 解析而来，本 DTO 只声明业务字段，
 * 不含任何 Bean Validation（本项目对外 DTO 统一零校验，必填靠业务侧显式判空）。
 */
public class RequestPayFailOrderReqDTO {

    /** 第三方用户 ID。 */
    private String thirdUserId;

    /** 逻辑卡号，多个卡号用逗号拼接；为空表示不限卡号、只按 thirdUserId 范围重试。 */
    private String cardNums;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getCardNums() {
        return cardNums;
    }

    public void setCardNums(String cardNums) {
        this.cardNums = cardNums;
    }

    @Override
    public String toString() {
        return "RequestPayFailOrderReqDTO{thirdUserId='" + thirdUserId + "', cardNums='" + cardNums + "'}";
    }
}
