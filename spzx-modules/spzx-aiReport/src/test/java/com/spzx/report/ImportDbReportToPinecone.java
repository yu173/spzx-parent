package com.spzx.report;

import com.spzx.report.service.SpzxEmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ImportDbReportToPinecone {

    @Autowired
    SpzxEmbeddingService spzxEmbeddingService;

    @Test
    public void testStore() {
        spzxEmbeddingService.embeddingGroupKeyToPinecone();        
        spzxEmbeddingService.embeddingCountKeyToPinecone();
        spzxEmbeddingService.embeddingSelectKeyToPinecone();
    }

}