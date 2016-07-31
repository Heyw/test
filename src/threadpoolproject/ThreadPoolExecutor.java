package threadpoolproject;

import java.util.HashSet;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ExecutorService，是通过Executors工厂方法配置的，可以使用一些池中线程来执行所有提交的任务；
ThreadPools处理两种不同的问题：当执行大数目异步（asynschronous）任务时，Thread pools通过减少每个任务激活开销可以提高性能；
Thread pools也提供了一种限制和管理包括线程在内的资源的方式，这些资源将会被使用当执行一系列任务时（tasks）。
每个ThreadPoolExecutor都会提供一些基础数据(basic statics),比如任务完成的数目之类
为了能在大范围环境中有效，ThreadPoolExecutor提供了很多可调整和可扩展的钩子(hook)。
然而，程序员被鼓励使用更方便的Executors工厂方法，像Executors.newCachedThreadPool()，无界线程池，可以自动回收线程
Executors.newFixedThreadPool(),固定大小线程池；
Executors.newSingleThreadPool(),单一背景线程池，可以对大多数常见场景(usage scenoarios)进行重新配置(preconfigure settings);
除此之外，当配置和调试该类的时候可以使用以下的指导：

（1）corePoolSize和maximumPoolSize:
线程池可以根据corePoolSize(getCorePoolSize()方法)和maximumPoolSize(getMaximumPoolSize()方法)设置的界限，自动调节线程池大小(getPoolSize());
当方法execute()中提交了一个新task，如果这时线程池中线程数少于corePoolSize那么将会创建一个线程来处理该任务，即使池中仍有空置的(idle)的工作线程；
如果线程池数目大于corePoolSize但小于maximumPoolSize，那么只有当queue中任务满了的情况，才会创建一个线程进行处理，否则加入队列进行等待。
通过设置corePoolSize和maximumPoolSize一样大小，那么可以创建一个newFixedThreadPool,如果给maximunPoolSize设置一个极大值(Integer.MAX_VALUE)，
那么线程池可以为并发任务提供任意数目的线程；典型地是，线程虽然只能通过构造
(construction)生成，但是可以通过方法setCorePoolSize()和
setMaximumPoolSize()方法动态改变。

（2）on-demand construction
   默认上，相同的core线程只有在新任务到达时才能初始创建和首次启动，但是这个过程通常可以通过使用方法preStartCoreThread()和方法preStartAllCoreThread()来动态覆盖。
   如果构造一个空的线程池，那么很可能需要prestart线程。
 
（3）creating new thread
通常使用一个ThreadFactory来创建新线程。如果不是其他指定的话，那么将使用Executors.defaultThreadFactory,
它将在同一个ThreadGroup中创建线程，这些线程有着相同的优先权(都是NORM_PRIORITY)并都不是daemon状态。
通过提供一个不同的ThreadFactory,那么就可以改变线程的名字，Thread Group，优先权，deamon status等等。
如果调用的ThreadFactory通过newThread()方法返回null从而创建线程失败时，那么虽然executor可能继续存在，但是可能不会在执行任何任务。
线程应该处理“modifyThread”（newRuntimePermission(“modifyThread”)）这个异常。
如果工作线程或者其他线程没有处理该permission，那么service可能会降级的：
配置变化可能会适时地不起作用，处于shutdown的线程可能会维持这样一种状态，termination is possible but not completed
  
(4)keep alive time
 如果当前线程池中的线程数目大于corePoolSize，那么多余的线程将被终止，如果这些线程已经空置的时间大于keepAliveTime(通过方法
getKeepAliveTime()获取)。这提供了一种方法来减少当线程池使用不频繁时资源的消耗。如果线程池使用变得活跃起来，那么新线程将被创建。
该参数同样可以通过方法setKeepAliveTime()来动态改变。通过设置一个long类型，TimeUnit为NANOSECONDS的value,可以使得从terminating到shutdown状态的空置线程失去作用。
默认上，该keep-alive policy只能运用与线程数大于corePoolSize数的场景，
但是allowCoreThreadTimeOut(boolean)方法可以使得policy同样使用于coreThread的场景，只要keepAliveTime的值不为零

(5)Queuing
  BlockingQueue将被用于传递和持有提交的任务，这种队列的使用和线程池的大小互相作用：
1.	如果线程池数目少于corePoolSize，那么Executors优先创建线程来处理任务而不是将任务加入队列
2.	如果线程吃数目大于corePoolSize,那么Executors优先将任务加入队列而不是增加线程来处理
3.	当队列已经满了无法加入新任务，那么会创建一个新的线程，如果线程数会超过maximumPoolSize，那么任务请求将会被拒绝

