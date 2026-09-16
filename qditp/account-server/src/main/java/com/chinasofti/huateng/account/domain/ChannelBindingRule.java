package com.chinasofti.huateng.account.domain;

import org.springframework.util.StringUtils;

/**
 * 支付通道绑定的入参不变量，<b>纯函数、无状态、不依赖任何 Bean</b>（第二个 domain 类，形态照
 * {@link ArchiveDecision}）。
 *
 * <p>2026-09-11 从 {@code PayChannelServiceImpl} 的四个 {@code validateXxx} 抽出，<b>逐条照搬、
 * 返回文案一字未改</b>。抽出的动机不是去重（ADR-D36 已经把三份重复收口过一次），而是让这组
 * 不变量能<b>脱离 Spring 上下文与 mapper 被直接断言</b>：原先要验证「钱包渠道必须带 thirdPayId」
 * 就得端到端打一次 IF8A-23。</p>
 *
 * <p>三条 NEVER：</p>
 * <ul>
 *   <li><b>返回的字符串就是 APP 侧看到的 8001 retMsg，NEVER 改动措辞</b>——它是对外可见行为，
 *       改一个字就是改契约。返回 {@code null} 表示校验通过。</li>
 *   <li><b>NEVER 把 IF8A-77 的字段集合并进来</b>：它校验的是 {@code cardIssueCode} /
 *       {@code regSignSeq}，与本类的 cardId / cardType 不是同一组，硬凑会得到一个带开关的四不像
 *       （原因见 {@code PayChannelServiceImpl.validateUpdateChannelDefaultContractRequest}）。</li>
 *   <li><b>NEVER 在本类里查库或调 RPC</b>。「钱包 cardId 与开户信息是否匹配」需要读
 *       {@code USER_ITP_REG_INFO}，那是服务层的事，本类只管「请求自身是否自洽」。</li>
 * </ul>
 */
public final class ChannelBindingRule {

    /**
     * 钱包支付渠道编码，{@code APP_USER_PAY_CHANNEL.CHANNEL} 的取值之一。
     *
     * <p>本常量是账户域内该编码的<b>唯一定义点</b>（原先是 {@code PayChannelServiceImpl} 的私有常量）。
     * <b>NEVER 在别处再写一份 {@code "0B"} 字面量</b>。</p>
     *
     * <p>与 {@code pay-sign-server} 的 {@code PaySignWorkflow.WALLET_PAYMENT_VENDOR} <b>不是同一个东西</b>：
     * 那个是支付厂商（vendor），这个是通道（channel），两者恰好同值属巧合，<b>NEVER 因此把它们收口成一个</b>。</p>
     */
    public static final String WALLET_PAYMENT_CHANNEL = "0B";

    /** 四个入口共用的空报文文案。 */
    public static final String REQUEST_BODY_REQUIRED = "请求报文不能为空";

    private ChannelBindingRule() {
    }

    /**
     * 是否钱包渠道。<b>入参可为 null</b>（返回 false），内部自行 trim。
     *
     * <p>原调用点有两种写法：已 trim 过的变量直接 {@code equals}，未 trim 的先 {@code .trim()}。
     * 这里统一成「内部 trim」，对已 trim 的串是空操作，因此<b>行为完全一致</b>。</p>
     */
    public static boolean isWallet(String channel) {
        return channel != null && WALLET_PAYMENT_CHANNEL.equals(channel.trim());
    }

    /**
     * IF8A-23 / 24 / 75 共用的「通道四要素」非空校验。
     *
     * <p>顺序 thirdUserId → cardId → cardType → channel <b>是有意固定的</b>：多个字段同时为空时，
     * APP 只会看到第一条文案，改顺序等于改对外行为。</p>
     *
     * @return 不通过时返回 retMsg 原文，通过返回 {@code null}
     */
    public static String validateBindingFields(String thirdUserId, String cardId, String cardType, String channel) {
        if (!StringUtils.hasText(thirdUserId)) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(cardId)) {
            return "cardId不能为空";
        }
        if (!StringUtils.hasText(cardType)) {
            return "cardType不能为空";
        }
        if (!StringUtils.hasText(channel)) {
            return "channel不能为空";
        }
        return null;
    }

    /**
     * IF8A-23 添加通道：四要素 + 钱包分支。
     *
     * <p><b>钱包分支 MUST 排在四要素之后</b>：它要读 {@code channel} 判断是不是钱包，
     * 四要素没过时 {@code channel} 可能为空，先判钱包会把「channel不能为空」这条文案吃掉。</p>
     *
     * @return 不通过时返回 retMsg 原文，通过返回 {@code null}
     */
    public static String validateAddBinding(String thirdUserId, String cardId, String cardType,
                                            String channel, String thirdPayId) {
        String msg = validateBindingFields(thirdUserId, cardId, cardType, channel);
        if (msg != null) {
            return msg;
        }
        if (isWallet(channel) && !StringUtils.hasText(thirdPayId)) {
            return "钱包支付渠道 thirdPayId 不能为空";
        }
        return null;
    }
}
