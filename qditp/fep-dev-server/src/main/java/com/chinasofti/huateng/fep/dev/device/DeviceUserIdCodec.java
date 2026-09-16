package com.chinasofti.huateng.fep.dev.device;

import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigInteger;

/**
 * 设备上送 {@code itpUserId} 的编码转换：十六进制转十进制并按渠道补齐零位。
 *
 * <p>纯函数、无任何外部依赖，从 {@code GateTransactionHandler} 原样搬出（2026-09-14 重构，
 * 逻辑逐行未改）。拆出来的理由：进制与补位规则的变化原因是「渠道对用户号长度的要求」，
 * 与出站编排、支付宝报文组装、扣费入参组装三者都不同源。</p>
 *
 * <p>同批把 {@code QrCodeStatusHandler} 里那份**逐字相同的私有副本**（`normalizeThirdUserId`
 * + `leftPadToEight`，只是没有支付宝补 10 位的分支）也收口到本类，IF1A-04 传
 * {@code alipayTransaction=false} 即与原行为等价。<b>NEVER 再在本模块任何类里复制这段逻辑</b>
 * ——两份副本一旦漂移，同一个 itpUserId 在 IF1A-01 与 IF1A-04 会算出不同的 thirdUserId，
 * 而两条链路查的是同一个用户，表现为「过闸能扣费、查状态查不到」。</p>
 *
 * <p>2026-09-14 从 {@code service.impl} 平移到 {@code device} 包：本类被 {@code gate}（IF1A-01）
 * 与 {@code qrcode}（IF1A-04）两条链路共用，**放进任一条的包里都会误导归属**。
 * 整个模块已无一个 interface，{@code impl} 后缀指向不存在的抽象，故该包整体撤销。</p>
 *
 * <p>2026-09-14（ADR-D70）**长度不再由本类决定**，改问 {@link IssueChannelCodeEnum#thirdUserIdLength}。
 * 本类现在只负责「十六进制 → 十进制」这一步 —— 那是**设备侧编码**，只有 AGM 报文有，
 * 因此它留在接入层是对的；而「哪个渠道要几位」属渠道语义，与
 * {@code IssueChannelCodeEnum.isAlipay} 同源，两半 MUST 待在一起。</p>
 *
 * <p>日志刻意不带接口编号（原两份副本分别写 IF1A-01 / IF1A-04）：本类被两条链路共用，
 * 写死任一编号都会误导排查方向；调用链靠 traceId 关联即可。</p>
 */
@Component
public class DeviceUserIdCodec {
    private static final Logger log = LoggerFactory.getLogger(DeviceUserIdCodec.class);

    /**
     * 将十六进制 itpUserId 转换为十进制字符串，并补齐到该渠道要求的位数。
     * 转换失败时原值返回并记录 warn 日志。
     *
     * <p>2026-09-14（ADR-D70）第二个参数由 {@code boolean alipayTransaction} 换成
     * {@code issueChannelCode}，长度改由 {@link IssueChannelCodeEnum#thirdUserIdLength} 给出 ——
     * 本类不再自己写 10 / 8。<b>NEVER 把长度常量搬回来</b>：ticket-server 的
     * {@code SupplementCodec.normalizeDeviceThirdUserId} 与本方法产出同一个字段
     * （{@code NotifyVerifyResultReqDTO.itpUserId} → {@code QRCODE_TXN_DETAIL.ITP_USER_ID}），
     * 长度分叉会让同一个用户在同一张表里出现两种位数。</p>
     *
     * <p>{@code issueChannelCode} 传 null 即按默认 8 位 —— IF1A-04 就是这么用的
     * （该链路报文里没有渠道码，改造前传的是 {@code alipayTransaction=false}，行为逐位一致）。</p>
     */
    public String normalize(String itpUserId, String issueChannelCode) {
        if (!StringUtils.hasText(itpUserId)) {
            return itpUserId;
        }
        String decimal;
        try {
            decimal = new BigInteger(itpUserId.trim(), 16).toString(10);
        } catch (Exception e) {
            log.warn("itpUserId十六进制转十进制失败, itpUserId={}", itpUserId);
            return itpUserId;
        }
        return leftPad(decimal, IssueChannelCodeEnum.thirdUserIdLength(issueChannelCode));
    }

    /**
     * 左侧补零至指定长度，已满足长度或为空时原值返回。
     *
     * <p>原实现是 {@code leftPadToEight} / {@code leftPadToTen} 两个方法，
     * 除长度外完全同形，合并为一个带长度参数的方法；两个调用点的实际长度不变。</p>
     */
    private String leftPad(String value, int length) {
        if (!StringUtils.hasText(value) || value.length() >= length) {
            return value;
        }
        return "0".repeat(length - value.length()) + value;
    }
}
