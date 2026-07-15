package com.chinasofti.huateng.acc.security.server.itp.service;

import com.chinasofti.huateng.acc.security.server.itp.util.ItpCardUtils;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ItpLogicNumberService {
    private final AtomicInteger sequence = new AtomicInteger();
    private final Set<String> issuedLogicNumbers = ConcurrentHashMap.newKeySet();

    public String nextLogicNumber() {
        for (int i = 0; i < 10; i++) {
            int next = sequence.updateAndGet(current -> current >= 9_999_999 ? 1 : current + 1);
            String logicNumber = ItpCardUtils.buildLogicNumber(next);
            if (issuedLogicNumbers.add(logicNumber)) {
                return logicNumber;
            }
        }
        return null;
    }
}
