package com.spzx.report.aitools;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

//向量数据库相似查询的工具类
public class PineconeSimilaryUtil {
    public static Map<String, String> getPineconeSimilarityEmbeddings(String question, String pname) {

        //创建向量存储
        EmbeddingStore embeddingStore = PineconeEmbeddingStore.builder()
                .apiKey(PineconeConst.PK1)// pinecone的key
                .index(pname)//如果指定的索引不存在，将创建一个新的索引
                .metadataTextKey("column")
                .metadataTextKey("word")
                .nameSpace("default")
                .build();

        // 先将要拿来匹配的文本向量化
        Embedding queryEmbedding = SpzxEmbeddingTools.getEmbedding(question);

        //创建搜索请求对象
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(1) //匹配最相似的一条记录
                .minScore(0.6)
                .build();

        //根据搜索请求 searchRequest 在向量存储中进行相似度搜索
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        System.out.println(searchResult);
        //searchResult.matches()：获取搜索结果中的匹配项列表。
        //.get(0)：从匹配项列表中获取第一个匹配项
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();
        if(matches.size()>0){
            EmbeddingMatch<TextSegment> embeddingMatch = searchResult.matches().get(0);
            //获取匹配项
            String column = embeddingMatch.embedded().metadata().getString("column");
            String word = embeddingMatch.embedded().metadata().getString("word");

            // 返回结果
            Map<String,String> resultMap = new HashMap<>();
            resultMap.put("column",column);
            resultMap.put("word",word);
            return resultMap;
        }else {
            // 返回结果
            Map<String,String> resultMap = new HashMap<>();
            resultMap.put("column","");
            resultMap.put("word","");
            return resultMap;
        }
    }
}