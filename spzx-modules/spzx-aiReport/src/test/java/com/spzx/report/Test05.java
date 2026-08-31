package com.spzx.report;

import com.spzx.report.aitools.PineconeConst;
import com.spzx.report.aitools.SpzxEmbeddingTools;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore;

import java.util.List;

//从向量数据库中匹配相似的数据
public class Test05 {
    public static void main(String[] args) {
        PineconeEmbeddingStore embeddingStore = PineconeEmbeddingStore.builder()
                .apiKey(PineconeConst.PK1)
                .index(PineconeConst.PGK_INDEX_GROUP_KEY_1)
                .metadataTextKey("column")
                .metadataTextKey("word")
                .nameSpace("default")
                .build();

        String text = "各种品牌";
        Embedding embedding = SpzxEmbeddingTools.getEmbedding(text);
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding( embedding)
                .maxResults(1)//批量返回的数据条数
                .minScore(0.8)//相似度分数，分数越大越精准
                .build();
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(request);
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();
        EmbeddingMatch<TextSegment> embeddingMatch = matches.get(0);
        TextSegment textSegment = embeddingMatch.embedded();
        Metadata metadata = textSegment.metadata();
        String column = metadata.getString("column");
        String word = metadata.getString("word");
        System.out.println(column);
        System.out.println(word);
    }
}