下面是加入队列的几种策略:
a)	直接交接(direct handoff)。SynchronousQueue是工作队列一个好的选择，该队列会将任务直接交接出去，而不是保有它们。
如果没有可以能够直接运行任务的线程，那么添加任务到队列的尝试会失败，这是一个新的线程将被创建。
这种policy可以避免在处理一系列可能存在内在依赖的请求时上锁的情况。为了避免添加任务时出现rejected的情况，直接交接要求无界的maximumPoolSize。
反过来这也会允许无界线程数的增长的可能性，当任务要求持续增加的平均速度大于线程池能够处理的速度。
b)	无界队列(unbounded queue)。使用一个没有预先定义容量的无界队列比如LinkedBlockingQueue，当所有corePoolSize线程都处于忙碌的情况下，会导致新任务在队列中等待。
因此超过corePoolSize的线程都不会被创建，maximunPoolSize这个参数将不会有任何作用，当所有任务都是互相独立的，这种队列的使用将会比较使用，
所以一个任务的执行不会影像到其他任务的执行，比如web page service。
该队列在smoothing out爆炸式请求时会非常有用，它允须无界队列可以不停增加，但任务请求的平均速度大于处理速度时。
c)	有界队列(bounded queue)。当有界队列使用有限的maximumPoolSize时，可以避免资源耗尽(resource exhaustion)，但是调试和控制起来也就更困难了。
队列大小和maximum pool size要折中对待：大容量队列和小线程池可以最小化cpu占用率和操作系统资源(OS resource)以及上下文环境切换，但是可能人为地导致低流量。
如果一个任务经常阻塞(比如说I/O)，那么系统能够为线程调度更多的时间，而不是你所允许的。
使用小容量队列通常需要大线程池，这可能使得cpu忙碌，但是会导致不可接受的调度花费，这也会导致流量的降低。 
          
        
（6） rejected task
      当Executor已经shut dowen或者当使用有界的最大线程数和工作队列容量同时已经饱和了的时候,executor()中提交的新任务将会被rejected。在这两种情况下，execute()方法都会激活
RejectedExecutionHandler#rejectedExceution()方法。这里有四种预先定义的处理policis:
A.	默认是ThreadPoolExecutor.AbortPolicy,它会为rejection抛出一个Runtime RejectedExecutionException
B.	ThreadPoolExecutor.CallerRunPolicy,它会激活execute()方法自身来执行该任务。该策略提供来一个反馈控制机制，但是会导致新任务提交的速率降低
C.	在ThreadPoolExcecutor.DiscardPolicy策略中,不能被执行的任务会被丢弃
D.	在ThreadPoolExecutor.DiscardOldestPolicy策略中，如果Executor并没有被shutdown，那么工作队列中处于队头的任务将被丢掉，
Executor将会重新尝试(很可能会再次失败，将导致这种策略再次被重复)

       定义和使用其他类别的RejectedExecutionException是可能，这样做需要一些关注，尤其是在设计这些策略只工作在特定的容量和队列策略中。
    
（7）Hook Methods
     这个类会提供protected的可覆盖的(overridable)beforeExecute()和afterExecute()，这两个方法会在任务执行之前或者之后调用。
     这可以用来控制任务执行环境，比如:重新初始化ThreadLocals，gathering statistics,以及adding log entries。
     此外，terminated()方法可以被覆盖用来专门处理那些需要在Executors已经terminated时的需求。
如果hook和call back方法抛出异常，那么内部工作线程可能会轮流失败以及突然terminated。

(8)Queue maintenance
   出于监视和调试的目的，getQueue()方法可以获取工作队列，但是出于其他任何目的而使用该方法的行为都是不支持的。
   当大数目的队列中的(queued)任务被取消时，remove()和purge()可以用于容量回收中。

(9)Finalization
   一个线程池如果不再被引用同时也不存在任何线程，那么这个线程池将会自动shutdown。
   如果你想要在使用者忘记调用terminated()方法的情况下确定不被引用的Executor能够被回收，那么你需要通过设置合理的keep alive time，
   或者使用零下限的core Threads,同时设置allowCoreThreadTimeOut(boolean)

(10)Extension Example
大多数ThreadPoolExecutor的扩展类需要覆盖一个或者多个protected
hook method。

 * @author Administrator
 *
 */
