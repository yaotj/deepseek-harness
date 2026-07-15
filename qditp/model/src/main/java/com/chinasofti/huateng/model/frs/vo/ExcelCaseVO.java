package com.chinasofti.huateng.model.frs.vo;

import com.chinasofti.huateng.model.frs.*;

import java.io.Serializable;
import java.util.List;

public class ExcelCaseVO implements Serializable {
    private static final long serialVersionUID = 696528254781236L;
    public List<RulLineAreaDTO> lineAreaNoInfo;
    public List<RulFeeInfoDTO> feeInfo;
    public List<RulFeeReviseDTO> feeReviseInfo;
    public List<RulLineInfoDTO> lineInfo;
    public List<RulIncomeInfoDTO> incomeInfo;
    public List<RulRuntimeInterDTO> runtimeInter;
    public List<RulStationInfoDTO> stationInfo;
    public List<RulNearStationInfoDTO> nearStationInfo;
    public List<RulTsfInfoDTO> tsfInfo;

    public List<RulLineAreaDTO> getLineAreaNoInfo() {
        return lineAreaNoInfo;
    }

    public void setLineAreaNoInfo(List<RulLineAreaDTO> lineAreaNoInfo) {
        this.lineAreaNoInfo = lineAreaNoInfo;
    }

    public List<RulFeeInfoDTO> getFeeInfo() {
        return feeInfo;
    }

    public void setFeeInfo(List<RulFeeInfoDTO> feeInfo) {
        this.feeInfo = feeInfo;
    }

    public List<RulFeeReviseDTO> getFeeReviseInfo() {
        return feeReviseInfo;
    }

    public void setFeeReviseInfo(List<RulFeeReviseDTO> feeReviseInfo) {
        this.feeReviseInfo = feeReviseInfo;
    }

    public List<RulLineInfoDTO> getLineInfo() {
        return lineInfo;
    }

    public void setLineInfo(List<RulLineInfoDTO> lineInfo) {
        this.lineInfo = lineInfo;
    }

    public List<RulIncomeInfoDTO> getIncomeInfo() {
        return incomeInfo;
    }

    public void setIncomeInfo(List<RulIncomeInfoDTO> incomeInfo) {
        this.incomeInfo = incomeInfo;
    }

    public List<RulRuntimeInterDTO> getRuntimeInter() {
        return runtimeInter;
    }

    public void setRuntimeInter(List<RulRuntimeInterDTO> runtimeInter) {
        this.runtimeInter = runtimeInter;
    }

    public List<RulStationInfoDTO> getStationInfo() {
        return stationInfo;
    }

    public void setStationInfo(List<RulStationInfoDTO> stationInfo) {
        this.stationInfo = stationInfo;
    }

    public List<RulNearStationInfoDTO> getNearStationInfo() {
        return nearStationInfo;
    }

    public void setNearStationInfo(List<RulNearStationInfoDTO> nearStationInfo) {
        this.nearStationInfo = nearStationInfo;
    }

    public List<RulTsfInfoDTO> getTsfInfo() {
        return tsfInfo;
    }

    public void setTsfInfo(List<RulTsfInfoDTO> tsfInfo) {
        this.tsfInfo = tsfInfo;
    }
}
