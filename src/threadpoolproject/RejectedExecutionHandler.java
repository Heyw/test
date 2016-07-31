package threadpoolproject;

public interface RejectedExecutionHandler {
    void rejectedExecution(Runnable r,ThreadPoolExecutor executor);
}