public class ThreadPoolExecutor extends AbstractExecutorService{
         /**
          *    ctl是线程池最主要的状态控制，它是一个原子整数，包装两个概念域：
          *     workCount:表示活跃线程的数目
          *     runstate：表示线程池的状态，是running还是shutdown等
          *     为了将这两个概念打包成一个整体，我们限制workCount的范围为（2^29-1）而不是(2^31-1)那样更具代表性
          *     workCount是活跃的（已经开始但没有停止运行的）线程的数量，由于线程池可能创建线程失败或者退出的线程仍在执行，从而导致
          * 该值与实际线程数目短暂的不等
          *     runState提供了线程池主要的生命周期，有以下几个参数:
          *         (1)Running:可以接受新任务并处理队列
          *         (2)ShutDown:不接受新任务，但是会处理已经进入队列的任务
          *         (3)Stop:不接受新任务，也不处理队列中的任务，会尝试interrupt正在执行的tasks
          *         (4)TIDYING:所有任务都已经终止，workCount为零，过渡到Terminated的线程即将调用方法terminated ()hook方法
          *         (5)Terminated:terminated()方法已经完成
          *    位于以上这些值的数字化的指令，允许有序比较，runState随着时间单调增加，没有必要和其他状态冲突，这些序列是：
          *          (1)RUNNING->SHUTDOWN :调用shutdown()，可能隐晦地在finalize()方法中调用
          *          (2)(RUNNING or SHUTDOWN)->STOP:调用terminated()方法
          *          (3)SHUTDOWN>TIDYING:当队列和线程池都为空的时候
          *          (4)STOP->TIDYING:当线程池为空的时候
          *          (5)TIDYING->Terminated:当钩子方法terminated()完成时
          *      当运行状态为terminated时，在awaitTermination()方法中等待的线程将会返回(return)
          *      当处于SHUTDOWN状态时，判断SHUTDOW到TIDYING要比你预料地更不直接一些，因为队列在为空的情况下也可能会变得不为空
          *      反过来亦然，但是当队列为空的情况下，如果我们观察到了workCount等于0，我们可以只进行终止操作
          * 
          */
	 private final AtomicInteger ctl=new AtomicInteger(ctlOf(RUNNING,0));
	 //count_bits就是用来表示workCount占用一个int的位数(32位)的低29位
	 private final static int COUNT_BITS=Integer.SIZE-3;
	 //capacity表示29位能代表的最大容量，即workCount的最大值，其二进制位为1的二进制位左移29位即1*2^29
	 //00011111111111111111111111111111=00100000000000000000000000000000-1
	 private static final int CAPACITY=1<<COUNT_BITS-1;
	/**
	 * 运行状态runstate占据int类型的高3位
	 */
	 //运行状态Running为 -1左移29位，即11111111111111111111111111111111左移29位为11100000000000000000000000000000
	 private static final int  RUNNING=-1<<COUNT_BITS-1;
	 //运行状态SHUTDOWN为0左移29位，还是为00000000000000000000000000000000
	 private static final int SHUTDOWN=0<<COUNT_BITS-1;
	 //运行状态STOP的值为1左移29位，位00100000000000000000000000000000
	 private static final int STOP=1<<COUNT_BITS;
	 //运行状态TIDYING的值为2左移29位，位01000000000000000000000000000000
	 private static final int TIDYING=2<<COUNT_BITS;	
	 //运行状态TERMINATED的值为3左移29位，位01100000000000000000000000000000
	 private static final int TERMINATED=1<<COUNT_BITS;
	 
	 
	 /**
	  * 包装与解包装ctl
	  */
	 /**
	  * 该方法用来获取runState的值，Capacity按位取反为11100000000000000000000000000000
	  * 与传入c(即ctl)相与，即将低29位置零，保留高三位即RUNSTATE的值
	  * @param c
	  * @return
	  */
	   private static int runStateOf(int c){
		   return c & ~CAPACITY;
	   }
	   /**
		  * 该方法用来获取workCount的值，Capacity为00011111111111111111111111111111
		  * 与传入c(即ctl)相与，即将低29位保留，高三位置零即可得到workCount的值
		  * @param c
		  * @return
		  */
	   private static int workCountOf(int c){
		   return c&CAPACITY;
	   }
	   /**
	    * 传入参数为已经移过值的rs(runstate)和wc(workCount),进行或操作，将这个两个概念保留到一个int中
	    * rs填充返回值的高三位，wc填充返回值的低29位
	    * @param rs
	    * @param wc
	    * @return
	    */
	   private static int ctlOf(int rs,int wc){
		   return rs | wc;
	   }
	   /**
	    * 判断线程池当前状态是否是运行状态
	    * @param c
	    * @return
	    */
	   private static boolean isRunning(int c){
		   return c<SHUTDOWN;
	   }
	   /**
	    * 判断当前运行状态是否处于stop
	    * @param c
	    * @param s
	    * @return
	    */
	   private static boolean runStateAtLeast(int c,int s ){
		   return c>s;
	   }
	   /**
	    * 判断当前运行状态是否少于Stop
	    * @param c
	    * @param s
	    * @return
	    */
	   private static boolean runStateLessThan(int c,int s){
		   return c<s;
	   }
	   /**如果为false，那么core线程即使闲置，也能存活;
	    * 如果为true,那么core线程超时等待工作时间为keepAliveTime
	    * */
	    private volatile boolean allowCoreThreadTimeOut;
	    private  volatile int corePoolSize;
		private  volatile int maximumPoolSize;
		/**为闲置core线程等待工作设置的超时值(nanoseconds)，如果超时将中止
		 * 如果pool中有超过corePoolSize或者如果allowCoreThreadTimeOut为true,线程将会使用这个超时值，否则线程将会永远等待工作
		 * */
		private volatile long keepAliveTime;
		/**
		 * 该队列用来放置任务，并将任务交接给线程
		 */
		private final BlockingQueue<Runnable> workQueue;
		/**
		 * mainLock持有访问workers set 与相关bookkeeping的权限。
		 * mainLock相对某种次序的并发set来说是更好地一种选择。其中一种原因是lock使得访问interruptedIdleWorks()方法串行化，
		 * 这能够避免没必要的中断风暴，尤其是处于shutdown状态时；另外退出的线程会并发地中断那些还没有中断的线程;
		 * 这也能简化largestPoolSize的某些关联数据bookkeeping；为了确保workes set在单独检查中断许可以及事实中断时的平稳性，
		 * 我们同样需要在shutdown()和shutdownNow()方法持有mainLock
		 * */
		private final ReentrantLock mainLock=new ReentrantLock();
		
		/**该set包含了所有线程池中的workers，只有在持有mainLock的时候才能访问*/
		private final HashSet<Worker>workers=new HashSet<>();
		
