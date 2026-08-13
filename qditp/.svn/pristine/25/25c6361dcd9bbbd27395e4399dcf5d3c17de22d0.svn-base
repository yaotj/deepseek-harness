package com.chinasofti.huateng.para.mapper.fare;

import com.chinasofti.huateng.para.model.BaseFarePageView;
import com.chinasofti.huateng.para.model.BaseFareStationOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BaseFarePageMapper {
    List<BaseFarePageView> selectCurrentPage(@Param("entryStationCode") String entryStationCode,
                                             @Param("exitStationCode") String exitStationCode);

    List<BaseFareStationOption> selectCurrentStations();
}
