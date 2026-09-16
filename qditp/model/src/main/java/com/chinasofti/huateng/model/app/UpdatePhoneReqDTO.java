package com.chinasofti.huateng.model.app;

/**
 * 更换手机号请求DTO（if8a_76）。
 *
 * <p>字段名以甲方规范为准：《青岛地铁-ITP与APP接口规范R6》if8a_76 表117 定义为
 * {@code newPhone} + {@code thirdUserId}。<b>NEVER</b> 改回 {@code newMsisdn} 作为规范字段——
 * 那是本项目早期自造的命名，2026-09-09 与规范核对后已纠正；旧命名仅在
 * {@code PhoneChangeController} 的入向别名列表里兜底，不属于对外契约。</p>
 */
public class UpdatePhoneReqDTO {

    /** ITP 侧第三方用户标识，用来定位 {@code USER_ITP_REG_INFO} 与该用户名下的员工码。 */
    private String thirdUserId;

    /**
     * 变更后的手机号。<b>字段名本身是对外契约</b>：改名 MUST 连带重建 {@code fep-app-server}
     * 镜像并与 APP 同步切换，否则 Fastjson2 会静默丢弃该键、下游只收到 {@code null}。
     */
    private String newPhone;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getNewPhone() {
        return newPhone;
    }

    public void setNewPhone(String newPhone) {
        this.newPhone = newPhone;
    }
}
