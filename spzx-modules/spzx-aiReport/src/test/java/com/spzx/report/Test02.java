package com.spzx.report;

import com.spzx.report.aitools.SpzxAiQuest;

public class Test02 {
    public static void main(String[] args) {
        SpzxAiQuest spzxAiQuest = new SpzxAiQuest();
        String answer = spzxAiQuest.processQuestion("小米在城市什么品牌最受欢迎");
        System.out.println(answer);
    }
}
