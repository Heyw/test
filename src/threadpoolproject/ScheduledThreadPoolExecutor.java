package threadpoolproject;

import java.util.AbstractQueue;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


/**
 * ThreadPoolExecutor���������а����ڸ������ӳٺ�����������߶���ִ�����
 * ��Ҫ��������߳�ʱ������Ҫ�� ThreadPoolExecutor ���ж��������Ի���ʱ������Ҫ���� Timer��
 * 
 * ���������ӳٵ������ִ�����������йغ�ʱ���ã����ú��ʱִ����û���κ�ʵʱ��֤��
 * �����ύ���Ƚ��ȳ� (FIFO) ˳����������Щ��������ͬһִ��ʱ�������
 * 
 * һ���ύ��������ִ��ǰ��ȡ������ô�������ִ�н��ᱻѹ�ƣ�Ȼ���⽫���ý����Ĺ۲�ͼ��ӣ���ᵼ�¶�ȡ������������ƵĻ��ա�
 * Ϊ�˱��������������Ҫ����setRemoveOnCancelPolicyΪtrue���⽫ʹ�õ�����ȡ��ʱ�������ӹ����������Ƴ���
 * 
 * ͨ��scheduleAtFixedRate()����scheduleWithFixedDelay�ɹ�ִ�е����񽫲����ص�������ͬ��ִ���ɲ�ͬ���߳�ִ��ʱ��
 * ����ִ�жԺ���ִ�е������ڴ�ɼ��Ե�Ч����
 * 
 * ��Ȼ����̳��� ThreadPoolExecutor�����Ǽ����̳еĵ��������Դ��ಢ�����á�
 * �ر��ǣ���Ϊ����Ϊһ��ʹ�� corePoolSize �̺߳�һ���޽���еĹ̶���С�ĳأ����Ե��� maximumPoolSize û��ʲôЧ���� 


 * @author heyw
 *
 */
public class ScheduledThreadPoolExecutor extends ThreadPoolExecutor implements ScheduledExecutorService {
	/*���ཫThreadPoolExecutorͨ�����·�ʽ���������⻯:
	 * (1)ʹ�ö��Ƶ�����ScheduledFutureTask,��Щ��Ҫ����ȵ�����(�ύ����ʱ����ʹ��ThreadPoolExecutor��submit)
	 * ��������Ϊ��ʱ�����Դ���
	 * (2)ʹ�ö��ƵĶ��У�DelayedWorkeQueue��һ���޽���еı��塣ȱ�����������Լ�corePoolSize��maximumPoolSize��С��ͬ��
	 * �����һЩִ�л��ƣ����TheadPoolExecutor��˵��
	 * (3)֧�ֿɹ�ѡ���run-after-down��������Ḳ��shutdown()�������Ƴ���ȡ������shutdown�����е�������͵��ύ�����ص�
	 * һ��shutdownʱ��ͬ�Ķ��μ���߼�һ���ġ�
	 * (4)�������η����������غ����ã����Ǳ�Ҫ�ģ���Ϊ���಻����������ʽ��дsubmit��������ȡͬ����Ч��������̳߳ؿ����߼�û�а��Ӱ��
	 * */
	/**�����shutdownʱӦ���Ƴ�����ѹ�����������񣬷���false*/
	private volatile boolean continueExistingPeriodicTasksAfterShutDown;
	
	/**�����shutdownʱӦ��ȡ��������������ʱ������false*/
	private volatile boolean exeucteExistingDelayedTasksAfterShutDown=true;
	
	/**���к��룬�������Ƶ��Ƚᣬͬʱ������ڽ�ʱ�ı�֤FIFO*/
    private final AtomicInteger squence=new AtomicInteger(0);
    
    /**���ScheduledFutureTask.cancelӦ�ôӶ������Ƴ�����ôΪtrue*/
    private volatile boolean removeOnCancel=false;
    
