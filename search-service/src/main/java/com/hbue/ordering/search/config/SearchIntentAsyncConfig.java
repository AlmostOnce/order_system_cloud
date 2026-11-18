package com.hbue.ordering.search.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 搜索意图是允许丢失的弱信号；线程和队列都有限，不能让推荐故障耗尽搜索请求资源。
 */
@Configuration
public class SearchIntentAsyncConfig {

    /**
     * 创建2个工作线程、64个等待任务的独立执行器。
     * 队列满或停止时拒绝，由调用方记录不含关键词的失败并丢弃；禁止CallerRuns让网络调用回到搜索线程。
     * 应用停止不等待队列排空，任务失败不重放搜索，已持久化意图由推荐服务负责幂等。
     *
     * @return 由Spring负责初始化和关闭的专用有界执行器
     */
    @Bean("searchIntentExecutor")
    public ThreadPoolTaskExecutor searchIntentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("search-intent-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
