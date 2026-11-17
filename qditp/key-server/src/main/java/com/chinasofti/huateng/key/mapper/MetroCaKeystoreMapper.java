package com.chinasofti.huateng.key.mapper;

import com.chinasofti.huateng.key.entity.MetroCaKeystore;
import com.chinasofti.huateng.key.page.KeyVersionView;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

import java.util.List;


@Mapper
@Component
public interface MetroCaKeystoreMapper {

    List<MetroCaKeystore> selectActiveList();


    List<KeyVersionView> selectVersionSummary();
}
