package com.spzx.report;

import dev.langchain4j.model.openai.OpenAiChatModel;
/*
测试LLM模型
 */
public class Test01 {
    public static void main(String[] args) {
        //初始化模型
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .apiKey("sk-6477ed13225b485fb194e05ab5ecb377")
                .modelName("deepseek-v3")
                .build();

        //向模型提问
        String answer = model.chat("什么是人生，人活着有什么意思？不停抱怨的人性情总是不好该怎么办");
        //输出结果
        System.out.println(answer);
    }
}
