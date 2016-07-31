package threadpoolproject;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Executors {
       public static  ExecutorService newCachedThreadPool(){
    	   return new ThreadPoolExecutor(0,Integer.MAX_VALUE,60L,TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
       }
   
       
       /**
        * ����һ��Ĭ�ϵ��̹߳����࣬����ͨ��ͬһ��ThreadGroup group���������߳�
        * ���System.getSecurityManager()���ص�SecurityManager��Ϊ�գ���ʹ�ø�SecurityManager��ȡThreadGroup
        * ����ͨ����ǰ�߳�invoking����getThreadGroup()����ȡThread
        * @return
        */
       public static DefaultThreadFactory getThreadFactory(){
    	   return new DefaultThreadFactory();
       }
       /**
        * ��̬�ڲ������ʹ�þ�̬��Ա����
        * @author Administrator
        *
        */
       static class DefaultThreadFactory   implements  ThreadFactory{
            private static final AtomicInteger poolNumber=new AtomicInteger(1);//���ܸı�
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
        * ��װRunnableΪCallable
        * @param task
        * @param result
        * @return
        */
       public static <T>Callable<T>callable(Runnable task,T result){
    	   if(task==null) throw new NullPointerException();
    	   return new RunnableAdapter<>(task, result);
       }
       
       /**
        * ����������Runnable��װ��Callable
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
