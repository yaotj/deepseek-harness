package com.chinasofti.huateng.key.mapper;

import com.chinasofti.huateng.key.entity.MetroMemberStaticKey;
import com.chinasofti.huateng.key.page.KeyVersionView;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

import java.util.List;


@Mapper
@Component
public interface MetroMemberStaticKeyMapper {

    MetroMemberStaticKey selectActiveByCardNum(String metroMemberCardNum);


    int insertIfAbsent(MetroMemberStaticKey staticKey);


    List<KeyVersionView> selectStatusSummary();
}
