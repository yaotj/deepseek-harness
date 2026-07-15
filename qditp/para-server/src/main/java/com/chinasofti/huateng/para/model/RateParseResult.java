package com.chinasofti.huateng.para.model;

import com.chinasofti.huateng.para.entity.fare.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RateParseResult {
    private String filePath;
    private String fileName;
    private Integer fileLength;
    private Map<String, Object> header;
    private Integer bodyReadBytes;
    private Integer bodyTotalBytes;
    private String md5InFile;
    private String md5Calculated;
    private Boolean md5Valid;
    private AddPara addPara;
    private List<FareMatrix> fareMatrices = new ArrayList<>();
    private List<TicketFare> ticketFares = new ArrayList<>();
    private List<FareGroup> fareGroups = new ArrayList<>();
    private List<BaseFare> baseFares = new ArrayList<>();

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Integer getFileLength() { return fileLength; }
    public void setFileLength(Integer fileLength) { this.fileLength = fileLength; }
    public Map<String, Object> getHeader() { return header; }
    public void setHeader(Map<String, Object> header) { this.header = header; }
    public Integer getBodyReadBytes() { return bodyReadBytes; }
    public void setBodyReadBytes(Integer bodyReadBytes) { this.bodyReadBytes = bodyReadBytes; }
    public Integer getBodyTotalBytes() { return bodyTotalBytes; }
    public void setBodyTotalBytes(Integer bodyTotalBytes) { this.bodyTotalBytes = bodyTotalBytes; }
    public String getMd5InFile() { return md5InFile; }
    public void setMd5InFile(String md5InFile) { this.md5InFile = md5InFile; }
    public String getMd5Calculated() { return md5Calculated; }
    public void setMd5Calculated(String md5Calculated) { this.md5Calculated = md5Calculated; }
    public Boolean getMd5Valid() { return md5Valid; }
    public void setMd5Valid(Boolean md5Valid) { this.md5Valid = md5Valid; }
    public AddPara getAddPara() { return addPara; }
    public void setAddPara(AddPara addPara) { this.addPara = addPara; }
    public List<FareMatrix> getFareMatrices() { return fareMatrices; }
    public void setFareMatrices(List<FareMatrix> fareMatrices) { this.fareMatrices = fareMatrices; }
    public List<TicketFare> getTicketFares() { return ticketFares; }
    public void setTicketFares(List<TicketFare> ticketFares) { this.ticketFares = ticketFares; }
    public List<FareGroup> getFareGroups() { return fareGroups; }
    public void setFareGroups(List<FareGroup> fareGroups) { this.fareGroups = fareGroups; }
    public List<BaseFare> getBaseFares() { return baseFares; }
    public void setBaseFares(List<BaseFare> baseFares) { this.baseFares = baseFares; }
}
