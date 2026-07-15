package com.chinasofti.huateng.wallet.model;

/**
 * 钱包操作结果。
 */
public class WalletOperateResult {
    /**
     * 钱包标识。
     */
    private String walletId;

    /**
     * 钱包编号。
     */
    private String walletNo;

    /**
     * 钱包状态。
     */
    private String walletStatus;

    /**
     * 处理结果说明。
     */
    private String message;

    public String getWalletId() {
        return walletId;
    }

    public void setWalletId(String walletId) {
        this.walletId = walletId;
    }

    public String getWalletNo() {
        return walletNo;
    }

    public void setWalletNo(String walletNo) {
        this.walletNo = walletNo;
    }

    public String getWalletStatus() {
        return walletStatus;
    }

    public void setWalletStatus(String walletStatus) {
        this.walletStatus = walletStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "WalletOperateResult{" +
                "walletId='" + walletId + '\'' +
                ", walletNo='" + walletNo + '\'' +
                ", walletStatus='" + walletStatus + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
