package com.chinasofti.huateng.facepay.api.paycenter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 支付中心回调的公共报文（信封），六个字段对齐网关文档「公共请求参数」
 * （{@code docs/external/支付中心网关接口文档.md} 第 34~41 行）。
 *
 * <p><b>{@code bizData} 是 Base64(业务 JSON, UTF-8)，不是裸 JSON。</b>依据两条：
 * 文档第 40 行原文「业务数据（base64后的JSON字符串）」；我方出向报文也是这个形态，
 * 见 {@code PayCenterMessageFactory.envelope} 里的 {@code Base64.getEncoder()}。
 * 同一个网关、同一套信封，入向没有理由不同。取业务报文 MUST 走 {@link #bizDataJson()}，
 * <b>NEVER 把 {@link #getBizData()} 直接丢给 {@code JSON.parseObject}</b>——重写初版就是这么写的，
 * 真实回调必然解析失败（2026-09-11 定位）。</p>
 *
 * <p>⚠️ <b>安全提示：本回调带 {@code sign}，但旧实现从不验签，本次重写保持同契约、同样不验签。</b>
 * 这意味着任何网络可达方都能构造一条 {@code status=SUCCESS} 的回调把订单改成已支付。
 * 加验签属契约变更 + 支付安全红线（AGENTS.md §5.2），MUST 人工评审后单独实施，
 * NEVER 在重写里顺手加。已在交付说明里单独列为风险项。</p>
 */
public class PayCenterCallbackRequest {

    private String merchantNo;

    private String apiVersion;

    private String signType;

    private String sign;

    private String charset;

    private String bizData;

    public String getMerchantNo() {
        return merchantNo;
    }

    public void setMerchantNo(String merchantNo) {
        this.merchantNo = merchantNo;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getBizData() {
        return bizData;
    }

    public void setBizData(String bizData) {
        this.bizData = bizData;
    }

    /**
     * 取业务报文的<b>明文 JSON</b>：先按 Base64 解，解不出再按裸 JSON 兜。
     *
     * <p>为什么要兜裸 JSON：①联调与回放时人手构造的报文多是裸 JSON；②旧实现里
     * {@code CollectPayServiceImpl} / {@code TvmTopupServiceImpl} 那两套死代码发的就是裸 JSON，
     * 万一网关侧对某个场景仍按老形态推，这里不至于整条回调丢掉。
     * <b>判据是「解出来以 &#123; 开头」而不是「Base64 解码没抛异常」</b>——裸 JSON 串里
     * 只含 Base64 字母表字符时也能被解码成乱码而不报错，只看异常会误判。</p>
     *
     * @return 明文 JSON；{@code bizData} 为空时返回 null
     */
    public String bizDataJson() {
        if (bizData == null || bizData.isBlank()) {
            return null;
        }
        String raw = bizData.trim();
        if (raw.startsWith("{")) {
            return raw;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8).trim();
            if (decoded.startsWith("{")) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // 不是合法 Base64，落到下面按原串返回，由调用方的 JSON 解析报错
        }
        return raw;
    }

    @Override
    public String toString() {
        return "PayCenterCallbackRequest{merchantNo=" + merchantNo
                + ", apiVersion=" + apiVersion
                + ", signType=" + signType
                + ", charset=" + charset
                + ", bizData=" + bizData
                + '}';
    }
}
