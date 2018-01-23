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
 * ThreadPoolExecutor，它可另行安排在给定的延迟后运行命令，或者定期执行命令。
 * 需要多个辅助线程时，或者要求 ThreadPoolExecutor 具有额外的灵活性或功能时，此类要优于 Timer。
 * 
 * 旦启用已延迟的任务就执行它，但是有关何时启用，启用后何时执行则没有任何实时保证。
 * 按照提交的先进先出 (FIFO) 顺序来启用那些被安排在同一执行时间的任务。
 * 
 * 一个提交的任务在执行前被取消，那么该任务的执行将会被压制，然而这将启用将来的观察和监视，这会导致对取消任务的无限制的回收。
 * 为了避免这种情况，需要设置setRemoveOnCancelPolicy为true，这将使得当任务取消时会立即从工作队列中移除。
 * 
 * 通过scheduleAtFixedRate()或者scheduleWithFixedDelay成功执行的任务将不会重叠。当不同的执行由不同的线程执行时，
 * 优先执行对后面执行的有着内存可见性的效果。
 * 
 * 虽然此类继承自 ThreadPoolExecutor，但是几个继承的调整方法对此类并无作用。
 * 特别是，因为它作为一个使用 corePoolSize 线程和一个无界队列的固定大小的池，所以调整 maximumPoolSize 没有什么效果。 


 * @author heyw
 *
 */
public class ScheduledThreadPoolExecutor extends ThreadPoolExecutor implements ScheduledExecutorService {
	/*该类将ThreadPoolExecutor通过如下方式进行了特殊化:
	 * (1)使用定制的任务，ScheduledFutureTask,那些不要求调度的任务(提交任务时，将使用ThreadPoolExecutor的submit)
	 * 将会设置为延时零来对待；
	 * (2)使用定制的队列，DelayedWorkeQueue，一种无界队列的变体。缺少容量限制以及corePoolSize和maximumPoolSize大小相同，
	 * 将会简化一些执行机制，相对TheadPoolExecutor来说。
	 * (3)支持可供选择的run-after-down参数，这会覆盖shutdown()方法来移除和取消不再shutdown后运行的任务，这和当提交任务重叠
	 * 一个shutdown时不同的二次检查逻辑一样的。
	 * (4)任务修饰方法允许拦截和设置，这是必要的，因为子类不能以其他方式重写submit方法来获取同样的效果，这对线程池控制逻辑没有半点影响
	 * */
	/**如果在shutdown时应该移除或者压制周期性任务，返回false*/
	private volatile boolean continueExistingPeriodicTasksAfterShutDown;
	
	/**如果在shutdown时应该取消非周期性任务时，返回false*/
	private volatile boolean exeucteExistingDelayedTasksAfterShutDown=true;
	
	/**序列号码，用来打破调度结，同时处于入口结时的保证FIFO*/
    private final AtomicInteger squence=new AtomicInteger(0);
    
    /**如果ScheduledFutureTask.cancel应该从队列中移除，那么为true*/
    private volatile boolean removeOnCancel=false;
    
