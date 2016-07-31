package threadpoolproject;

public interface Executor {
	/**
	 * 在将来某个时间点执行给定的任务，考虑到{@code Executor}}的实现类，任务可以在一个新线程中执行，或者在一个线程池中执行，或者调用
	 * 一个线程来执行
	 * @param command the run task
	 * @param RejectedExecutionException if the task cannot be accpeted for execution 
	 */
       void execute(Runnable command);
}
