package com.spzx.report.aitools;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;

// 向量化工具类
public class SpzxEmbeddingTools {
    /**
     * 将文本向量化
     * @param text 文本
     * @return 向量数据
     */
    public static Embedding getEmbedding(String text) {
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel
                .builder()
                .apiKey(SpzxEmbeddingConst.ALI_API_KEY)
                .baseUrl(SpzxEmbeddingConst.ALI_EMBEDDINGS_BASE_URL)// https://dashscope.aliyuncs.com/compatible-mode/v1
                .modelName(SpzxEmbeddingConst.ALI_EMBEDDINGS_MODEL) // text-embedding-v2
                .build();
        Response<Embedding> hello = embeddingModel.embed(text);
        Embedding content = hello.content();
        return content;
    }
}