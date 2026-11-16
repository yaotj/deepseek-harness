package com.chinasofti.huateng.model.app;

/**
 * HCE 卡黑名单文件生成并上传 ACC FTP 的执行结果。
 *
 * <p><b>MUST 保持普通 POJO（无参构造 + getter/setter），NEVER 改成 record</b> ——
 * 调用侧 {@code BlacklistClient} 用 Hutool {@code JSONUtil.toBean} 填充，record 拿不到值会静默返回全 0 对象，
 * 于是调度日志永远看不到真实结果（同款坑见 {@code BlacklistClient.postOutboxScan} 的注释）。
 */
public class HceBlacklistFileGenRespDTO {

    /** 结果码，0000 成功。 */
    private String resultCode;

    /** 结果描述。 */
    private String resultMsg;

    /** 本轮写入文件的 HCE 黑名单卡数量。 */
    private int cardCount;

    /** 本次文件版本号（6 位，见文件名尾段）。 */
    private Long version;

    /** 生成的文件名（PRM.0013.9700. + 6 位版本号）。 */
    private String fileName;

    /** 是否已成功上传 ACC FTP。 */
    private boolean uploaded;

    /** 附加说明（失败原因等）。 */
    private String message;

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public String getResultMsg() {
        return resultMsg;
    }

    public void setResultMsg(String resultMsg) {
        this.resultMsg = resultMsg;
    }

    public int getCardCount() {
        return cardCount;
    }

    public void setCardCount(int cardCount) {
        this.cardCount = cardCount;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean isUploaded() {
        return uploaded;
    }

    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
