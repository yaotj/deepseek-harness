package com.chinasofti.huateng.para.mapper;

import com.chinasofti.huateng.model.app.LineCodeRecordDTO;
import com.chinasofti.huateng.model.app.RequestStationLineInfoDTO;
import com.chinasofti.huateng.model.app.RequestStationNameResult;
import com.chinasofti.huateng.model.app.StationCodeRecordDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


@Mapper
public interface AppParaQueryMapper {

    List<LineCodeRecordDTO> selectLineCodeList();


    List<StationCodeRecordDTO> selectStationCodeList(@Param("lineCode") String lineCode);


    Integer selectFareTier(@Param("entryStationCode") String entryStationCode,
                           @Param("exitStationCode") String exitStationCode);


    Integer selectTicketPrice(@Param("fareTier") Integer fareTier);


    String selectStationName(@Param("stationCode") String stationCode);


    RequestStationLineInfoDTO selectStationLineInfo(@Param("stationCode") String stationCode);


    List<RequestStationNameResult> selectStationNameBatch(@Param("stationCodes") List<String> stationCodes);
}
