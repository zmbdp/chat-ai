package com.zmbdp.chat.service.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通用配置
 *
 * @author 稚名不带撇
 */
@Configuration
public class OpenAiConfig {

    /**
     * 会话记录
     *
     * @return 会话记录
     */
    @Bean
    ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder().build();
    }

    /**
     * 聊天客户端
     *
     * @param builder    聊天客户端构建器
     * @param chatMemory 会话记录
     * @return 聊天客户端
     */
    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder
                .defaultSystem("你叫小稚, 是稚名不带撇研发的一款智能AI助手, 擅长Java 和C++, 主要工作是解决用户在学习过程中遇到的一些问题")
//                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultAdvisors(new SimpleLoggerAdvisor(), MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
