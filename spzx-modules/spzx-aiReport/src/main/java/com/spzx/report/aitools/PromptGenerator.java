package com.spzx.report.aitools;

import com.alibaba.nacos.common.packagescan.resource.ClassPathResource;
import com.alibaba.nacos.common.packagescan.resource.Resource;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;

// 提示词生成器核心类
public class PromptGenerator {

    // 字符串提示词模板
    public static final String PROMPT_TEMPLATE = "";// 简单的提示词可以直接字符串

    // 文本提示词模板
    public static String systemPrompt = "你是一个精通电商领域的助手，需要根据用户的要求准确回答问题。" + PromptGenerator.readPromptFile();// 系统提示词信息

    public static String readPromptFile(){
        try {
            Resource resource = new ClassPathResource("aiReportPrompt.txt");
            String content = FileCopyUtils.copyToString(new InputStreamReader(resource.getInputStream()));//此处读取提示词文本
            return content;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return PROMPT_TEMPLATE;
    }
    
    public static String generatePrompt(String question) {
        LocalDateTime now = LocalDateTime.now();
        return PROMPT_TEMPLATE + "\n【当前问题】" + question + "\n【当前时间】" + now;
    }
}