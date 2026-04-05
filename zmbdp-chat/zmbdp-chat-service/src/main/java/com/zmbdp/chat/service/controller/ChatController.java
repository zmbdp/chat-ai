package com.zmbdp.chat.service.controller;

import com.zmbdp.chat.service.domain.dto.ChatMessageDTO;
import com.zmbdp.chat.service.domain.vo.ChatSessionVO;
import com.zmbdp.chat.service.domain.vo.ChatMessageVO;
import com.zmbdp.chat.service.domain.vo.StreamReqDTO;
import com.zmbdp.chat.service.service.IChatService;
import com.zmbdp.common.domain.domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/chat")
public class ChatController {

    /**
     * 聊天客户端
     */
    @Autowired
    private ChatClient chatClient;

    /**
     * 会话记录
     */
    @Autowired
    private ChatMemory chatMemory;

    /**
     * 聊天记录
     */
    @Autowired
    private IChatService chatService;

    /**
     * 聊天接口
     *
     * @param prompt   输入内容
     * @param chatId   聊天 id
     * @param imageUrl 图片地址
     * @return 聊天结果
     */
    @PostMapping(value = "/stream", produces = "text/html;charset=utf-8")
    public Result<Flux<String>> stream(@Validated @RequestBody StreamReqDTO streamReqDTO) throws Exception {
        String prompt = streamReqDTO.getPrompt();
        String chatId = streamReqDTO.getChatId();
        String imageUrl = streamReqDTO.getImageUrl();
        log.info("chatId: {}, prompt: {}, imageUrl: {}", chatId, prompt, imageUrl);
        syncHistoryToMemory(chatId);
        chatService.save(chatId, prompt);
        chatService.saveMessage(chatId, "user", prompt);

        Flux<String> contentFlux;
        if (imageUrl == null || imageUrl.isBlank()) {
            // 如果说没有图片，则使用普通方式进行聊天
            contentFlux = this.chatClient.prompt()
                    .user(prompt)
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .content();
        } else {
            // 如果说有图片，则使用图片方式进行聊天
            log.info("使用图片进行聊天, chatId: {}, prompt: {}, imageUrl: {}", chatId, prompt, imageUrl);
            List<Media> mediaList = List.of(new Media(org.springframework.util.MimeTypeUtils.IMAGE_JPEG, new URI(imageUrl).toURL().toURI()));
            // 构建用户提示词
            UserMessage userMessage = UserMessage.builder()
                    .text(prompt)
                    .media(mediaList)
                    .build();
            contentFlux = this.chatClient.prompt(new Prompt(userMessage))
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .content();
        }

        StringBuilder assistantReply = new StringBuilder();
        return Result.success(contentFlux
                .doOnNext(assistantReply::append)
                .doOnComplete(() -> {
                    if (!assistantReply.isEmpty()) {
                        chatService.saveMessage(chatId, "assistant", assistantReply.toString());
                    }
                }));
    }

    /**
     * 获取会话列表
     *
     * @return 会话列表
     */
    @GetMapping("/session_history")
    public Result<List<ChatSessionVO>> sessionHistory() {
        return Result.success(chatService.getSessionHistory()
                .stream()
                .map(dto -> new ChatSessionVO(dto.getId(), dto.getTitle()))
                .collect(Collectors.toList()));
    }

    /**
     * 根据聊天 id 获取会话记录
     *
     * @param chatId 聊天 id
     */
    @GetMapping("/message_history")
    public Result<List<ChatMessageVO>> getMessageHistory(@RequestParam String chatId) {
        log.info("获取会话记录, chatId:{}", chatId);
        return Result.success(chatService.getMessageHistory(chatId)
                .stream()
                .map(ChatMessageVO::new)
                .collect(Collectors.toList()));
    }

    /**
     * 根据聊天 id 删除会话
     *
     * @param chatId 聊天 id
     * @return 删除结果
     */
    @DeleteMapping("/delete_by_chat_id")
    public Result<Boolean> deleteByChatId(@RequestParam String chatId) {
        log.info("删除会话, chatId:{}", chatId);
        try {
            chatService.deleteByChatId(chatId);
            chatMemory.clear(chatId);
        } catch (Exception e) {
            log.error("删除会话失败, chatId:{}", chatId, e);
            return Result.fail();
        }
        return Result.success();
    }

    /**
     * 把已持久化的历史记录同步到 Spring AI 内存中
     *
     * @param chatId 聊天 id
     */
    private void syncHistoryToMemory(String chatId) {
        List<Message> currentMessages = chatMemory.get(chatId);
        if (currentMessages != null && !currentMessages.isEmpty()) {
            return;
        }
        List<ChatMessageDTO> history = chatService.getMessageHistory(chatId);
        if (history.isEmpty()) {
            return;
        }
        List<Message> messages = history.stream()
                .map(this::buildMessage)
                .collect(Collectors.toList());
        chatMemory.add(chatId, messages);
    }

    /**
     * 持久化消息转 Spring AI Message
     *
     * @param chatMessageDTO 历史消息
     * @return Message
     */
    private Message buildMessage(ChatMessageDTO chatMessageDTO) {
        if ("assistant".equals(chatMessageDTO.getRole())) {
            return new AssistantMessage(chatMessageDTO.getContent());
        }
        return new UserMessage(chatMessageDTO.getContent());
    }
}