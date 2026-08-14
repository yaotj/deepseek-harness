package com.chinasofti.huateng.model.para;

import java.util.List;
import java.util.stream.Collectors;

import com.chinasofti.huateng.model.enums.DeviceTypeEnum;

public class SearchDevByStationCodesAndDevTypesDTO {

    private List<String> stationCodes;

    private List<String> devTypes;

    public List<String> getStationCodes() {
        return stationCodes;
    }

    public void setStationCodes(List<String> stationCodes) {
        this.stationCodes = stationCodes;
    }

    public List<String> getDevTypes() {
        return devTypes;
    }

    public void setDevTypes(List<String> devTypes) {
        this.devTypes = devTypes;
    }

    public List<DeviceTypeEnum> getDevTypeEnums() {
        return devTypes.stream()
            .map(DeviceTypeEnum::fromCode)
            .collect(Collectors.toList());
    }
}
