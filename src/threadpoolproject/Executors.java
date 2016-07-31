package threadpoolproject;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Executors {
       public static  ExecutorService newCachedThreadPool(){
    	   return new ThreadPoolExecutor(0,Integer.MAX_VALUE,60L,TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
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
