package com.chinasofti.huateng.model.app;

/**
 * 删除黑名单请求参数。
 *
 * <p>本 DTO 只走内部 RPC 与运营页，不被 parseBizData 解析，因此可以加字段；
 * 加字段后 MUST 重建链路上每一个经手它的模块镜像 —— model 版本号恒为 2.0.0，
 * 旧镜像里是旧 class，Fastjson2 会静默丢弃它不认识的字段。</p>
 */
public class DeleteBlackListReqDTO {
    /**
     * 卡ID，支持多个，逗号分隔。
     */
    private String cardId;

    /**
     * 解除原因，落 BLACKLIST_RELEASED.RELEASE_REASON 与操作日志的 REASON，可空。
     */
    private String releaseReason;

    /**
     * 解除操作者：运维手工记管理员账号，系统触发记服务名，可空。
     */
    private String releaseBy;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getReleaseReason() {
        return releaseReason;
    }

    public void setReleaseReason(String releaseReason) {
        this.releaseReason = releaseReason;
    }

    public String getReleaseBy() {
        return releaseBy;
    }

    public void setReleaseBy(String releaseBy) {
        this.releaseBy = releaseBy;
    }

    @Override
    public String toString() {
        return "DeleteBlackListReqDTO{cardId='" + cardId + "', releaseReason='" + releaseReason
                + "', releaseBy='" + releaseBy + "'}";
    }
}
