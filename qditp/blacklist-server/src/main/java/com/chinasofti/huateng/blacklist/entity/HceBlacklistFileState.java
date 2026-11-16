package com.chinasofti.huateng.blacklist.entity;

import java.time.LocalDateTime;

/**
 * HCE 黑名单文件版本号状态表实体：单行表，承载上一次已分配的文件版本号。
 *
 * <p>版本号单调递增、只进不退：即便某一轮上传 FTP 失败，版本也已消耗，
 * 下一轮用新版本生成新文件，ACC 侧按版本号识别新旧。
 */
public class HceBlacklistFileState {

    /**
     * 参数类型，主键（如 0013）。
     */
    private String paramType;

    /**
     * 当前已分配的最新文件版本号。
     */
    private Long currentVersion;

    /**
     * 最近一次分配时间。
     */
    private LocalDateTime updateTime;

    public String getParamType() {
        return paramType;
    }

    public void setParamType(String paramType) {
        this.paramType = paramType;
    }

    public Long getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(Long currentVersion) {
        this.currentVersion = currentVersion;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