	/**
	 * ����ϵͳ��ǰʱ��
	 * @return
	 */
	final long now(){
		return System.nanoTime();
	}
	/**
	 * ����һ������ָ�����Ĵ�С�ĵ��ȳ�
	 * @param corePoolSize
	 */
	public ScheduledThreadPoolExecutor(int corePoolSize) {
		super(corePoolSize, Integer.MAX_VALUE, 0, TimeUnit.NANOSECONDS, new DelayedWorkQueue());
	}
   /**
    * �������ȳأ����г�ʼ����
    * @param corePoolSize
    * @param threadFactory
    */
	public ScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory) {
         super(corePoolSize, Integer.MAX_VALUE, 0, TimeUnit.NANOSECONDS,new DelayedWorkQueue(), threadFactory);
     }
	/**
	 * �������ȳأ����г�ʼ����
	 * @param corePoolSize
	 * @param handler
	 */
	  public ScheduledThreadPoolExecutor(int corePoolSize,RejectedExecutionHandler handler) {
          super(corePoolSize, Integer.MAX_VALUE, 0, TimeUnit.NANOSECONDS,new DelayedWorkQueue(), handler);
       }

	  public ScheduledFuture<?> schedule(Runnable command, long delay,TimeUnit unit) {
		  if(command==null || unit==null) throw new NullPointerException();
		  RunnableScheduledFuture<?>t=decorateTask(command, new ScheduledFutureTask<Void>(command,null,triggerTime(delay, unit)));
		  delayedExecute(t);
		  return t;
	}

	@Override
	public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay,
			TimeUnit unit) {
		 if(callable==null || unit==null) throw new NullPointerException();
		 RunnableScheduledFuture t=decorateTask(callable,new ScheduledFutureTask<V>(callable,triggerTime(delay,unit)));
		 delayedExecute(t);
		return t;
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
			long initDelay, long period, TimeUnit unit) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable commmand,
			long initDelay, long dealy, TimeUnit unit) {
		// TODO Auto-generated method stub
		return null;
	}
	
	/**����һ����ʱ�����Ĵ���ʱ��*/
	private long triggerTime(long delay ,TimeUnit unit){
		return triggerTime(unit.toNanos(delay<0?0:delay));
	}
	/**
	 * ����һ����ʱ�����Ĵ���ʱ��
	 * @param delay
	 * @return
	 */
	long triggerTime(long delay){
		return now()+(delay<(Long.MAX_VALUE>>1)? delay:overflowFree(delay));
	}
	/**
	 * �����������е���ʱֵ��������Long.MAXVALUE���ڣ�Ϊ�˱�����compareTOʱ����overflow.
	 * �÷����������ã�����и�����Ҫ���ӣ����ǻ�û�У�ͬʱ��Щ������е��������ʱֵΪLong.MAX_VALUE
	 * @param delay
	 * @return
	 */
	private long overflowFree(long delay){
		Delayed head=(Delayed)super.getQueue().peek();
		if(head!=null){
			long headDelay=head.getDelay(TimeUnit.NANOSECONDS);
			if(headDelay<0&&(delay-headDelay<0)){
				delay=Long.MAX_VALUE+headDelay;
			}
		}
		return delay;
	}
	private class ScheduledFutureTask<V>extends FutureTask<V>implements RunnableScheduledFuture<V>{
		/** ���к����������ƽ���֧��FIFO */
		private final long sequenceNumber;
		/**������Ϊ��λ���������ִ��ʱ��*/
		private long time;
		/**
		 *�ظ�ִ����������ڣ�Ϊ��������ִ������Ϊ�̶�Ƶ�ʣ�Ϊ��������ִ������Ϊ�̶�����ʱ��Ϊ����䲻���ظ�ִ��
		 */
		private final long period;
		/**��ʱ���е�ָ����Ϊ��֧�ָ����ȡ������*/
		int headIndex;
		/**ͨ��reExecutorPeriodic()�������½������ִ�е�ʵ������*/
		RunnableScheduledFuture<V>outerTask=this;
		
		
		/**
		 * ����һ����ʼ����ʱ���һ����ִ������
		 * @param command
		 * @param result
		 * @param nanos
		 */
		ScheduledFutureTask(Runnable command,V result,long nanos){
			super(command,result);
			this.sequenceNumber=squence.get();
			this.time=nanos;
			this.period=0;
					
		}
		/**
		 * ����һ����ʼ����ʱ��Ϊnanos���������ظ�ִ������
		 * @param command
		 * @param result
		 * @param nanos
		 * @param period
		 */
		ScheduledFutureTask(Runnable command,V result,long nanos,long period){
			super(command,result);
			this.sequenceNumber=squence.get();
			this.time=nanos;
			this.period=period;
		}
		
		/**
		 * ������������ʱ���һ��������
		 * @param command
		 * @param nanos
		 */
		ScheduledFutureTask(Callable<V> command,long nanos){
			super(command);
			this.sequenceNumber=squence.get();
			this.time=nanos;
			this.period=0;
					
		}
		
		public long getDelay(TimeUnit unit) {
			return unit.convert(time-now(), TimeUnit.NANOSECONDS);//����ʣ�������ʱ��
		}
		
		public int compareTo(Delayed o) {
		  if(this==o){
			  return 0;
		  }
		  if(o instanceof ScheduledFutureTask){
			  ScheduledFutureTask<?>x=(ScheduledFutureTask<?>)o;
			  if(time>x.time)
				  return 1;
			  else if(time<x.time)
				  return -1;
			  else if(sequenceNumber<x.sequenceNumber)
				  return -1;
			  else
				  return 1;
		  }
		  long d=(getDelay(TimeUnit.NANOSECONDS)-o.getDelay(TimeUnit.NANOSECONDS));
			return d==0?0:(d>0?1:-1);
		}
		
		public boolean isPeriodic() {
			return period!=0;
		}
		/**Ϊ����������������һ��ִ��ʱ��*/
		private void setNextRunTime(){
			long p=period;
			if(p>0)
				time+=p;
			else
				time=triggerTime(-p);
		}
		
		public boolean cancel(boolean mayInterruptIfRunning){
			boolean canceled=super.cancel(mayInterruptIfRunning);
			if(canceled&&removeOnCancel&&headIndex>=0){
				remove(this);
			}
			return canceled;
		}
		
		/**��дFutureTask��run()�������Ա����ö��л������½������*/
		public void run(){
			boolean periodic=isPeriodic();
			if(!canRunInCurrentState(periodic)){
				cancel(true);
			}else if(!periodic)
				ScheduledFutureTask.super.run();
			else if(ScheduledFutureTask.super.runAndReset()){
				setNextRunTime();
				reExecutePeriodic(outerTask);
			}
		}
	}
	
	/**����ܹ�ִ��һ���������������Լ�run-after-shutdown����������Ļ�����ô����true*/
	boolean canRunInCurrentState(boolean periodic){
		return isRunningOrShutDown(periodic?
				continueExistingPeriodicTasksAfterShutDown:exeucteExistingDelayedTasksAfterShutDown);
	}
	
	/**
	 * ��ʱ�������Ҫִ�з���������̳߳��Ѿ�shutdown����ô�ܾ������񣬷�����Ӹ����񣬲�����һ���߳�
	 * �����Ҫ�Ļ���ִ�и���������������ڱ����ʱ���̳߳��Ѿ��رգ���ô����״̬��run-after-shutdown��Ҫ�����Ƴ���ȡ��������
	 * @param task
	 */
	private  void delayedExecute(RunnableScheduledFuture<?>task){
		if(isShutDown())
			reject(task);
		else{
			super.getQueue().add(task);
			if(isShutDown()&&!continueExistingPeriodicTasksAfterShutDown&&remove(task)){
				task.cancel(false);
			}else{
				ensurePrestart();
			}
		}
	}
	/**��һ������������������ӣ����ǵ�ǰ����״̬����ֹ����delayedExecute��ͬ���뷨�������Ƴ���������Ǿܾ�����*/
	void reExecutePeriodic(RunnableScheduledFuture<?>task){
		if(canRunInCurrentState(true)){
			super.getQueue().add(task);
			if(!canRunInCurrentState(true)&&remove(task))
				task.cancel(true);
			else
				ensurePrestart();
		}
			
	}
	/**
	 * ��ȡ�й��ڴ�ִ�г����� shutdown ��������Ƿ����ִ�������ӳ�����(ExecuteExistingDelayedTasks)�Ĳ��ԡ�
	 * ����������£�����ִ�� shutdownNow ʱ��������ִ�г����ѹر�ʱ����������Ϊ false �����ֹ��Щ���񡣴�ֵĬ��Ϊ true�� 
	 * @return
	 */
	 public boolean getExecutorExistingDelayedTasksAfterShutdownPolicy(){
		 return exeucteExistingDelayedTasksAfterShutDown;
	 }
	 
	 /**
	  * ��ȡ�й��ڴ�ִ�г����� shutdown ������¡��Ƿ����ִ�����ж�������Ĳ��ԡ�
	  * ����������£�����ִ�� shutdownNow ʱ��������ִ�г����ѹر�ʱ����������Ϊ false �����ֹ��Щ���񡣴�ֵĬ��Ϊ false��
	  */
	 public boolean getContinueExistingDelayedTasksAfterShutdownPolicy(){
		 return continueExistingPeriodicTasksAfterShutDown;
	 }
	/**����һ�����ͷ��������ͷ��������ż�ͨ������ڷ�����ǰ���ʾ���õķ����Ƿ��ͷ�����������������з��ͣ���ô���Ƿ��ͷ���
	 * �޸Ļ����滻����ִ��runnable��task��������Ա�������д��ʵ�ֿ��Թ����ڲ�task���ࡣ����Ĭ�Ϸ���task
	 * @param runnabl
	 * @param task
	 * @return ���ض�����һ�������࣬�����ࣺ�����ż�ͨ���������������
	 */
	protected <V> RunnableScheduledFuture<V> decorateTask(Runnable runnabl,RunnableScheduledFuture<V>task){
		return task;
	}
	
	/**����һ�����ͷ��������ͷ��������ż�ͨ������ڷ�����ǰ���ʾ���õķ����Ƿ��ͷ�����������������з��ͣ���ô���Ƿ��ͷ���
	 * �޸Ļ����滻����ִ��callable��task��������Ա�������д��ʵ�ֿ��Թ����ڲ�task���ࡣ����Ĭ�Ϸ���task
	 * @param callable
	 * @param task
	 * @return ���ض�����һ�������࣬�����ࣺ�����ż�ͨ���������������
	 */
	protected <V>RunnableScheduledFuture<V>decorateTask(Callable<V>callable,RunnableScheduledFuture<V>task){
		return task;
	}
	/**
	 * ר�ŵ���ʱ���У����뱻����ΪBlockingQueue����ʹ���ܳ���RunnableScheduledFutures
	 * @author heyw
	 *
	 */
    static class DelayedWorkQueue extends AbstractQueue<Runnable>implements BlockingQueue<Runnable>{
    	/*
    	 * DelayedWorkQueue�ǻ��ڶ����ݽṹ�ģ������ЩDelayQueue��PriorityQueue�����ƣ�ֻ��ÿ��ScheduledFutureTask������heap�����м�¼�Լ�������(heapIndex);
    	 * ������Ѱ�ҹ���cancellation��task�ı�Ҫ���Ӷ�����ļӿ��Ƴ����������ٶ�(��o(n)�½���o(log(n))),�������������֮ǰ�ȴ�Ԫ��������top��ɵ�����������
    	 * ������Ϊ���ǳ��е�RunnablScheduledFutuer���ܲ���ScheduledFutureTask����˲���ȷ�����ǳ��е���Щָ�������ã�����������£����ǽ�����ȥ��������������
    	 * 
    	 * ���е�heap�����������¼ָ��ı仯����Ҫ��shiftup��shiftdown�����Ƴ�ʱ��heapIndex��������Ϊ-1��ע��ScheduledFutureTask�ڶ�����������һ�Σ�
    	 * �������������͵�task��һ����ȷ��������Ǳ�heapIndexΨһʶ��ġ�
    	 * 
    	 * �����ݽṹ���ƶ������ṹ������index�����������ӽڵ�С�����ӽڵ㣬���ڵ�С���ӽڵ�
    	 * ���ڵ���ӽڵ������¹�ϵ:�ӽڵ� index=k����ô���ڵ�indexΪ���ڵ�index��ȥ1������2������ȡ���� index=(k-1)/2
    	 * ���ڵ�index=k,��ô�ӽڵ�index=2*k+1
    	 * shiftUp��shiftDown�������Ǹ�������߼������ӿ��Ƴ���
    	 */
        private final static int INIT_CAPACITY=16;
        private RunnableScheduledFuture[] queue=new RunnableScheduledFuture[INIT_CAPACITY];
        private final ReentrantLock lock=new ReentrantLock();
        private  int size=0;
       
        /**
         * leader��ָ��Ϊ�ڶ���ͷ���ȴ�������̡߳�Leader_followerģ�͵ı���������С������Ҫ�Ķ�ʱ�ȴ���
         * ��ֻ�ȴ���һ��������ȥ����ʱ���������߳���ʱ�������ڵȴ����ڴ�take()����poll()��������ʱleader��Ҫ֪ͨһЩ�������̣߳�
         * ������Щ�����߳��Ѿ���ʱ��Ϊ��leader�����ۺ�ʱ����ͷ��㱻һ�����и�������ʱ���taskȡ����ͬʱleader�����Ա���������Ϊnull����Чʱ��
         * һЩ�����ȴ����̣߳�������ǰ��leader�����ᱻ֪ͨ����˵ȴ��̱߳����ڵȴ�ʱ׼���û�ȡ����ʧȥleadership
         */
        private Thread leader=null;
        
        /**
         *����֪ͨ��һ���������ڶ���ͷ������ʹ�û���һ���߳̽���Ϊleaderʱ
         */
        private final Condition available=lock.newCondition();
        
        /**
         * ���f��ScheduledFutureTask,��ô������heapIndex
         * @param f
         * @param idx
         */
        private void setIndex(RunnableScheduledFuture f,int idx){
        	if(f instanceof ScheduledFutureTask){
        		((ScheduledFutureTask)f).headIndex=idx;
        	}
        }
        
        /**
         * ���¼��������e��������ʱ�������ʵ�λ�ã�k����һ��ʼ���е�size��Ҳ���ǴӶ���β����������ƥ��
         * @param k
         * @param e
         */
        private void shiftUp(int k,RunnableScheduledFuture key){
        	while(k>0){
        		int parent=(k-1)>>>1;//����k��Ѱ���丸�ڵ��ָ��
        		RunnableScheduledFuture parentnode=queue[parent];
        		if(key.compareTo(parentnode)>=0){//�������ڵ�������ʱ���ڸ��ڵ�������ʱ����ô���ڶ���β����Ӹø�����
        			break;
        		}
        		queue[k]=parentnode;//�������ڵ�������ʱС�ڸ��ڵ�������ʱ���򽫸��ڵ���ӵ�����index=k��
        		setIndex(parentnode,k);
        		k=parent;
        	}
        	queue[k]=key;
        	setIndex(key,k);//Ϊ�ýڵ�����ָ��
        }
        
        /**
         * �������topλ�õ�Ԫ�������ƶ�������������ʱ��λ�ã�ͨ�����Ϻ������ӽڵ���ʱ�Ƚϣ�ֻ���ڳ�����ʱ�ſɵ���
         * @param k
         * @param key
         */
        private void shiftDown(int k,RunnableScheduledFuture key){
        	int half=size>>>1;//halfΪsize��һ�룬��Ϊ���ݶ����������ʣ�λ����������һ���û���ӽڵ㣬С��һ��Ĳ����ӽڵ�
        	while(k<half){
        		int childIndex=(k<<1)+1;//Ѱ�����ӽڵ�=2*k+1
        		RunnableScheduledFuture c=queue[childIndex];
        		int rightchildIndex=childIndex+1;//���ӽڵ�����
        		if(rightchildIndex<size&&c.compareTo(queue[rightchildIndex])>0){//������ӽڵ�������ʱ�������ӽڵ�������ʱ����ô�滻�����ӽڵ�����
        			c=queue[childIndex=rightchildIndex];
        		}
        		if(key.compareTo(c)<=0){//����ƶ��Ľڵ���ʱ��С���ӽڵ���ʱ����ô����������λ�ã��˳�ѭ��
        			break;
        		}
        		queue[k]=c;//���ӽڵ����Ƶ���ǰ������
        		setIndex(c, k);
        		k=childIndex;//�����������ӽڵ�����������ѭ��
        	}
        	queue[k]=key;
        	setIndex(key, k);
        }
        
        /**�������ö�����Ĵ�С�����Ӱٷ�֮��ʮ*/
        private void grow(){
        	int oldCapacity=queue.length;
        	int newCapacity=oldCapacity+oldCapacity>>1;//����ٷ�֮��ʮ
        	if(newCapacity<0){//����Integer.Max_Value
        		newCapacity=Integer.MAX_VALUE;
        	}
        	queue=Arrays.copyOf(queue, newCapacity);//������queue���Ƶ�ָ��newCapaciyt�Ĵ�С������
        }
        
        /**����ָ�������������е���������������ڣ��򷵻�-1*/
        private int indexOf(Object x){
        	if(x!=null){//����Ҫ�ж��Ƿ�Ϊ��
        		if(x instanceof ScheduledFutureTask){
        			/*���Ǽ��:x�����������һ���̳߳��е�ScheduledFutureTask
        			 * */
        			int i=((ScheduledFutureTask)x).headIndex;
        			if(i>=0&&i<size&&queue[i]==x){
        				return i;
        			}
        		}else{
        			for(int i=0;i<queue.length;i++){
        				if(x.equals(queue[i])){
        					return i;
        				}
        			}
        		}
        	}
        	return -1;
        }
        
        public boolean contains(Object o) {
			final ReentrantLock lock=this.lock;
			lock.lock();
			try{
				return indexOf(o)!=-1;
			}finally{
				lock.unlock();
			}
		}
        
    	public boolean isEmpty() {
			return size==0;
		}
    	
		public boolean remove(Object x) {
			final ReentrantLock lock=this.lock;
			lock.lock();
			try{
				int i=indexOf(x);
				if(i<0)
					return false;
				int s=--size;
				setIndex(queue[i], -1);//�Ƴ��ö���
				RunnableScheduledFuture replacement=queue[s];//���Խ�����β��Ԫ�ط����Ƴ������λ���ϣ������ڲ���λ���ϣ���Ҫ������������ʱʱ��
				if(s!=i){
					shiftDown(i, replacement);//�����ƶ������ʵ�λ�ã�ע�⾭���÷�����i��ֵ���䣬��Ϊ��������
					if(queue[i]==replacement){//���û���ƶ�����ô������Ѱ�Һ��ʵ�λ��
						shiftUp(i, replacement);
					}
				}
				return true;
			}finally{
				lock.unlock();
			}
		}

		public Runnable element() {
			return null;
		}

		public RunnableScheduledFuture peek() {
			final ReentrantLock lock=this.lock;
			lock.lock();
			try{
				return queue[0];
			}finally{
				lock.unlock();
			}
		}

		public boolean offer(Runnable e) {
			if(e==null) throw new NullPointerException();
			RunnableScheduledFuture x=(RunnableScheduledFuture)e;
			final ReentrantLock lock=this.lock;
			lock.lock();
			try{
				int s=size;
				if(s>=queue.length)//���size���ڵ��ڶ��еĳ��ȣ���ô�����Ӷ���
					grow();
				size=s+1;
				if(s==0){
					queue[0]=x;
				     setIndex(x, s);
				}else{
					shiftUp(s, x);
				}
				if(queue[0]==x){//�������ͷ������ˣ���ô����leaderΪnull,Ȼ��֪ͨ�����ȴ��߳�
					leader=null;
					available.signal();
				}
			}finally{
				lock.unlock();
			}
			return true;
		}
		
		public int size() {
			return 0;
		}
		public boolean add(Runnable e) {
			return offer(e);
		}

		public void put(Runnable e) throws InterruptedException {
			offer(e);
		}

		public boolean offer(Runnable e, long timeout, TimeUnit uint){
			return offer(e);
		}
		
		/**
		 * Ϊpoll()����take()���м��ء������е�һ��Ԫ�������һ��Ԫ�������Ȼ�󲻶�����Ѱ�Һ���λ�á�ֻ���ڼ���ʱ����
		 * @param f ��Ҫ�Ƴ�������
		 * @return f 
		 */
		private RunnableScheduledFuture finishPoll(RunnableScheduledFuture f){
			int s=--size;
			RunnableScheduledFuture x=queue[s];//��ȡ���һ��Ԫ��
			queue[s]=null;
			if(s!=0)
				shiftDown(0, x);
			setIndex(f, -1);//����Ҫɾ����f����Ϊ-1��
			return f;
		}
		
		public RunnableScheduledFuture take() throws InterruptedException {
			final ReentrantLock lock=this.lock;
			lock.lockInterruptibly();
			try{
				for(;;){
					RunnableScheduledFuture firstTask=queue[0];//��ȡ��һ������
					long delay=firstTask.getDelay(TimeUnit.NANOSECONDS);
					if(delay<=0){
						return finishPoll(firstTask);
					}else if(leader!=null){//���delay�������ң�leader�̲߳�Ϊnull����ô�ȴ�
						available.await();
					}else{
						Thread thisThread=Thread.currentThread();
						leader=thisThread;
						try{
							available.awaitNanos(delay);//�ȴ���ʱʱ��
						}finally{
							if(leader==thisThread){
								leader=null;
								available.signal();
							}
						}
					}
				}
			}finally{
				if(leader==null&&queue[0]!=null){
					available.signal();
				}
				lock.unlock();
			}
		
		}
		public RunnableScheduledFuture poll() {
			final ReentrantLock lock=this.lock;
			lock.lock();
			try{
				  RunnableScheduledFuture first=queue[0];
				  if(first==null || first.getDelay(TimeUnit.NANOSECONDS)>0){
					  return null;
				  }else{
					  return finishPoll(first);
				  }
			}finally{
				lock.unlock();
			}
		}
		public RunnableScheduledFuture poll(long timeout, TimeUnit unit)throws InterruptedException {
			long nanos=unit.toNanos(timeout);//���ȴ�ʱ��ת��Ϊ����
			final ReentrantLock lock=this.lock;
			lock.lockInterruptibly();
			try{
				for(;;){
				    RunnableScheduledFuture first=queue[0];
				    if(first==null){
				    	if(nanos<=0){
				    		return null;
				    	}else{
				    		available.awaitNanos(nanos);
				    	}
				    }else{
				    	long delay=first.getDelay(TimeUnit.NANOSECONDS);
				    	if(delay<=0){
				    		return finishPoll(first);
				    	}
				    	if(nanos<=0)
				    		return null;
				    	if(nanos<delay || leader!=null){
				    		nanos=available.awaitNanos(nanos);//����ʣ��ȴ�ʱ��=delay-�ȴ�ʱ��Ĺ���ֵ
				         }else{
				    	Thread thread=Thread.currentThread();
				    	leader=thread;
				    	try{
				    		long timeleft=available.awaitNanos(delay);//���ص���dealy-�ȴ�ʱ��Ĺ���ֵ
				    		nanos-=delay-timeleft;//(nanosʣ��ʱ��)
				    		}finally{
				    			if(thread==leader){
				    				leader=null;
				    			}
				    		}
				    	}
				    }
				}
			}finally{
				if(leader==null &&queue[0]!=null)
					available.signal();
				lock.unlock();
			}
		}
		
		/**
		 * �Ƴ�����ͷ��Ԫ�أ�ֻ�е���Ԫ���Ѿ�����ʱ�ŵ��á�����÷���ֻ���ڼ����������ʹ��
		 * @return
		 */
		private RunnableScheduledFuture pollExpired(){
			RunnableScheduledFuture first=queue[0];
			if(first==null||first.getDelay(TimeUnit.NANOSECONDS)>0)
				return null;
			return finishPoll(first);
		}
		
		public Object[] toArray() {
		   final ReentrantLock lock=this.lock;
		   lock.lock();
		   try{
			   return Arrays.copyOf(queue, size,Object[].class);
		   }finally{
			   lock.unlock();
		   }
		}

		@SuppressWarnings("unchecked")
		public <T> T[] toArray(T[] a) {
			final ReentrantLock lock=this.lock;
			lock.lock();
			try{
				if(a.length<size)
					return (T[])Arrays.copyOf(queue,size,a.getClass());
				System.arraycopy(queue, 0, a, 0, size);
				if(a.length>size)
					a[size]=null;
				return (T[])a;
			}finally{
				lock.unlock();
			}
		}

		
		public void clear() {
			final ReentrantLock lock=this.lock;
			lock.lock();
			try{
				for(int i=0;i<size;i++){
					RunnableScheduledFuture t=queue[i];
					queue[i]=null;
					setIndex(t, -1);
				}
				size=0;
			}finally{
				lock.unlock();
			}
		}

		public int remainingCapacity() {
			return Integer.MAX_VALUE;
		}


		public int drainTo(Collection<? super Runnable> c) {
			if(c==null) throw new NullPointerException();
			if(c==this) throw new IllegalArgumentException();
			final ReentrantLock lock=this.lock;
			lock.lock();
			try{
				int n=0;
				RunnableScheduledFuture first;
				while((first=pollExpired())!=null){
					c.add(first);
					n++;
				}
				return n;
			}finally{
				lock.unlock();
			}
			
		}

		public int drainTo(Collection<? super Runnable> c, int maxnum) {
			if(c==null) throw new NullPointerException();
			if(c==this)throw new IllegalArgumentException();
			if(maxnum<=0)return 0;
			final ReentrantLock lock=this.lock;
			lock.lock();
			try{
				int n=0;
				RunnableScheduledFuture first;
				while(n<maxnum&&((first=pollExpired())!=null)){
					c.add(first);
					n++;
				}
				return n;
			}finally{
				lock.unlock();
			}
		}

		public Iterator<Runnable> iterator() {
			return null;
		}
      private  class Itrr implements Iterator<Runnable>{
    	   final RunnableScheduledFuture[] array;
    	   int cursor=0;
    	   int lastRet=-1;
	   public Itrr(RunnableScheduledFuture[] arrs){
		   this.array=arrs;
	   }
		public boolean hasNext() {
			return cursor<array.length;
		}
		@Override
		public Runnable next() {
			if(cursor>=array.length) throw new NoSuchElementException();
			lastRet=cursor++;
			return array[lastRet];
		}
		public void remove() {
           if(lastRet<0)
        	   throw new IllegalStateException();
           DelayedWorkQueue.this.remove(array[lastRet]);
           lastRet=-1;
		}
       }
    }
}
