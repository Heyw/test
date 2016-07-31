package threadpoolproject;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;

public abstract class AbstractExecutorService implements ExecutorService {
	@Override
	public Future<?> submit(Runnable r) {
	  if(r==null)  throw new  NullPointerException();
	   RunnableFuture<Void> ftask=newTaskFor(r,null);
		execute(ftask);
		return ftask;
	}
	@Override
	public <T> FutureTask<T> submit(Runnable r, T result) {
		if(r==null) throw new NullPointerException();
		RunnableFuture<T> ftask=newTaskFor(r,result);
		return null;
	}
	
    /**
     * 通过一个包装方法，将Runnable对象包装成FutureTask   
     * @param r
     * @param value
     * @return
     */
	private <T> RunnableFuture<T> newTaskFor(Runnable r, T value) {
		return new FutureTask<T>(r,value);
	}
}