		/**等待条件，用来支持等待termination*/
		private final Condition termination=mainLock.newCondition();
		
		/**记录该线程池中出现过的最大线程数量(每次增加线程时都会拿workers的size与该值进行比较，如果大于，则更新该值)
		 * 只有在持有mainLock的时候访问。
		 * */
		private int largestPoolSize;
		/**记录完成任务的数量，只有在worker thread 中断时才能进行更新，只有持有mainLock时才能访问*/
		private static int completedTaskCount;
		private  volatile ThreadFactory factory;
		private volatile RejectedExecutionHandler handler;
		/**
		 * 默认rejected execution handler
		 */
		private static final RejectedExecutionHandler defaultHandler=new AbortPolicy();
         
		public ThreadPoolExecutor(int corePoolSize,
        		int maximumPoolSize,
        		long keepAliveTime,
        		TimeUnit unit,
        		BlockingQueue<Runnable>workQueue,
        		ThreadFactory factory,
        		RejectedExecutionHandler handler ){
        	 if(corePoolSize<0 || maximumPoolSize<=0 || corePoolSize>maximumPoolSize || keepAliveTime<0){
        		 throw new IllegalArgumentException();
        	 }
        	 if(workQueue==null || factory==null || handler==null){
        		 throw new NullPointerException();
        	 }
        	 this.corePoolSize=corePoolSize;
        	 this.maximumPoolSize=maximumPoolSize;
        	 this.keepAliveTime=keepAliveTime;
        	 this.workQueue=workQueue;
        	 this.factory=factory;
        	 this.handler=handler;
        	
        }

		public ThreadPoolExecutor(int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit  units,
				SynchronousQueue<Runnable> synchronousQueue1) {
			this(corePoolSize, maxPoolSize, keepAliveTime, units, synchronousQueue1, Executors.getThreadFactory(), defaultHandler);
		}

		/**
		 * 在将来某个时间点执行指定的Runnable任务，该任务可能被一个新线程执行也可能被线程池中已存在的线程执行
		 */
		public void execute(Runnable command) {
			if(command==null) throw new NullPointerException();
			/**
			 *任务执行策略:
			 *(1)如果线程池中运行的线程数少于corePoolSize，那么就创建一个新线程，指定的任务为该线程第一个任务
			 *(2)如果任务成功进入队列中(queued),那么仍然需要进行双重检查，不论我们是否已经增加一个线程(因为存在最新一次检查时
			 *一个线程死去的情况)还是线程池关闭了。于是我们重新检查运行状态，如果必要地话，如果停止了，退回入队操作，如果没有线程，那么
			 *就重新启动一个线程
			 *(3)如果添加任务失败，那么尝试新建一个线程，如果同样失败，那么线程池已经关闭，这样可以rejected该任务
			 */
			int c=ctl.get();//获取控制状态
			if(workCountOf(c)<corePoolSize){//如果线程数小于corePoolSize，那么增加一个worker，并为worker里的新线程指定该任务
				if(addWorker(command, true))
					return;
				c=ctl.get();//如果添加失败，重新获取当前状态
			}
			if(isRunning(c)&&workQueue.offer(command)){//如果Running状态，则添加任务进入队列workQueue
				int reject=ctl.get();//重新检查运行状态
				if(!isRunning(reject)&&remove(command)){//如果非Running状态，那么移除该任务，成功则执行reject
					reject(command);
				}else if(workCountOf(reject)==0){//如果Running状态或者移除任务失败，则判断线程池是否为空导致的，是那么就增加一个空任务的线程
					addWorker(null, false);
				}
			}else if(!addWorker(command, false)){//如果添加任务进入队列失败，则尝试添加一个worker，如果同样失败，那么线程池已经关闭了
				reject(command);
			}
		}
		
