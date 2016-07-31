package threadpoolproject;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

public interface ExecutorService extends Executor {
    /**
     * 提交一个Runnable 任务用来执行，同时会返回一个代表该task的Future
     * Future的方法{@code get}将会返回{@code null}，如果该任务成功完成了
     * @param r
     * @return
     */
	Future<?> submit(Runnable r);
	/**
	 * 提交一个Runnable 任务用来执行，同时会返回一个代表该task的FutureTask
	 * Future的方法{@code get}将会返回指定的result,如果该任务成功完成了
	 * @param r
	 * @param result
	 * @return
	 */
     <T> FutureTask<T> submit(Runnable r,T result);
}
