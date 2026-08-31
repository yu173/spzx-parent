package com.spzx.report.aitools;

/**
 * 针对于向量模型的请求参数：三件套
 */
public class SpzxEmbeddingConst {
    // 通过阿里百炼ai对数据进行向量化处理
    public static String ALI_API_KEY =  "sk-6477ed13225b485fb194e05ab5ecb377";
    //public static String ALI_EMBEDDINGS_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";// http
    public static String ALI_EMBEDDINGS_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";// sdk
    public static String ALI_EMBEDDINGS_MODEL = "text-embedding-v2";
}