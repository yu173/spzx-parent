package com.spzx.report.aitools;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

/**
 * 处理请求的问题
 */
public class SpzxAiQuest {

    // 初始化组件
    private static ChatModel llmProc;// 通义模型
    private static ChatModel llmSum;// deepseek模型
    private MyAssistAgent agent;// 我们自己的被代理函数式接口

    public SpzxAiQuest(){

        // 初始化语言模型
        this.llmProc = OpenAiChatModel.builder()
                .apiKey(SpzxAiConst.DASHSCOPE_API_KEY)
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1") // SpzxAiConst.DASHSCOPE_API_BASE_URL;
                .modelName("qwen-plus")// 通义千问
                .build();

        this.llmSum = OpenAiChatModel.builder()
                .apiKey(SpzxAiConst.DASHSCOPE_API_KEY)
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .modelName("deepseek-v3")// ds
                .build();

        // 初始化 chatAgent 代理
        this.agent = AiServices.builder(MyAssistAgent.class)
                .chatModel(llmProc)
                .systemMessageProvider((ignored) -> PromptGenerator.systemPrompt)
                .build();
    }


    /***
     * 控制层调用过程（问题处理）
     * @param question
     * @return
     * @throws Exception
     */
    public String processQuestion(String question) {

        System.out.println("1 原始问题："+question);

        //组合提示词模板
        System.out.println("2 组合提示词模板=============================================");
        String questionMerge = PromptGenerator.generatePrompt(question);
        System.out.println(questionMerge);

        // 通义千问
        System.out.println("3 提问：通义千问===============================================");
        String answer = agent.chat(questionMerge);
        int jsonStart = answer.lastIndexOf("{");
        int jsonEnd = answer.lastIndexOf("}");
        System.out.println("4 回答：通义千问===============================================");
        System.out.println(answer);
        String jsonAnswer = answer.substring(jsonStart, jsonEnd+1);
        return  jsonAnswer;
    }

}