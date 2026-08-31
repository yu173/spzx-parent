package com.spzx.report;

import com.spzx.report.aitools.PineconeConst;
import com.spzx.report.aitools.SpzxEmbeddingTools;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore;
import dev.langchain4j.store.embedding.pinecone.PineconeServerlessIndexConfig;
import lombok.Data;

/*
测试向量数据库存储：pinecone
 */
public class Test04 {
    public static void main(String[] args) {
        // 原始数据
        String text = "大漠孤烟直，长河落日圆";

        PineconeEmbeddingStore embeddingStore = PineconeEmbeddingStore.builder()
                .apiKey(PineconeConst.PK1)
                .index("spzx-report-count-key-1")
                .createIndex(PineconeServerlessIndexConfig.builder()
                        .cloud("AWS")
                        .region("us-east-1")
                        .dimension(1536)
                        .build())
                .build();
        // 2 存入向量数据
        // 向量数据
        Embedding embedding = SpzxEmbeddingTools.getEmbedding(text);

        // 构建元数据
        TextSegment meta = TextSegment.from(text,
                Metadata.metadata("作者", "王维").put("朝代","唐朝")
        );

        String result = embeddingStore.add(embedding, meta);
        System.out.println(result);

        //这个向量数据库最多只能创建5个索引
    }
}
