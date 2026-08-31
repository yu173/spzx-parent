package com.spzx.report;


import com.spzx.report.aitools.SpzxEmbeddingConst;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;

/**
 * 将文本向量化
 */
public class Test03 {
    public static void main(String[] args) {
        //langchain4j集成openai
        OpenAiEmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(SpzxEmbeddingConst.ALI_API_KEY)
                .baseUrl(SpzxEmbeddingConst.ALI_EMBEDDINGS_BASE_URL)
                .modelName(SpzxEmbeddingConst.ALI_EMBEDDINGS_MODEL)
                .build();
        //向量模型：将文本转换为向量数据
        Response<Embedding> response = embeddingModel.embed("hello world");
        //向量数据
        Embedding embedding = response.content();
        System.out.println(embedding);//1536维向量数组
    }
}
