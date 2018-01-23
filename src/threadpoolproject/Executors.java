package threadpoolproject;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Executors {
	   /**
	    * 创建一个可根据需要创建新线程的线程池，但是在以前构造的线程可用时将重用它们。
	    * 对于执行很多短期异步任务的程序而言，这些线程池通常可提高程序性能。调用 execute 将重用以前构造的线程（如果线程可用）。
	    * 如果现有线程没有可用的，则创建一个新线程并添加到池中。终止并从缓存中移除那些已有 60 秒钟未被使用的线程。
	    * 因此，长时间保持空闲的线程池不会使用任何资源。
	    * 注意，可以使用 ThreadPoolExecutor 构造方法创建具有类似属性但细节不同（例如超时参数）的线程池。 
	    * @return
	    */
       public static  ExecutorService newCachedThreadPool(){
    	   return new ThreadPoolExecutor(0,Integer.MAX_VALUE,60L,TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
       }
       /**
        * 创建一个可根据需要创建新线程的线程池，但是在以前构造的线程可用时将重用它们，并在需要时使用提供的 ThreadFactory 创建新线程。 
        * @param factory
        * @return
        */
       public static ExecutorService newCachedThreadPool(ThreadFactory factory){
		return new ThreadPoolExecutor(0,Integer.MAX_VALUE,60L,TimeUnit.SECONDS,new SynchronousQueue<Runnable>(),factory);
    	   
       }
       public static ExecutorService newFixedThreadPool(int nThreads) {
           return new ThreadPoolExecutor(nThreads, nThreads,  0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
       }

       /**
        * 返回一个默认的线程工厂类，该类通过同一个ThreadGroup group来创建新线程
        * 如果System.getSecurityManager()返回的SecurityManager不为空，则使用该SecurityManager获取ThreadGroup
        * 否则通过当前线程invoking方法getThreadGroup()来获取Thread
        * @return
        */
       public static DefaultThreadFactory getThreadFactory(){
    	   return new DefaultThreadFactory();
       }
       /**
        * 静态内部类才能使用静态成员变量
        * @author Administrator
        *
        */
       static class DefaultThreadFactory   implements  ThreadFactory{
            private static final AtomicInteger poolNumber=new AtomicInteger(1);//不能改变
            private static AtomicInteger threadNumber=new AtomicInteger(1);
            private final ThreadGroup group;
            private final String namePrefix;
         public DefaultThreadFactory(){
        	 SecurityManager s=System.getSecurityManager();
        	 group=(s!=null)?s.getThreadGroup():Thread.currentThread().getThreadGroup();
        	 namePrefix="pool-"+poolNumber.getAndIncrement()+"-thread-";
         }
            
		@Override
		public Thread newThread(Runnable command) {
			Thread t=new Thread(group, command, namePrefix+threadNumber.getAndDecrement(),0);
			if(t.isDaemon()){
				t.setDaemon(false);
			}
			if(t.getPriority()!=Thread.NORM_PRIORITY){
				t.setPriority(Thread.NORM_PRIORITY);
			}
			return t;
		}
       }      
       
       /**
        * 包装Runnable为Callable
        * @param task
        * @param result
        * @return
        */
       public static <T>Callable<T>callable(Runnable task,T result){
    	   if(task==null) throw new NullPointerException();
    	   return new RunnableAdapter<>(task, result);
       }
       
       /**
        * 该类用来将Runnable包装成Callable
        * @author heyw
        *
        * @param <T>
        */
       static final class RunnableAdapter<T>implements Callable<T>{
    	   final Runnable task;
    	   final T result;
    	   public RunnableAdapter(Runnable task,T result){
    		   this.task=task;
    		   this.result=result;
    	   }
    	   public T call(){
    		   task.run();
    		   return result;
    	   }
       }
}