		/**
		 * 为指定的任务激活rejected handler
		 * @param command
		 */
		final void reject(Runnable command){
			handler.rejectedExecution(command, this);
		}
		/**
		 * 从执行程序的内部队列中移除此任务（如果存在），从而如果尚未开始，则其不再运行。 
         * 此方法可用作取消方案的一部分。它可能无法移除在放置到内部队列之前已经转换为其他形式的任务。
         *  例如，使用 submit 输入的任务可能被转换为维护 Future 状态的形式。但是，在此情况下，purge() 方法可用于移除那些已被取消的 Future。
		 * @param task
		 * @return
		 */
		public boolean remove(Runnable task){
			boolean removed=workQueue.remove(task);
			tryTerminate();//防止线程池已经SHUTDOWN同时已经empty
			return removed;
		}
		/**
		 * 该方法用来判断能否添加新的任务，在当前的线程池状态和给定的界限下(要么是核心线程数，要么是最大线程数)；
		 * 如果添加成功，那么线程数(workCount)也会相应的增加，可能地话，一个新的工作者将会被创建并执行，这个firstTask被当作
		 * 该新工作者线程的第一个任务来执行；addWorker 在以下几种状况下回返回false:(1)线程池状态为stopped或者可以转化为shutdown状态
		 * (2)线程池创建线程失败当需要创建时,线程创建失败可能是因为线程池返回null或者可能是因为异常(通常是OutOfMemoryException)
		 * 
		 * @param firstTask 当线程池线程数少于corePoolSize或者当工作队列快接近满了时，那么很多workers将会被创建，来运行初始化的首次任务
		 * 初始化闲散的线程通常会通过preStartCoreThread创建，或者会被用来替代一些快死亡的线程
		 * 
		 * 
		 * @param core
		 */
		private boolean addWorker(Runnable firstTask,boolean core){
			retry:
				for(;;){//外循环：判断运行状态
					//获取当前控制状态c与线程池运行状态
					int c=ctl.get();
					int rs=runStateOf(c);
					
					//如果rs大于等于shutdown时返回false，当且仅当rs==shutdown同时firstTask==null且queue.isEmpty()不为空的情况除外
					//SHUTDOWN状态不再接受新任务，但会处理队列中的任务
					if(rs>=SHUTDOWN&&!(rs==SHUTDOWN&&firstTask==null&&!workQueue.isEmpty())){
						return false;
					}
					//不满足以上条件，继续内(inner)循环：判断线程数
					for(;;){
						//获取线程数，如果大于核心数或者最大数(通过主动设置core布尔值)，直接返回false
						int wc=workCountOf(c);
						if(wc>CAPACITY||wc>(core?corePoolSize:maximumPoolSize)){
							return false;
						}
						//尝试将线程数加1,如果成功跳出retry,失败则判断是线程池运行状态rs的改变引起还是线程数增加导致的
						if(compareAndIncrement(c)){
							break retry;
						}
						//获取当前控制状态ctl中得到运行状态，判断运行状态是否已经改变，如果改变了，那么重新开始retry
						if(runStateOf(ctl.get())!=rs){
							continue retry;
						}
						//运行到这，这说明cas失败是因为线程数的增加导致，那么重新开始内循环
					}
				}
		   boolean workerAdded=false;
		   boolean workerStarted=false;
		   Worker w=null;
		   try{
			   final ReentrantLock mainLock=this.mainLock;//获得主锁
			   w=new Worker(firstTask);//创建一个worker，每个任务都有专门的线程来处理
			   final Thread t=w.thread;//获取w的工作线程
			   //如果t等于null，则说明ThreadFactory创建失败，也将不会进行add worker操作
			   if(t!=null){
				   mainLock.lock();//不要放在try语句中
				   /**在锁定状态下，检查运行状态
				    * 判断线程状态
				    * */
				   try{
					   int c=ctl.get();//获取控制状态
					   int rs=runStateOf(c);//获取运行状态
					   //检查运行状态，只有在running状态或者shutdown状态时，firstTask==null(调用方法shutDown())的情况下，才能进行添加操作
					   if(rs<SHUTDOWN || (rs==SHUTDOWN&& firstTask==null)){
						   if(t.isAlive()){//如果线程已经处于运行状态了，那么抛出异常
							   throw new IllegalThreadStateException();
						   }
						   workers.add(w);
						   int size=workers.size();
						   if(size>largestPoolSize){
							   largestPoolSize=size;
						   }
						   workerAdded=true;//表明添加任务成功
					   }
				   }finally{
					   mainLock.unlock();
				   }
			   }
			   if(workerAdded){//worker入队列成功则运行线程，并设置workerStarted成功
				   t.start();
				   workerStarted=true;//线程启动了
			   }
		   }finally{
			 if(!workerStarted){
				 addWorkerFailed(w);
			 }  
		   }
		   return workerStarted;
		}
		/**线程添加失败，则(1)获取锁 (2)根据w判断是否从workers中移除该worker(3)decrementWorkerCount()
		 * (4)tryTerminate()，以防删除的worker持用Termination状态
		 * */
		private void addWorkerFailed(Worker w) {
			final ReentrantLock mainLock=this.mainLock;
			mainLock.lock();
			try{
				if(w!=null){
					workers.remove(w);
				}
			decrementWorkerCount();
			tryTerminate();
			}finally{
				mainLock.unlock();
			}
			
		}
		private boolean compareAndIncrement(int expect) {
			return ctl.compareAndSet(expect, expect+1);
		}
		private boolean compareAndDecrementWorker(int expect){
			return ctl.compareAndSet(expect, expect-1);
		}
		/**
		 * 只有在线程突然中断时才可以调用，如processWorkerExit()
		 */
		private  void decrementWorkerCount(){
			do{}while(!compareAndDecrementWorker(ctl.get()));
		}
		public static class AbortPolicy implements RejectedExecutionHandler{
          public AbortPolicy(){}
			@Override
			/**
			 * 始终抛出异常，abort:中止
			 */
			public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
				throw new  RejectedExecutionException("Task:"+r.toString()+"rejected from "+executor.toString());
				
			}
			
		}
		
		/**
		 * Worker类主要是与其他minor bookkeeping一道对线程运行的任务中断控制状态进行维持。
		 * 该类继承了AbstractQueuedSynchronizer只是为了获取和释放围绕任务执行的锁。
		 * 这会防止那些倾向于唤醒等待任务的工作线程的interrupts去中断正在运行任务。
		 * 我们实现了一个不可重入(non-reentrant)互斥锁，而不是采用ReentrantLock,是因为我们不希望工作任务在激活线程池控制方法例如setCorePoolSize()时再次获取锁。
		 * 为了严禁在线程真正开始一个工作任务之前interrupts，我们给锁状态设置了一个负数，并且当线程运行时才清除。
		 * @author heyw
		 *
		 */
		private final class Worker   extends AbstractQueuedSynchronizer
		implements Runnable{
			
			/**
			 * 该类永远不会被序列化，提供一个seralVersionUID为了禁止javac警告
			 */
			private static final long serialVersionUID = -9122931407671700319L;
			/**Worker运行任务的线程，当factory失败时为null			 */
			final Thread thread;
			/** 即将运行的初始化任务*/
			Runnable firstTask;
			/**每个线程的共有任务计数器 */
			volatile long completedTasks;
			
			/**创建Worker利用传入的参数firstTask和ThreadFactoryPool中的变量factory */
			public Worker(Runnable firstTask){
				setState(-1);//-1表示当前状态严禁中断
				this.firstTask=firstTask;
				this.thread=getFactory().newThread(this);
			}
			/**
			 * 将主循环放到外部类的runWorker()中运行，内部类可以访问外部类方法和属性
			 */
			@Override
			public void run() {
				runWorker(this);
			}
			//Lock方法，0代表unlock,1代表lock
			//定义三个不能在外包中使用的方法isHeldExclusively()，tryAcquire(),tryRelease()
			/**判断当前线程是否拥有互斥锁，true表示拥有，此时state为1*/
			protected boolean isHeldExclusively(){
				return getState()!=0;
			}
			/**获取锁*/
			protected boolean tryAcquire(int unused){
				if(compareAndSetState(0,1)){//判断当前状态是否为0，如果是则设置为1，代表获取锁的过程
					setExclusiveOwnerThread(Thread.currentThread());//该方法是AbstractQueuedSynchronizer中的方法
					return true;
				}
				return false;
			}
			/**释放锁*/
			protected boolean tryRelease(int unused){
				setExclusiveOwnerThread(null);
				setState(0);
				return true;
			}
			//提供给外包使用的方法 lock(),unlock(),tryLock(),islock(),interruptIfStarted()
			public void lock(){
				 acquire(1);
				}
			
			public void unlock(){
			    release(1);	
			}
			public boolean tryLock(){
				return tryAcquire(1);
			}
			public boolean isLock(){
				return isHeldExclusively();
			}
			void interruptIfStart(){
				Thread t;
				if(getState()>=0 && (t=thread)!=null &&!t.isInterrupted()){
					try{
						t.interrupt();
					}catch(SecurityException ignore){
						
					}
				}
			}
		}

		public ThreadFactory getFactory() {
			return factory;
		}
		
		/**
		 * 主要Worker的运行循环方法。当应对一些问题时，不断地从getTasks()方法获取任务，并执行；
		 * (1)可以从一个初始任务开始运行，因此通常没必要从队列中获取第一个任务；另外只要线程池仍在运行，那么就可以通过方法getTasks()不断获取任务；
		 * 如果getTasks()返回null,那么worker退出是因为线程池runstate或者configuration parameters的变化导致的；
		 * worker退出的其他原因是因为外部代码抛出的异常所致，在这种情况下是由completedAbruptly holds，这将导致processWorkerExit替代当前线程
		 * (2)在运行任何任务之前，获取锁视为防止在任务运行时其他线程池中断该任务，调用clearInterruptsForTaskRun是为了确保只有当线程池处于stopped状态，
		 * 那么当前线程不会有自身interrupt set；
		 * (3)再运行任何任务之前都会调用方法beforeExecute()，该方法可能会抛出一个异常，这将导致线程死亡(completedAbruptly 为true时将退出循环)，该任务也将不会被处理
		 * (4)如果beforeExecute()方正常完成，那么运行task，同时将抛出的所有异常聚集到afterExecute()方法中。我们分开处理RuntimeException,Error以及手动异常。
		 * 因为Runnable.run()方法不能重新抛出异常，所以我们将其零用UncaughtExceptionHandler转换成error。任何抛出地异常都会导致线程死亡；
		 * (5)当task.run()方法执行完成后，将调用方法afterExecute(),这个方法也会抛出异常，这也将导致线程死亡。According to JLS Sec 14.20, this exception is the one that
          * will be in effect even if task.run throws
          * 异常机制的实际效果(net effective)就是尽可能地提供了由使用者代码导致的任何问题的准确信息。
		 * @param worker
		 */
	   final void runWorker(Worker w){
			Thread wt=Thread.currentThread();
			Runnable task=w.firstTask;
			w.firstTask=null;
			w.unlock();//允许中断
			boolean completedAbruptly=true;
			try{
				 while(task!=null && (task=getTask())!=null){
					 w.lock();
					 //如果线程池处于stopping,那么确认线程已经中断了
					 //如果线程池不处于stopping，那么确认线程没有中断。这里需要对运行状态进行第二次检查为了处理当清理中断标志时shutDownNow race
					 if((runStateAtLeast(ctl.get(),STOP) || (!wt.interrupted()&&runStateAtLeast(ctl.get(), STOP)))
							 &&!wt.isInterrupted()){
						 wt.interrupt();
					 }
					 try{
						 beforeExecute(wt,task);
						 Throwable thrown=null;
						 try{
							 task.run();
						 }catch(RuntimeException x){
							 thrown=x;throw x;
						 }catch(Error x){
							 thrown=x;throw x;
						 }catch(Throwable x){
							 thrown=x;throw new Error(x);
						 }finally{
							 afterExecute(task,thrown);
						 }
					 }finally{
						 task=null;
						 w.completedTasks++;
						 w.unlock();
					 }
				 }
				 completedAbruptly=false;
			}finally{
				 processWorkerExit(w,completedAbruptly);
			 }
		}
	   //hook methods
	   /**
	    * 该方法将在给定的线程wt中执行给定的任务task之前被激活，该方法是被线程wt激活的，可能被用于重新初始化ThreadLocals或者用来输出日志
	    * 该实现并没有做任何事，但是子类可以进行定制；为了合理编写大量覆盖方法，子类需要在重写方法结尾处激活父类super.beforeExecute()
	    * @param wt
	    * @param task
	    */
		private void beforeExecute(Thread wt, Runnable task) {
			
		}
		/**
		 * 当指定任务执行完成后，执行任务的线程将激活该方法；如果Throwable不为空，那么Throwable可能是uncaught(的RuntimeException)或者error，这将导致异常突然被中止
		 * 该实现并没有做任何事情，但是可以在子类中进行定制；同样为了合理编写大量覆盖方法，需要在重写方法开始处激活该方法(super.afterExecute());
		 * 当task(FutureTask)结束动作时，无论是否通过其他方法(如：submit()),task对象都会捕获和保有那些可以处理的异常，这些异常不会传递给afterExecute()，如果希望这样做，
		 * 那么可以在子类中进行实现。
		 * public ExtendedExecutor extends ThreadPoolExecutor{
		 * ........
		 * private void afterExecute(Runnable r,Throwable t){
		 * super.afterExecute(r,t);
		 * if(r!=null && r instanceof Future<?>){
		 *      try{
		 *          Object result =(Future<?> r).get();
		 *      }catch (CancellationException ce) {
      *           t = ce;
     *       } catch (ExecutionException ee) {
     *           t = ee.getCause();
     *       } catch (InterruptedException ie) {
     *           Thread.currentThread().interrupt(); // ignore/reset
     *       }
     *     }
     *     if (t != null)
     *       System.out.println(t);
     *   }
		 * }
		 * }
		 * }
		 * @param r
		 * @param t
		 */
		private void afterExecute(Runnable r, Throwable t) {
			
		}
		/**
		 * 该类用来为一个正在死亡的worker进行清理和标记，只能在worker线程中调用，除非completedAbrubtly为true，否则认为workerCount已经调整好了，准备退出；
		 * 该类会将线程从worker set中除去，并且可能会中断线程池或者替换一些worker，如果要么这些worker退出时是因为user task异常导致，
		 * 或者要么此时少于corePoolSize的worker在运行或者work queue 不为空但是没有多余的work
		 * @param w
		 * @param completedAbruptly
		 */
		private void processWorkerExit(Worker w,boolean completedAbruptly){
			if(completedAbruptly){//如果completedAbruptly为true，说明workCounter没有被调整
				decrementWorkerCount();
			}
			final ReentrantLock mainLock=this.mainLock;
			mainLock.lock();
			try{
				//更新completedTaskCount并移除w
			       completedTaskCount+=w.completedTasks;
			       workers.remove(w);
			}finally{
				mainLock.unlock();
			}
			//从workers中移除worker,需要调用tryTerminate()方法
			tryTerminate();
			int c=ctl.get();
			//线程池状态只有处于stop，TYDING或者TERMINATED
			if(runStateLessThan(c, STOP)){
				if(!completedAbruptly){
					int min=allowCoreThreadTimeOut?0:corePoolSize;
					if(min==0 && !workQueue.isEmpty()){
						min=1;
					}
					if(workCountOf(c)>min){
						return ;//无需替换
					}
				}
				addWorker(null, false);
			}
		}
		
		/**
		 * 如果ShutDown以及pool和queue为空，或者如果STOP以及pool为空，则转变到TERMINATED状态;
		 * 如果其他有资格中断的条件但是workerCount不为零，那么中断一个空置的线程来确保shutdown信号的传达；
		 * 该方法的调用必须跟在任何可能导致termination的方法之后，如：在shutdown时减少work count或者从队列中移除任务；
		 * 为了从ScheduleThreadPoolExecutor中获取访问权限，该方法并不是private的
		 */
		private void tryTerminate() {
			for(;;){
				//首先先进行状态判断
				int c=ctl.get();
				//如果状态不为stop，shutdown时或者为shutdown时workQueue不为空，则不能进行terminate
				if(isRunning(c)||runStateAtLeast(c, TIDYING)||(runStateOf(c)==SHUTDOWN&&!workQueue.isEmpty())){
					return ;
				}
				//如果线程池不为空，则尝试中断任意一个worker后返回
				if(workCountOf(c)!=0){
					interruptIdleWorkers(ONLY_ONE);
					return ;
				}
				//程序运行到这，线程池状态肯定为shutdown且workQueue为空或者stop，同时线程池没有线程
				final ReentrantLock mainLock=this.mainLock;
				mainLock.lock();
				try{
					//尝试设置运行状态为TIDYING,成功则调用terminated()方法用来表示已经终止线程池，并设置运行状态为terminated的
					 if(ctl.compareAndSet(c, TIDYING)){
						 try{
							 terminated();
						 }finally{
							  ctl.compareAndSet(c, TERMINATED);
							  termination.signalAll();//通知所有等待线程，已经终止线程池了	 
						 }
						 return ;
					 }
					 //如果cas失败，重新循环尝试
				}finally{
					mainLock.unlock();
				}
			}
		}
		/**
		 * 该方法中断那些可能正在等待task的线程(可以通过是否可以被加锁确定)，于是这些线程可以检查terminated和配置变更，
		 * 忽略SecurityExceptions(在这种情况下，线程可能不能被中断)
		 * @param onlyone 如果为true，那么最多只能中断一个线程。当termination是以其他方式激活的，另一方面还有其他workers,那么该方法只能从tryTerminate()方法
		 * 中被调用。在这种情况下，最多中断一个worker来传达shutdown信号以防所有的线程都处于等待。
		 * 中断任意（arbitrary）线程能够确保既然shutdown启动了那么新来的线程最终也能够退出；为了确保最终termination,该方法需要满足至多中断一个线程，
		*但是shutdown()方法所有闲置线程以便足够的线程能够合理退出，而不用等待完成一个流浪任务。
		 */
		private void interruptIdleWorkers(boolean onlyone){
			final ReentrantLock mainLock=this.mainLock;
			mainLock.lock();
			try{
				 for(Worker w:workers){
					 Thread t=w.thread;
					 if(!t.isInterrupted()&&w.tryLock()){//中断那些等待task的线程
						 try{
							t.interrupt(); 
						 }catch(SecurityException ingore){}
						 finally{
							 w.unlock();
						 }
					 }
					 if(onlyone){//最多中断一个idle线程
						 break;
					 }
				 }
			}finally{
				mainLock.unlock();
			}
			
		}
		
		private static boolean ONLY_ONE=true;
		/**
		 * 根据当前配置设定不同，该方法将表现为阻塞或者定时等待，或者返回null,如果出现以下几种情况导致worker必须退出:
		 * (1)线程池运行状态为stop
		 * (2)线程池运行状态为shutdown但是workqueue为空
		 * (3)超过maximumPoolSize的workers
		 * (4)worker超时等待一个任务，同时超时的任务会被终止(通过allowCoreThreadTimeOut || workerCount>corePoolSize来判断),无论是在定时任务之前还是之后
		 * @return task，或者null，如果worker必须退出，无论如何workerCount都会减少
		 */
		private Runnable getTask() {
		    boolean timeOut=false;//上次poll()是否已经超时？
		    
		    retry:
		    	//外循环：判断当前运行状态是否是运行状态后，进行内循环
		    	for(;;){
		    		   int c=ctl.get();//获取当前控制状态
		    		   int rs=runStateOf(c);//获取当前运行状态
		    		 //  int ws=workCountOf(c);//此处不获取当前线程数，因为没有意义，如果不为running状态则返回，ws就不会有用
		    		   //如果运行状态为stop及以上或者为shutdown且workQueue不为空，则减少工作线程数，并返回null
		    		   if((rs>=SHUTDOWN) &&(rs>=STOP||workQueue.isEmpty())){
		    			   decrementWorkerCount();
		    			   return null;
		    		   }
		    		   boolean timed;//设置定时标志，判断依据ws>corePoolSize或者allowCoreThreadTimeOut
		    		   //开始内循环
		    		   for(;;){
		    			   int ws=workCountOf(c);//在此处获取当前工作线程数
		    			   timed=allowCoreThreadTimeOut||(ws>corePoolSize);//判断是否timed
		    			   //判断是否可以跳出内循环进行取数，条件为ws小于最大线程数且并没有超时(timeOut&&timed)=true
		    			   if(ws<=maximumPoolSize&&!(timeOut&&timed)){
		    				   break;
		    			   }
		    			   //cas尝试减少工作线程，如果失败则根据原因选择重新外循环还是内循环
		    			   if(compareAndDecrementWorker(c)){
		    				   return null;//成功返回null
		    			   }
		    			   //如果cas失败，失败是因为rs变化，则外循环
		    			   c=ctl.get();//重新获取控制状态c
		    			   if(runStateOf(c)!=rs){
		    				   continue retry;
		    			   }
		    			   //如果cas失败，是因为工作线程数量的变化，内循环
		    		   }
		    		   try{
		    		   //跳出循环，则开始获取数，根据是否timed来选择非阻塞的poll还是阻塞take()
		    		   Runnable result=timed?workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS):workQueue.take();
		    		   //判断result是否为空
		    		   if(result!=null){
		    			   return result;
		    		   }
		    		   //如果为空，则判断为已经超时
		    		   timeOut=true;
		    		   }catch(InterruptedException retry){//异常，则重新外循环
		    			   timeOut=false;
		    		   }
		    	}
			
		}
		
		/**
		 * 当线程池终止时调用该方法。该方法默认实现不做任何事。note:为了合理嵌套大量重写，子类实现需要使用该方法来激活super.terminated()方法
		 */
		private void terminated(){}
	
}
