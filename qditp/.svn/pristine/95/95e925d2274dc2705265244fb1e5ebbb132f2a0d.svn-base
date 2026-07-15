package com.chinasofti.huateng.para.model;

import com.chinasofti.huateng.para.entity.network.LineInfo;
import com.chinasofti.huateng.para.entity.network.SectInfo;
import com.chinasofti.huateng.para.entity.network.StationInfo;
import com.chinasofti.huateng.para.entity.network.TsfInfo;
import com.chinasofti.huateng.para.entity.network.ZoneDtl;
import com.chinasofti.huateng.para.entity.network.ZoneInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 路网拓扑参数文件解析结果。
 */
public class RowNetworkParseResult {
    private String filePath;
    private String fileName;
    private Integer fileLength;
    private Map<String, Object> header;
    private Integer bodyReadBytes;
    private Integer bodyTotalBytes;
    private String md5InFile;
    private String md5Calculated;
    private Boolean md5Valid;
    private List<LineInfo> lineInfos = new ArrayList<>();
    private List<StationInfo> stationInfos = new ArrayList<>();
    private List<TsfInfo> tsfInfos = new ArrayList<>();
    private List<ZoneInfo> zoneInfos = new ArrayList<>();
    private List<ZoneDtl> zoneDtls = new ArrayList<>();
    private List<SectInfo> sectInfos = new ArrayList<>();

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Integer getFileLength() {
        return fileLength;
    }

    public void setFileLength(Integer fileLength) {
        this.fileLength = fileLength;
    }

    public Map<String, Object> getHeader() {
        return header;
    }

    public void setHeader(Map<String, Object> header) {
        this.header = header;
    }

    public Integer getBodyReadBytes() {
        return bodyReadBytes;
    }

    public void setBodyReadBytes(Integer bodyReadBytes) {
        this.bodyReadBytes = bodyReadBytes;
    }

    public Integer getBodyTotalBytes() {
        return bodyTotalBytes;
    }

    public void setBodyTotalBytes(Integer bodyTotalBytes) {
        this.bodyTotalBytes = bodyTotalBytes;
    }

    public String getMd5InFile() {
        return md5InFile;
    }

    public void setMd5InFile(String md5InFile) {
        this.md5InFile = md5InFile;
    }

    public String getMd5Calculated() {
        return md5Calculated;
    }

    public void setMd5Calculated(String md5Calculated) {
        this.md5Calculated = md5Calculated;
    }

    public Boolean getMd5Valid() {
        return md5Valid;
    }

    public void setMd5Valid(Boolean md5Valid) {
        this.md5Valid = md5Valid;
    }

    public List<LineInfo> getLineInfos() {
        return lineInfos;
    }

    public void setLineInfos(List<LineInfo> lineInfos) {
        this.lineInfos = lineInfos;
    }

    public List<StationInfo> getStationInfos() {
        return stationInfos;
    }

    public void setStationInfos(List<StationInfo> stationInfos) {
        this.stationInfos = stationInfos;
    }

    public List<TsfInfo> getTsfInfos() {
        return tsfInfos;
    }

    public void setTsfInfos(List<TsfInfo> tsfInfos) {
        this.tsfInfos = tsfInfos;
    }

    public List<ZoneInfo> getZoneInfos() {
        return zoneInfos;
    }

    public void setZoneInfos(List<ZoneInfo> zoneInfos) {
        this.zoneInfos = zoneInfos;
    }

    public List<ZoneDtl> getZoneDtls() {
        return zoneDtls;
    }

    public void setZoneDtls(List<ZoneDtl> zoneDtls) {
        this.zoneDtls = zoneDtls;
    }

    public List<SectInfo> getSectInfos() {
        return sectInfos;
    }

    public void setSectInfos(List<SectInfo> sectInfos) {
        this.sectInfos = sectInfos;
    }

    @Override
    public String toString() {
        return "RowNetworkParseResult{" +
                "filePath='" + filePath + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileLength=" + fileLength +
                ", header=" + header +
                ", bodyReadBytes=" + bodyReadBytes +
                ", bodyTotalBytes=" + bodyTotalBytes +
                ", md5InFile='" + md5InFile + '\'' +
                ", md5Calculated='" + md5Calculated + '\'' +
                ", md5Valid=" + md5Valid +
                ", lineInfos=" + lineInfos +
                ", stationInfos=" + stationInfos +
                ", tsfInfos=" + tsfInfos +
                ", zoneInfos=" + zoneInfos +
                ", zoneDtls=" + zoneDtls +
                ", sectInfos=" + sectInfos +
                '}';
    }
}
