package com.chinasofti.huateng.facepay.api.device;

/**
 * 出票结果上报里的单张票信息，TVM 与 BOM 两侧的 {@code ticketList} 元素结构相同。
 *
 * <p>三个字段合起来是 {@code F2F_TICKET} 的业务主键与金额来源：
 * {@code (ticketLogicNum, transDate)} 正是 {@code UK_F2F_TICKET_LOGIC} 的两列。</p>
 *
 * <p>金额单位分，设备传字符串，落库时转 {@code Long}。<b>转换失败不抛异常</b>——
 * 出票结果是既成事实，不能因为一个金额格式问题把整批票丢掉，
 * 由 {@link #priceInFen()} 返回 null 并留在 {@code F2F_RESULT_REPORT.RAW_BODY} 里备查。</p>
 */
public class TicketInfo {

    /** 票逻辑卡号。 */
    private String ticketLogicNum;

    /** 交易日期，设备侧格式为 yyyyMMddHHmmss 或 yyyyMMdd，原样落库不做归一。 */
    private String transDate;

    /** 单票金额，单位分。 */
    private String transAmount;

    /**
     * 单票金额转 {@code Long}（分）。
     *
     * @return 金额；为空或非数字时返回 null，调用方 MUST 容忍 null 而不是抛异常
     */
    public Long priceInFen() {
        if (transAmount == null || transAmount.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(transAmount.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getTicketLogicNum() {
        return ticketLogicNum;
    }

    public void setTicketLogicNum(String ticketLogicNum) {
        this.ticketLogicNum = ticketLogicNum;
    }

    public String getTransDate() {
        return transDate;
    }

    public void setTransDate(String transDate) {
        this.transDate = transDate;
    }

    public String getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(String transAmount) {
        this.transAmount = transAmount;
    }

    @Override
    public String toString() {
        return "TicketInfo{ticketLogicNum=" + ticketLogicNum
                + ", transDate=" + transDate
                + ", transAmount=" + transAmount + '}';
    }
}
