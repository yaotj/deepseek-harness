package com.chinasofti.huateng.key.mapper;

import com.chinasofti.huateng.key.page.KeyVersionView;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Mapper
@Component
public interface MetroAgmKeyVersionMapper {
    Long selectMaxApprovedBatchNumber(String providerId);


    List<KeyVersionView> selectLatestByProvider();

    String selectApprovalStatus(String providerId, Long batchNumber);


    int transitionApprovalStatus(String providerId, Long batchNumber, String fromStatus, String toStatus,
                                 String operator);
}
