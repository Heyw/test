package threadpoolproject;

import java.util.concurrent.Callable;


/**
 * �ṩ ExecutorService ִ�з�����Ĭ��ʵ�֡�
 * ����ʹ�� newTaskFor ���ص� RunnableFuture ʵ�� submit��invokeAny �� invokeAll ������Ĭ������£�RunnableFuture �Ǵ˰����ṩ�� FutureTask �ࡣ
 * ���磬submit(Runnable) ��ʵ�ִ�����һ������ RunnableFuture �࣬���ཫ��ִ�в����ء�
 * ���������д newTaskFor �������Է��� FutureTask ֮��� RunnableFuture ʵ�֡� 
 * @author heyw
 *
 */
public abstract class AbstractExecutorService implements ExecutorService {
	public Future<?> submit(Runnable r) {
	  if(r==null)  throw new  NullPointerException();
	   RunnableFuture<Void> ftask=newTaskFor(r,null);
		execute(ftask);//ִ������ThreadPoolExecutor��executor()����
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
     * ͨ��һ����װ��������Runnable�����װ��FutureTask   
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
