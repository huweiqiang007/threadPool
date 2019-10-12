package com.ghy.centerinfo.medicine.settle.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Queue;
import java.util.concurrent.*;

/**
 * create by huweiqiang on 2019/10/12
 */
@Service
public class ThreadPoolService {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolService.class);
    //获取CPU核数
    private static final int availableProcessor = Runtime.getRuntime().availableProcessors();
    //闲置线程存活时间
    private static final long TIME_KEEP_ALIVE = 600;
    //线程池所使用的缓冲队列的大小
    private static final int SIZE_WORK_QUEUE = 500;
    //任务调度周期
    private static final int PERIOD_TASK_QOS = 1000;
    //线程池满，存入此缓冲队列,最大长度为Integer.max
    private final Queue<Runnable> linkedBlockingDeque = new LinkedBlockingDeque<>();
    //线程池的拒绝策略：线程池超出界线时将任务加入缓冲队列
    private final RejectedExecutionHandler handler = (task,executor)->{
        logger.info("===========>线程池已超出核心线程数将任务加入缓冲队列");
        linkedBlockingDeque.offer(task);
    };
    //线程池
    private volatile static ThreadPoolExecutor executor;
    //创建单例懒汉式
    private ThreadPoolExecutor getInstance(){
        if(executor == null){
            synchronized (ThreadPoolService.class){
                if(executor == null){
                    //IO密集型、CPU密集型
                    int coreSize = availableProcessor  * 2;
                    int maxSize = (availableProcessor + 1) * 2;
                    executor = new ThreadPoolExecutor(coreSize,maxSize,TIME_KEEP_ALIVE,
                            TimeUnit.SECONDS,new ArrayBlockingQueue<>(SIZE_WORK_QUEUE),handler);
                }
            }
        }
        return executor;
    }
    //将缓冲区的任务重加加载到线程池
    private final Runnable bufferTask = () ->{
        //判断缓冲区是否有记录
        // 并且判断线程池的队列容量少于SIZE_WORK_QUEUE，开始把缓冲区的队列的数据加载到线程池中去
        if(!linkedBlockingDeque.isEmpty()&&
                executor.getQueue().size() < SIZE_WORK_QUEUE ){
            logger.info("===============>(调度线程池)缓冲队列出现数据，重新添加到线程池,缓冲区队列数量:{},线程池队列数量:{}",linkedBlockingDeque.size(),executor.getQueue().size());
            executor.execute(linkedBlockingDeque.poll());
        }
    };
    //创建一个定时任务线程池，也称为调度线程池
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    //定时以及周期的执缓冲区队列中的任务
    protected final ScheduledFuture<?> mTaskHandler = scheduler.scheduleAtFixedRate(bufferTask, 0,
            PERIOD_TASK_QOS, TimeUnit.MILLISECONDS);
    /**
     * 禁止实例化
     */
    private ThreadPoolService(){

    }
    /**
     * 线程池中添加任务
     */
    public void addThreadPoolTask(Runnable task){
        if(task != null){
            getInstance().execute(task);
        }
    }
    protected boolean isTaskEnd() {
        if (executor.getActiveCount() == 0) {
            return true;
        } else {
            return false;
        }
    }

    public void shutdown() {
        linkedBlockingDeque.clear();
        executor.shutdown();
    }

}
