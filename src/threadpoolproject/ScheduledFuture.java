package threadpoolproject;



/**
 * 一个延迟的、结果可接受的操作，可将其取消。通常shcheduled的 future 是用 ScheduledExecutorService 安排任务的结果。
 * @author heyw
 *
 * @param <V>
 */
public interface ScheduledFuture<V>extends Delayed,Future<V> {

}
