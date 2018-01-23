package threadpoolproject;

import java.util.concurrent.Callable;


/**
 * 提供 ExecutorService 执行方法的默认实现。
 * 此类使用 newTaskFor 返回的 RunnableFuture 实现 submit、invokeAny 和 invokeAll 方法，默认情况下，RunnableFuture 是此包中提供的 FutureTask 类。
 * 例如，submit(Runnable) 的实现创建了一个关联 RunnableFuture 类，该类将被执行并返回。
 * 子类可以重写 newTaskFor 方法，以返回 FutureTask 之外的 RunnableFuture 实现。 
 * @author heyw
 *
 */
public abstract class AbstractExecutorService implements ExecutorService {
	public Future<?> submit(Runnable r) {
	  if(r==null)  throw new  NullPointerException();
	   RunnableFuture<Void> ftask=newTaskFor(r,null);
		execute(ftask);//执行子类ThreadPoolExecutor的executor()方法
		return ftask;
	}
    /**
     * @throws RejectedExecutionException
     * @throws NullPointerException
     */
	public <T> Future<T> submit(Runnable r, T result) {
		if(r==null) throw new NullPointerException();
		RunnableFuture<T> ftask=newTaskFor(r,result);
		execute(ftask);
		return ftask;
	}
	/**
	 * throws RejectedExecutionException
	 * throws NullPointerException
	 */
	public <T>Future<T>submit(Callable<T>callable){
		if(callable==null)throw new NullPointerException();
		RunnableFuture<T>ftask=newTaskFor(callable);
		execute(ftask);
		return ftask;
	}
    /**
     * 通过一个包装方法，将Runnable对象包装成FutureTask   
     * @param r
     * @param value
     * @return
     */
	protected <T> RunnableFuture<T> newTaskFor(Runnable r, T value) {
		return new FutureTask<T>(r,value);
	}
	
	protected<T>RunnableFuture<T>newTaskFor(Callable<T>callable){
		return new FutureTask<T>(callable);
	}
}
