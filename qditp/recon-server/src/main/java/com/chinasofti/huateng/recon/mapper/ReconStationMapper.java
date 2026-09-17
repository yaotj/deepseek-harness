package com.chinasofti.huateng.recon.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 车站到线路的映射，唯一用途是在 PAY 汇总文件写出前补齐「线路」那一段；本模块只读、零写入。
 */
@Mapper
public interface ReconStationMapper {

    /** 全量取车站码到线路码的映射，返回 {@code List<Map>}（只用两列，不为他域维表建实体）。 */
    List<Map<String, Object>> selectStationLineCodes();
}