	/**
	 * 返回系统当前时间
	 * @return
	 */
	final long now(){
		return System.nanoTime();
	}
	/**
	 * 创建一个带有指定核心大小的调度池
	 * @param corePoolSize
	 */
	public ScheduledThreadPoolExecutor(int corePoolSize) {
		super(corePoolSize, Integer.MAX_VALUE, 0, TimeUnit.NANOSECONDS, new DelayedWorkQueue());
	}
   /**
    * 创建调度池，带有初始参数
    * @param corePoolSize
    * @param threadFactory
    */
	public ScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory) {
         super(corePoolSize, Integer.MAX_VALUE, 0, TimeUnit.NANOSECONDS,new DelayedWorkQueue(), threadFactory);
     }
	/**
	 * 创建调度池，带有初始参数
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
	
	/**返回一个延时动作的触发时间*/
	private long triggerTime(long delay ,TimeUnit unit){
		return triggerTime(unit.toNanos(delay<0?0:delay));
	}
	/**
	 * 返回一个延时动作的触发时间
	 * @param delay
	 * @return
	 */
	long triggerTime(long delay){
		return now()+(delay<(Long.MAX_VALUE>>1)? delay:overflowFree(delay));
	}
	/**
	 * 将队列中所有的延时值都限制在Long.MAXVALUE以内，为了避免在compareTO时产生overflow.
	 * 该方法将被调用，如果有个任务将要出队，但是还没有，同时有些进入队列的任务的延时值为Long.MAX_VALUE
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
		/** 序列号码用来打破结来支持FIFO */
		private final long sequenceNumber;
		/**以纳秒为单位的任务初次执行时间*/
		private long time;
		/**
		 *重复执行任务的周期：为正数代表执行任务为固定频率，为负数代表执行任务为固定的延时，为零待变不会重复执行
		 */
		private final long period;
		/**延时队列的指数，为了支持更快的取消操作*/
		int headIndex;
		/**通过reExecutorPeriodic()方法重新进入队列执行的实际任务*/
		RunnableScheduledFuture<V>outerTask=this;
		
		
		/**
		 * 创建一个初始启动时间的一次性执行任务
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
		 * 创建一个初始启动时间为nanos的周期性重复执行任务
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
		 * 创建给定纳秒时间的一次性任务
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
			return unit.convert(time-now(), TimeUnit.NANOSECONDS);//返回剩余的启动时间
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
		/**为周期性任务设置下一次执行时间*/
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
		
		/**重写FutureTask的run()方法，以便重置队列或者重新进入队列*/
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
	
	/**如果能够执行一个给定运行条件以及run-after-shutdown参数的任务的话，那么返回true*/
	boolean canRunInCurrentState(boolean periodic){
		return isRunningOrShutDown(periodic?
				continueExistingPeriodicTasksAfterShutDown:exeucteExistingDelayedTasksAfterShutDown);
	}
	
	/**
	 * 延时任务的主要执行方法。如果线程池已经shutdown，那么拒绝该任务，否则添加该任务，并启动一个线程
	 * 如果必要的话，执行该任务。如果任务正在被添加时，线程池已经关闭，那么根据状态和run-after-shutdown的要求来移除和取消该任务
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
	/**将一个周期性任务重新入队，除非当前运行状态的阻止，与delayedExecute相同的想法，除了移除任务而不是拒绝任务*/
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
	 * 获取有关在此执行程序已 shutdown 的情况下是否继续执行现有延迟任务(ExecuteExistingDelayedTasks)的策略。
	 * 在这种情况下，仅在执行 shutdownNow 时，或者在执行程序已关闭时将策略设置为 false 后才终止这些任务。此值默认为 true。 
	 * @return
	 */
	 public boolean getExecutorExistingDelayedTasksAfterShutdownPolicy(){
		 return exeucteExistingDelayedTasksAfterShutDown;
	 }
	 
	 /**
	  * 获取有关在此执行程序已 shutdown 的情况下、是否继续执行现有定期任务的策略。
	  * 在这种情况下，仅在执行 shutdownNow 时，或者在执行程序已关闭时将策略设置为 false 后才终止这些任务。此值默认为 false。
	  */
	 public boolean getContinueExistingDelayedTasksAfterShutdownPolicy(){
		 return continueExistingPeriodicTasksAfterShutDown;
	 }
	/**这是一个泛型方法，泛型方法尖括号加通配符放在方法名前面表示调用的方法是泛型方法，如果方法参数有泛型，那么就是泛型方法
	 * 修改或者替换用来执行runnable的task。该类可以被子类重写来实现可以管理内部task的类。该类默认返回task
	 * @param runnabl
	 * @param task
	 * @return 返回对象是一个泛型类，泛型类：尖括号加通配符放在类名后面
	 */
	protected <V> RunnableScheduledFuture<V> decorateTask(Runnable runnabl,RunnableScheduledFuture<V>task){
		return task;
	}
	
	/**这是一个泛型方法，泛型方法尖括号加通配符放在方法名前面表示调用的方法是泛型方法，如果方法参数有泛型，那么就是泛型方法
	 * 修改或者替换用来执行callable的task。该类可以被子类重写来实现可以管理内部task的类。该类默认返回task
	 * @param callable
	 * @param task
	 * @return 返回对象是一个泛型类，泛型类：尖括号加通配符放在类名后面
	 */
	protected <V>RunnableScheduledFuture<V>decorateTask(Callable<V>callable,RunnableScheduledFuture<V>task){
		return task;
	}
	/**
	 * 专门的延时队列，必须被修饰为BlockingQueue，即使它能持有RunnableScheduledFutures
	 * @author heyw
	 *
	 */
    static class DelayedWorkQueue extends AbstractQueue<Runnable>implements BlockingQueue<Runnable>{
    	/*
    	 * DelayedWorkQueue是基于堆数据结构的，这和那些DelayQueue和PriorityQueue很相似，只是每个ScheduledFutureTask都会在heap数组中记录自己的索引(heapIndex);
    	 * 这会估计寻找关于cancellation的task的必要，从而极大的加快移除队列任务速度(从o(n)下降到o(log(n))),减少由于在清楚之前等待元素上升到top造成的垃圾滞留；
    	 * 但是因为我们持有的RunnablScheduledFutuer可能不是ScheduledFutureTask，因此不能确保我们持有的这些指标是有用，在这种情况下，我们将返回去进行线性搜索；
    	 * 
    	 * 所有的heap操作都必须记录指标的变化，主要是shiftup和shiftdown。当移除时，heapIndex将被设置为-1。注意ScheduledFutureTask在队列中最多出现一次（
    	 * 但对与其他类型的task不一定正确），因此是被heapIndex唯一识别的。
    	 * 
    	 * 堆数据结构类似二叉树结构，根据index进行排序，左子节点小于右子节点，父节点小于子节点
    	 * 父节点和子节点有如下关系:子节点 index=k，那么父节点index为父节点index减去1，除以2，向下取整即 index=(k-1)/2
    	 * 父节点index=k,那么子节点index=2*k+1
    	 * shiftUp和shiftDown方法就是根据这个逻辑，来加快移除的
    	 */
        private final static int INIT_CAPACITY=16;
        private RunnableScheduledFuture[] queue=new RunnableScheduledFuture[INIT_CAPACITY];
        private final ReentrantLock lock=new ReentrantLock();
        private  int size=0;
       
        /**
         * leader被指定为在队列头部等待任务的线程。Leader_follower模型的变体用于最小化不必要的定时等待。
         * 它只等待下一个即将过去的延时，而其他线程延时则无限期等待。在从take()或这poll()方法返回时leader需要通知一些其他的线程，
         * 除非这些其他线程已经暂时成为了leader。无论何时队列头结点被一个具有更早期望时间的task取代，同时leader的属性被重新设置为null而无效时，
         * 一些其他等待的线程，除开当前的leader，将会被通知。因此等待线程必须在等待时准备好获取或者失去leadership
         */
        private Thread leader=null;
        
        /**
         *条件通知当一个新任务在队列头部可以使用或者一个线程将成为leader时
         */
        private final Condition available=lock.newCondition();
        
        /**
         * 如果f是ScheduledFutureTask,那么设置其heapIndex
         * @param f
         * @param idx
         */
        private void setIndex(RunnableScheduledFuture f,int idx){
        	if(f instanceof ScheduledFutureTask){
        		((ScheduledFutureTask)f).headIndex=idx;
        	}
        }
        
        /**
         * 将新加入的任务e，根据延时方法合适的位置，k代表一开始队列的size，也就是从队列尾部不断向上匹配
         * @param k
         * @param e
         */
        private void shiftUp(int k,RunnableScheduledFuture key){
        	while(k>0){
        		int parent=(k-1)>>>1;//根据k，寻找其父节点的指数
        		RunnableScheduledFuture parentnode=queue[parent];
        		if(key.compareTo(parentnode)>=0){//如果新入节点任务延时大于父节点任务延时，那么就在队列尾部添加该该任务
        			break;
        		}
        		queue[k]=parentnode;//如果新入节点任务延时小于父节点任务延时，则将父节点添加到队列index=k处
        		setIndex(parentnode,k);
        		k=parent;
        	}
        	queue[k]=key;
        	setIndex(key,k);//为该节点设置指数
        }
        
        /**
         * 将添加在top位置的元素向下移动到符合自身延时的位置，通过不断和自身子节点延时比较，只有在持有锁时才可调用
         * @param k
         * @param key
         */
        private void shiftDown(int k,RunnableScheduledFuture key){
        	int half=size>>>1;//half为size的一半，因为根据二叉树的性质，位置索引大于一半的没有子节点，小于一半的才有子节点
        	while(k<half){
        		int childIndex=(k<<1)+1;//寻找左子节点=2*k+1
        		RunnableScheduledFuture c=queue[childIndex];
        		int rightchildIndex=childIndex+1;//右子节点索引
        		if(rightchildIndex<size&&c.compareTo(queue[rightchildIndex])>0){//如果左子节点任务延时大于右子节点任务延时，那么替换成右子节点任务
        			c=queue[childIndex=rightchildIndex];
        		}
        		if(key.compareTo(c)<=0){//如果移动的节点延时，小于子节点延时，那么这就是其合适位置，退出循环
        			break;
        		}
        		queue[k]=c;//将子节点上移到当前索引处
        		setIndex(c, k);
        		k=childIndex;//将索引换成子节点索引，重新循环
        	}
        	queue[k]=key;
        	setIndex(key, k);
        }
        
        /**重新设置堆数组的大小，增加百分之五十*/
        private void grow(){
        	int oldCapacity=queue.length;
        	int newCapacity=oldCapacity+oldCapacity>>1;//扩大百分之五十
        	if(newCapacity<0){//超出Integer.Max_Value
        		newCapacity=Integer.MAX_VALUE;
        	}
        	queue=Arrays.copyOf(queue, newCapacity);//将数组queue复制到指定newCapaciyt的大小数组中
        }
        
        /**返回指定对象在数组中的索引，如果不存在，则返回-1*/
        private int indexOf(Object x){
        	if(x!=null){//首先要判断是否为空
        		if(x instanceof ScheduledFutureTask){
        			/*理智检查:x对象可能是另一个线程池中的ScheduledFutureTask
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
				setIndex(queue[i], -1);//移除该对象
				RunnableScheduledFuture replacement=queue[s];//尝试将队列尾部元素放在移除对象的位置上，究竟在不在位置上，还要看替代对象的延时时间
				if(s!=i){
					shiftDown(i, replacement);//向下移动到合适的位置，注意经过该方法后i的值不变，因为副本传递
					if(queue[i]==replacement){//如果没有移动，那么就向上寻找合适的位置
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
				if(s>=queue.length)//如果size大于等于队列的长度，那么就增加队列
					grow();
				size=s+1;
				if(s==0){
					queue[0]=x;
				     setIndex(x, s);
				}else{
					shiftUp(s, x);
				}
				if(queue[0]==x){//如果队列头被替代了，那么设置leader为null,然后通知其他等待线程
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
		 * 为poll()或者take()进行记载。将队列第一个元素用最后一个元素替代，然后不断向下寻找合适位置。只能在加锁时调用
		 * @param f 将要移除的任务
		 * @return f 
		 */
		private RunnableScheduledFuture finishPoll(RunnableScheduledFuture f){
			int s=--size;
			RunnableScheduledFuture x=queue[s];//获取最后一个元素
			queue[s]=null;
			if(s!=0)
				shiftDown(0, x);
			setIndex(f, -1);//设置要删除的f索引为-1；
			return f;
		}
		
		public RunnableScheduledFuture take() throws InterruptedException {
			final ReentrantLock lock=this.lock;
			lock.lockInterruptibly();
			try{
				for(;;){
					RunnableScheduledFuture firstTask=queue[0];//获取第一个任务
					long delay=firstTask.getDelay(TimeUnit.NANOSECONDS);
					if(delay<=0){
						return finishPoll(firstTask);
					}else if(leader!=null){//如果delay大于零且，leader线程不为null，那么等待
						available.await();
					}else{
						Thread thisThread=Thread.currentThread();
						leader=thisThread;
						try{
							available.awaitNanos(delay);//等待延时时间
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
			long nanos=unit.toNanos(timeout);//将等待时间转换为纳秒
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
				    		nanos=available.awaitNanos(nanos);//返回剩余等待时间=delay-等待时间的估计值
				         }else{
				    	Thread thread=Thread.currentThread();
				    	leader=thread;
				    	try{
				    		long timeleft=available.awaitNanos(delay);//返回的是dealy-等待时间的估计值
				    		nanos-=delay-timeleft;//(nanos剩余时间)
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
		 * 移除队列头部元素，只有当该元素已经超期时才调用。另外该方法只能在加锁的情况下使用
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
