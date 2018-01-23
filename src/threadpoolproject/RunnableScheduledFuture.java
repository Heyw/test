package threadpoolproject;


/**
 * 作为 Runnable 的 ScheduledFuture。成功执行 run 方法可以完成 Future 并允许访问其结果。
 * @author heyw
 *
 * @param <V>
 */
public interface RunnableScheduledFuture<V>extends RunnableFuture<V>,ScheduledFuture<V> {
    /**
     * 如果任务是周期性的，将返回true。周期性的任务可能会根据计划重复执行，非周期性任务只能执行一次
     * @return
     */
	boolean isPeriodic();
}
