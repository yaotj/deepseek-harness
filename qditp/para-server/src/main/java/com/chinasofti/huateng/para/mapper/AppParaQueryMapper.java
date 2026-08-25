package com.chinasofti.huateng.para.mapper;

import com.chinasofti.huateng.model.app.LineCodeRecordDTO;
import com.chinasofti.huateng.model.app.RequestStationLineInfoDTO;
import com.chinasofti.huateng.model.app.RequestStationNameResult;
import com.chinasofti.huateng.model.app.StationCodeRecordDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * APP 参数查询 Mapper。
 */
@Mapper
public interface AppParaQueryMapper {
    /**
     * 查询当前路网版本的线路代码列表。
     *
     * @return 线路代码列表
     */
    List<LineCodeRecordDTO> selectLineCodeList();

    /**
     * 查询当前路网版本的车站代码列表。
     *
     * @param lineCode 线路代码，为空时查询全部车站
     * @return 车站代码列表
     */
    List<StationCodeRecordDTO> selectStationCodeList(@Param("lineCode") String lineCode);

    /**
     * 根据进出站查询当前费率参数版本的费率等级。
     *
     * @param entryStationCode 进站车站代码
     * @param exitStationCode 出站车站代码
     * @return 费率等级
     */
    Integer selectFareTier(@Param("entryStationCode") String entryStationCode,
                           @Param("exitStationCode") String exitStationCode);

    /**
     * 根据费率等级查询当前费率参数版本的基础票价。
     *
     * @param fareTier 费率等级
     * @return 票价
     */
    Integer selectTicketPrice(@Param("fareTier") Integer fareTier);

    /**
     * 根据车站代码查询当前路网参数版本的车站中文名称。
     *
     * @param stationCode 车站代码
     * @return 车站中文名称
     */
    String selectStationName(@Param("stationCode") String stationCode);

    /**
     * 根据车站代码查询当前路网参数版本的车站名称及所属线路信息。
     *
     * @param stationCode 车站代码
     * @return 车站名称、所属线路代码、线路名称
     */
    RequestStationLineInfoDTO selectStationLineInfo(@Param("stationCode") String stationCode);

    /**
     * 批量根据车站代码查询当前路网参数版本的车站中文名称。
     *
     * @param stationCodes 车站代码列表
     * @return 车站名称结果列表
     */
    List<RequestStationNameResult> selectStationNameBatch(@Param("stationCodes") List<String> stationCodes);
}
