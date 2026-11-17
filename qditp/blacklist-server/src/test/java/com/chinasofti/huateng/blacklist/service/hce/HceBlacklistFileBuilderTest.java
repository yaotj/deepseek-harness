package com.chinasofti.huateng.blacklist.service.hce;

import com.chinasofti.huateng.blacklist.entity.Blacklist;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


public class HceBlacklistFileBuilderTest {

    @Test
    public void generateSampleFile() throws IOException {
        List<Blacklist> rows = new ArrayList<>();
        rows.add(sample("0178606904586419", "0442"));
        rows.add(sample("0178606904586420", "0442"));
        rows.add(sample("0178606904586433", "0443"));

        long version = 1L;
        byte[] content = HceBlacklistFileBuilder.build(version, rows);

        Path outDir = Paths.get(System.getProperty("user.dir")).getParent().resolve("output");
        Files.createDirectories(outDir);
        Path file = outDir.resolve("PRM.0013.9700." + String.format("%06d", version));
        Files.write(file, content);
        System.out.println("sample file written: " + file + ", bytes=" + content.length);
    }

    private Blacklist sample(String cardId, String cardType) {
        Blacklist row = new Blacklist();
        row.setCardId(cardId);
        row.setCardType(cardType);
        row.setThirdUserId("2088000000000001");
        return row;
    }
}
