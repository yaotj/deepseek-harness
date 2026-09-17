package com.chinasofti.huateng.model.accsecure;

/**
 * IF7B-01 请求逻辑卡号（ACC 安全服务）响应报文。
 */
public class RequestQrLogicNumListRespDTO {
    /**
     * 返回码。
     */
    private String retCode;

    /**
     * 返回消息。
     */
    private String retMsg;

    /**
     * ACC 生成的逻辑卡号文件名。
     */
    private String fileName;

    /**
     * 获取 ACC 返回码。
     * @return 返回码，成功为 {@code 0000}（部分链路返回 {@code 200}）
     */
    public String getRetCode() {
        return retCode;
    }

    /**
     * 设置 ACC 返回码。
     * @param retCode 返回码，成功为 {@code 0000}（部分链路返回 {@code 200}）
     */
    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    /**
     * 获取 ACC 返回消息。
     * @return 返回消息，失败时为 ACC 侧的失败。
     */
    public String getRetMsg() {
        return retMsg;
    }

    /**
     * 设置 ACC 返回消息。
     * @param retMsg 返回消息，失败时为 ACC 侧的失败。
     */
    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    /**
     * 获取 ACC 生成的逻辑卡号文件名。
     * @return 逻辑卡号文件名，调用方据此从 FTP 拉取卡号文件。
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 设置 ACC 生成的逻辑卡号文件名。
     * @param fileName 逻辑卡号文件名，调用方据此从 FTP 拉取卡号文件。
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
