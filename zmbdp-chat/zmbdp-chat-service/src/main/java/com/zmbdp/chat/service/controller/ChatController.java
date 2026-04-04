package com.zmbdp.chat.service.controller;

import com.zmbdp.chat.service.entity.ChatInfo;
import com.zmbdp.chat.service.entity.MessageVO;
import com.zmbdp.chat.service.repository.ChatHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatClient chatClient;

    /**
     * 会话记录
     */
    @Autowired
    private ChatMemory chatMemory;

    /**
     * 聊天记录
     */
    @Autowired
    private ChatHistoryRepository memoryChatHistoryRepository;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 聊天
     */
    @RequestMapping(value = "/stream", produces = "text/html;charset=utf-8")
    public Flux<String> stream(String prompt, String chatId, String imageUrl) throws Exception {
        log.info("chatId: {}, prompt: {}, imageUrl: {}", chatId, prompt, imageUrl);
        // 你自己的历史记录
        memoryChatHistoryRepository.save(chatId, prompt);

        if (imageUrl == null || imageUrl.isBlank()) {
            return this.chatClient.prompt()
                    .user(prompt)
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .content();
        }

        List<Media> mediaList = List.of(new Media(MimeTypeUtils.IMAGE_JPEG, new URI(imageUrl).toURL().toURI()));
        UserMessage userMessage = UserMessage.builder()
                .text(prompt)
                .media(mediaList)
                .build();

        return this.chatClient.prompt(new Prompt(userMessage))
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }

    /**
     * 获取会话列表
     *
     * @return 会话列表
     */
    @RequestMapping("/getChatIds")
    public List<ChatInfo> getChatIds() {
        return memoryChatHistoryRepository.getChats();
    }

    /**
     * 根据会话 id 获取会话记录
     *
     * @param chatId 会话 id
     */
    @RequestMapping("/getChatHistory")
    public List<MessageVO> getChatHistory(String chatId) {
        log.info("获取会话记录, chatId:{}", chatId);
        List<Message> messages = chatMemory.get(chatId);
        return messages.stream().map(MessageVO::new).collect(Collectors.toList());
    }

    /**
     * 根据会话 id 删除会话
     *
     * @param chatId 会话 id
     * @return 删除结果
     */
    @RequestMapping("/deleteChat")
    public Boolean deleteChat(String chatId) {
        log.info("删除会话, chatId:{}", chatId);
        try {
            memoryChatHistoryRepository.clearByChatId(chatId);
            chatMemory.clear(chatId);
        } catch (Exception e) {
            log.error("删除会话失败, chatId:{}", chatId, e);
            return false;
        }
        return true;
    }
}
