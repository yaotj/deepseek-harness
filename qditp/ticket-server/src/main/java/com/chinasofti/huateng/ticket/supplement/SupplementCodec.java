package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * 补站域（IF5A-01 / IF5A-03 / IF8A-04）包内共享的字面量与编解码。
 *
 * <p><b>这不是通用工具类，NEVER 提升为 public、NEVER 搬进 {@code model} / {@code rpc} /
 * {@code resource/micro}。</b>AGENTS.md §5.1 禁止的是「自造 DateUtils / StringUtils 这类通用工具」；
 * 本类是**包级私有的补站域常量与编解码收口**，存在的唯一理由是消除
 * {@code CardDataAnalyseHandler} / {@code CardDataUpdateHandler} / {@code ExcessFareHandler}
 * 三处逐字重复的 {@code defaultString} 与 {@code encodeHexThirdUserId}（审查项 U001 / U003 / U004）。</p>
 *
 * <p><b>跨包的三份 {@code defaultString} 刻意不动</b>：{@code gate.GateTicketHandler}、
 * {@code ridestatus.TicketRideStatusServiceImpl}、{@code pay-sign-server.AppNotifyServiceImpl}。
 * 最后那份多了一个 {@code .trim()}，语义已经漂移；把三者合并成公共工具会跨包甚至跨模块，
 * 既违反上一段的约束、又要先与业务确认 {@code .trim()} 该不该保留。**NEVER 顺手合并。**</p>
 */
final class SupplementCodec {

    private static final Logger log = LoggerFactory.getLogger(SupplementCodec.class);

    /** 闸机 / BOM 报文里表示「站点未知」的哨兵值，与 {@code ticket.default-last-txn-station} 默认值一致。 */
    static final String STATION_UNKNOWN = "FFFF";

    /** {@code updateType=01} 乘客在付费区（闸机内侧）。 */
    static final String UPDATE_TYPE_PAID_AREA = "01";

    /** {@code updateType=00} 乘客在非付费区（闸机外侧）。 */
    static final String UPDATE_TYPE_FREE_AREA = "00";

    /** {@code providerId=07} 支付宝发行方，手机号在 alipay-account-server。 */
    static final String PROVIDER_ID_ALIPAY = "07";

    /** {@code providerId} 缺省回填值。 */
    static final String PROVIDER_ID_DEFAULT = "99";

    /** {@code issueChannelCode} 缺省回填值。 */
    static final String ISSUE_CHANNEL_DEFAULT = "01";

    /** 闸机报文 {@code handleResultCode} 成功值。 */
    static final String HANDLE_RESULT_SUCCESS = "000";

    /** 远端 {@code retCode} 成功值。 */
    static final String RET_SUCCESS = "0000";

    /** 金额零值，报文里是字符串而不是数值。 */
    static final String AMOUNT_ZERO = "0";

    private SupplementCodec() {
    }

    static String defaultString(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /**
     * 判断站点码是否为「未知站」。
     *
     * <p>审查项 C004：原实现同时存在硬编码 {@code "FFFF"} 与配置项
     * {@code ticket.default-last-txn-station} 两套判据，配置一改就有一半比较点漏判。
     * 现在**统一走本方法**，同时容忍两者（配置值可能被 K8s env 覆盖成别的哨兵）。
     * <b>NEVER 退回裸 {@code "FFFF".equalsIgnoreCase(x)}。</b></p>
     */
    static boolean isUnknownStation(String stationCode, String configuredUnknown) {
        if (!StringUtils.hasText(stationCode)) {
            return true;
        }
        return STATION_UNKNOWN.equalsIgnoreCase(stationCode)
                || (StringUtils.hasText(configuredUnknown) && configuredUnknown.equalsIgnoreCase(stationCode));
    }

    /**
     * 把 account-server 给的十进制 thirdUserId 归一成检票链路里的 {@code itpUserId} 形态：
     * 去掉前导零后按渠道左补零（支付宝 10 位、其余 8 位）。
     *
     * <p><b>2026-09-14（ADR-D63）：本方法取代原来的 {@code encodeHexThirdUserId}。</b>
     * 原实现把十进制编成十六进制大写串，注释写「闸机据此拒绝」——但那个报文的接收方从来不是闸机，
     * 而是 {@code fep-dev-server}，它收到后第一件事就是
     * {@code DeviceUserIdCodec.normalize} 把十六进制解回十进制再左补零。**即「编码 → 解码 → 补位」
     * 三步里前两步互相抵消，净效果只有补位**，而那次 RPC 正是 ticket-server ⇄ fep-dev
     * 双向环的其中一条边。现在补站链路直接进程内调 {@code gate.GateTicketHandler}，
     * 该编解码连同那条边一起删掉，本方法只保留唯一有净效果的补位。</p>
     *
     * <p><b>NEVER 改回十六进制。</b>补站报文现在不出进程，没有任何按 hex 解析的消费方；
     * 编成 hex 只会让 {@code QRCODE_TXN_DETAIL.THIRD_USER_ID} 落进一个与 IF1A-01 真实过闸
     * 完全不同形态的值，对账侧无法关联。</p>
     *
     * <p>非十进制数字时 <b>MUST 返回 {@code null}</b>（与原实现一致，只是理由变了）：
     * 补位对非数字串没有意义，硬塞进去会让明细表里出现一个既不是用户号、又无法与账户域关联的脏值；
     * 返回 null 时该字段留空，属可见缺失。</p>
     *
     * <p><b>2026-09-14（ADR-D70）：第二个参数由 {@code boolean alipayTransaction} 换成
     * {@code issueChannelCode}，长度改由 {@link IssueChannelCodeEnum#thirdUserIdLength} 给出。</b>
     * 本方法与 {@code fep-dev-server} 的 {@code DeviceUserIdCodec.normalize} 产出同一个字段，
     * 长度此前在两个模块各写一份 10 / 8，现已收口到 {@code model}。
     * <b>NEVER 把长度常量写回本类。</b></p>
     */
    static String normalizeDeviceThirdUserId(String decimalThirdUserId, String issueChannelCode) {
        if (!StringUtils.hasText(decimalThirdUserId)) {
            return decimalThirdUserId;
        }
        String decimal;
        try {
            decimal = Long.toString(Long.parseLong(decimalThirdUserId.trim()));
        } catch (NumberFormatException e) {
            log.error("thirdUserId 非十进制数字，无法按渠道补位，本次置空: {}", decimalThirdUserId);
            return null;
        }
        return leftPad(decimal, IssueChannelCodeEnum.thirdUserIdLength(issueChannelCode));
    }

    /**
     * 左侧补零至指定长度，已满足长度时原值返回。
     *
     * <p>长度由调用方从 {@link IssueChannelCodeEnum#thirdUserIdLength} 取得（ADR-D70），
     * 本方法不认识渠道、也不认识 10 / 8。此前这里的注释写着「长度口径与 fep-dev-server 的
     * {@code DeviceUserIdCodec.leftPad} 逐字一致，MUST 同步改」——那条**靠人记的同步约定已作废**，
     * 现在两边问同一个方法，改一处即全改。</p>
     */
    private static String leftPad(String value, int length) {
        if (value.length() >= length) {
            return value;
        }
        return "0".repeat(length - value.length()) + value;
    }
}
