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
 * ExecutorService����ͨ��Executors�����������õģ�����ʹ��һЩ�����߳���ִ�������ύ������
ThreadPools�������ֲ�ͬ�����⣺��ִ�д���Ŀ�첽��asynschronous������ʱ��Thread poolsͨ������ÿ�����񼤻������������ܣ�
Thread poolsҲ�ṩ��һ�����ƺ͹�������߳����ڵ���Դ�ķ�ʽ����Щ��Դ���ᱻʹ�õ�ִ��һϵ������ʱ��tasks����
ÿ��ThreadPoolExecutor�����ṩһЩ��������(basic statics),����������ɵ���Ŀ֮��
Ϊ�����ڴ�Χ��������Ч��ThreadPoolExecutor�ṩ�˺ܶ�ɵ����Ϳ���չ�Ĺ���(hook)��
Ȼ��������Ա������ʹ�ø������Executors������������Executors.newCachedThreadPool()���޽��̳߳أ������Զ������߳�
Executors.newFixedThreadPool(),�̶���С�̳߳أ�
Executors.newSingleThreadPool(),��һ�����̳߳أ����ԶԴ������������(usage scenoarios)������������(preconfigure settings);
����֮�⣬�����ú͵��Ը����ʱ�����ʹ�����µ�ָ����

��1��corePoolSize��maximumPoolSize:
�̳߳ؿ��Ը���corePoolSize(getCorePoolSize()����)��maximumPoolSize(getMaximumPoolSize()����)���õĽ��ޣ��Զ������̳߳ش�С(getPoolSize());
������execute()���ύ��һ����task�������ʱ�̳߳����߳�������corePoolSize��ô���ᴴ��һ���߳�����������񣬼�ʹ�������п��õ�(idle)�Ĺ����̣߳�
����̳߳���Ŀ����corePoolSize��С��maximumPoolSize����ôֻ�е�queue���������˵�������Żᴴ��һ���߳̽��д������������н��еȴ���
ͨ������corePoolSize��maximumPoolSizeһ����С����ô���Դ���һ��newFixedThreadPool,�����maximunPoolSize����һ������ֵ(Integer.MAX_VALUE)��
��ô�̳߳ؿ���Ϊ���������ṩ������Ŀ���̣߳����͵��ǣ��߳���Ȼֻ��ͨ������
(construction)���ɣ����ǿ���ͨ������setCorePoolSize()��
setMaximumPoolSize()������̬�ı䡣

��2��on-demand construction
   Ĭ���ϣ���ͬ��core�߳�ֻ���������񵽴�ʱ���ܳ�ʼ�������״������������������ͨ������ͨ��ʹ�÷���preStartCoreThread()�ͷ���preStartAllCoreThread()����̬���ǡ�
   �������һ���յ��̳߳أ���ô�ܿ�����Ҫprestart�̡߳�
 
��3��creating new thread
ͨ��ʹ��һ��ThreadFactory���������̡߳������������ָ���Ļ�����ô��ʹ��Executors.defaultThreadFactory,
������ͬһ��ThreadGroup�д����̣߳���Щ�߳�������ͬ������Ȩ(����NORM_PRIORITY)��������daemon״̬��
ͨ���ṩһ����ͬ��ThreadFactory,��ô�Ϳ��Ըı��̵߳����֣�Thread Group������Ȩ��deamon status�ȵȡ�
������õ�ThreadFactoryͨ��newThread()��������null�Ӷ������߳�ʧ��ʱ����ô��Ȼexecutor���ܼ������ڣ����ǿ��ܲ�����ִ���κ�����
�߳�Ӧ�ô���modifyThread����newRuntimePermission(��modifyThread��)������쳣��
��������̻߳��������߳�û�д����permission����ôservice���ܻή���ģ�
���ñ仯���ܻ���ʱ�ز������ã�����shutdown���߳̿��ܻ�ά������һ��״̬��termination is possible but not completed
  
(4)keep alive time
 �����ǰ�̳߳��е��߳���Ŀ����corePoolSize����ô������߳̽�����ֹ�������Щ�߳��Ѿ����õ�ʱ�����keepAliveTime(ͨ������
getKeepAliveTime()��ȡ)�����ṩ��һ�ַ��������ٵ��̳߳�ʹ�ò�Ƶ��ʱ��Դ�����ġ�����̳߳�ʹ�ñ�û�Ծ��������ô���߳̽���������
�ò���ͬ������ͨ������setKeepAliveTime()����̬�ı䡣ͨ������һ��long���ͣ�TimeUnitΪNANOSECONDS��value,����ʹ�ô�terminating��shutdown״̬�Ŀ����߳�ʧȥ���á�
Ĭ���ϣ���keep-alive policyֻ���������߳�������corePoolSize���ĳ�����
����allowCoreThreadTimeOut(boolean)��������ʹ��policyͬ��ʹ����coreThread�ĳ�����ֻҪkeepAliveTime��ֵ��Ϊ��

(5)Queuing
  BlockingQueue�������ڴ��ݺͳ����ύ���������ֶ��е�ʹ�ú��̳߳صĴ�С�������ã�
1.	����̳߳���Ŀ����corePoolSize����ôExecutors���ȴ����߳���������������ǽ�����������
2.	����̳߳���Ŀ����corePoolSize,��ôExecutors���Ƚ����������ж����������߳�������
3.	�������Ѿ������޷�������������ô�ᴴ��һ���µ��̣߳�����߳����ᳬ��maximumPoolSize����ô�������󽫻ᱻ�ܾ�

�����Ǽ�����еļ��ֲ���:
a)	ֱ�ӽ���(direct handoff)��SynchronousQueue�ǹ�������һ���õ�ѡ�񣬸ö��лὫ����ֱ�ӽ��ӳ�ȥ�������Ǳ������ǡ�
���û�п����ܹ�ֱ������������̣߳���ô������񵽶��еĳ��Ի�ʧ�ܣ�����һ���µ��߳̽���������
����policy���Ա����ڴ���һϵ�п��ܴ�����������������ʱ�����������Ϊ�˱����������ʱ����rejected�������ֱ�ӽ���Ҫ���޽��maximumPoolSize��
��������Ҳ�������޽��߳����������Ŀ����ԣ�������Ҫ��������ӵ�ƽ���ٶȴ����̳߳��ܹ�������ٶȡ�
b)	�޽����(unbounded queue)��ʹ��һ��û��Ԥ�ȶ����������޽���б���LinkedBlockingQueue��������corePoolSize�̶߳�����æµ������£��ᵼ���������ڶ����еȴ���
��˳���corePoolSize���̶߳����ᱻ������maximunPoolSize����������������κ����ã������������ǻ�������ģ����ֶ��е�ʹ�ý���Ƚ�ʹ�ã�
����һ�������ִ�в���Ӱ�����������ִ�У�����web page service��
�ö�����smoothing out��ըʽ����ʱ��ǳ����ã��������޽���п��Բ�ͣ���ӣ������������ƽ���ٶȴ��ڴ����ٶ�ʱ��
c)	�н����(bounded queue)�����н����ʹ�����޵�maximumPoolSizeʱ�����Ա�����Դ�ľ�(resource exhaustion)�����ǵ��ԺͿ�������Ҳ�͸������ˡ�
���д�С��maximum pool sizeҪ���жԴ������������к�С�̳߳ؿ�����С��cpuռ���ʺͲ���ϵͳ��Դ(OS resource)�Լ������Ļ����л������ǿ�����Ϊ�ص��µ�������
���һ�����񾭳�����(����˵I/O)����ôϵͳ�ܹ�Ϊ�̵߳��ȸ����ʱ�䣬��������������ġ�
ʹ��С��������ͨ����Ҫ���̳߳أ������ʹ��cpuæµ�����ǻᵼ�²��ɽ��ܵĵ��Ȼ��ѣ���Ҳ�ᵼ�������Ľ��͡� 
          
        
��6�� rejected task
      ��Executor�Ѿ�shut dowen���ߵ�ʹ���н������߳����͹�����������ͬʱ�Ѿ������˵�ʱ��,executor()���ύ�������񽫻ᱻrejected��������������£�execute()�������ἤ��
RejectedExecutionHandler#rejectedExceution()����������������Ԥ�ȶ���Ĵ���policis:
A.	Ĭ����ThreadPoolExecutor.AbortPolicy,����Ϊrejection�׳�һ��Runtime RejectedExecutionException
B.	ThreadPoolExecutor.CallerRunPolicy,���ἤ��execute()����������ִ�и����񡣸ò����ṩ��һ���������ƻ��ƣ����ǻᵼ���������ύ�����ʽ���
C.	��ThreadPoolExcecutor.DiscardPolicy������,���ܱ�ִ�е�����ᱻ����
D.	��ThreadPoolExecutor.DiscardOldestPolicy�����У����Executor��û�б�shutdown����ô���������д��ڶ�ͷ�����񽫱�������
Executor�������³���(�ܿ��ܻ��ٴ�ʧ�ܣ����������ֲ����ٴα��ظ�)

       �����ʹ����������RejectedExecutionException�ǿ��ܣ���������ҪһЩ��ע���������������Щ����ֻ�������ض��������Ͷ��в����С�
    
��7��Hook Methods
     �������ṩprotected�Ŀɸ��ǵ�(overridable)beforeExecute()��afterExecute()��������������������ִ��֮ǰ����֮����á�
     �����������������ִ�л���������:���³�ʼ��ThreadLocals��gathering statistics,�Լ�adding log entries��
     ���⣬terminated()�������Ա���������ר�Ŵ�����Щ��Ҫ��Executors�Ѿ�terminatedʱ������
���hook��call back�����׳��쳣����ô�ڲ������߳̿��ܻ�����ʧ���Լ�ͻȻterminated��

(8)Queue maintenance
   ���ڼ��Ӻ͵��Ե�Ŀ�ģ�getQueue()�������Ի�ȡ�������У����ǳ��������κ�Ŀ�Ķ�ʹ�ø÷�������Ϊ���ǲ�֧�ֵġ�
   ������Ŀ�Ķ����е�(queued)����ȡ��ʱ��remove()��purge()�����������������С�

(9)Finalization
   һ���̳߳�������ٱ�����ͬʱҲ�������κ��̣߳���ô����̳߳ؽ����Զ�shutdown��
   �������Ҫ��ʹ�������ǵ���terminated()�����������ȷ���������õ�Executor�ܹ������գ���ô����Ҫͨ�����ú����keep alive time��
   ����ʹ�������޵�core Threads,ͬʱ����allowCoreThreadTimeOut(boolean)

(10)Extension Example
�����ThreadPoolExecutor����չ����Ҫ����һ�����߶��protected
hook method��

 * @author Administrator
 *
 */
public class ThreadPoolExecutor extends AbstractExecutorService{
         /**
          *    ctl���̳߳�����Ҫ��״̬���ƣ�����һ��ԭ����������װ����������
          *     workCount:��ʾ��Ծ�̵߳���Ŀ
          *     runstate����ʾ�̳߳ص�״̬����running����shutdown��
          *     Ϊ�˽���������������һ�����壬��������workCount�ķ�ΧΪ��2^29-1��������(2^31-1)�������ߴ�����
          *     workCount�ǻ�Ծ�ģ��Ѿ���ʼ��û��ֹͣ���еģ��̵߳������������̳߳ؿ��ܴ����߳�ʧ�ܻ����˳����߳�����ִ�У��Ӷ�����
          * ��ֵ��ʵ���߳���Ŀ���ݵĲ���
          *     runState�ṩ���̳߳���Ҫ���������ڣ������¼�������:
          *         (1)Running:���Խ��������񲢴������
          *         (2)ShutDown:�����������񣬵��ǻᴦ���Ѿ�������е�����
          *         (3)Stop:������������Ҳ����������е����񣬻᳢��interrupt����ִ�е�tasks
          *         (4)TIDYING:���������Ѿ���ֹ��workCountΪ�㣬���ɵ�Terminated���̼߳������÷���terminated ()hook����
          *         (5)Terminated:terminated()�����Ѿ����
          *    λ��������Щֵ�����ֻ���ָ���������Ƚϣ�runState����ʱ�䵥�����ӣ�û�б�Ҫ������״̬��ͻ����Щ�����ǣ�
          *          (1)RUNNING->SHUTDOWN :����shutdown()���������޵���finalize()�����е���
          *          (2)(RUNNING or SHUTDOWN)->STOP:����terminated()����
          *          (3)SHUTDOWN>TIDYING:�����к��̳߳ض�Ϊ�յ�ʱ��
          *          (4)STOP->TIDYING:���̳߳�Ϊ�յ�ʱ��
          *          (5)TIDYING->Terminated:�����ӷ���terminated()���ʱ
          *      ������״̬Ϊterminatedʱ����awaitTermination()�����еȴ����߳̽��᷵��(return)
          *      ������SHUTDOWN״̬ʱ���ж�SHUTDOW��TIDYINGҪ����Ԥ�ϵظ���ֱ��һЩ����Ϊ������Ϊ�յ������Ҳ���ܻ��ò�Ϊ��
          *      ��������Ȼ�����ǵ�����Ϊ�յ�����£�������ǹ۲쵽��workCount����0�����ǿ���ֻ������ֹ����
          * 
          */
	 private final AtomicInteger ctl=new AtomicInteger(ctlOf(RUNNING,0));
	 //count_bits����������ʾworkCountռ��һ��int��λ��(32λ)�ĵ�29λ
	 private final static int COUNT_BITS=Integer.SIZE-3;
	 //capacity��ʾ29λ�ܴ���������������workCount�����ֵ���������λΪ1�Ķ�����λ����29λ��1*2^29
	 //00011111111111111111111111111111=00100000000000000000000000000000-1
	 private static final int CAPACITY=1<<COUNT_BITS-1;
	/**
	 * ����״̬runstateռ��int���͵ĸ�3λ
	 */
	 //����״̬RunningΪ -1����29λ����11111111111111111111111111111111����29λΪ11100000000000000000000000000000
	 private static final int  RUNNING=-1<<COUNT_BITS-1;
	 //����״̬SHUTDOWNΪ0����29λ������Ϊ00000000000000000000000000000000
	 private static final int SHUTDOWN=0<<COUNT_BITS-1;
	 //����״̬STOP��ֵΪ1����29λ��λ00100000000000000000000000000000
	 private static final int STOP=1<<COUNT_BITS;
	 //����״̬TIDYING��ֵΪ2����29λ��λ01000000000000000000000000000000
	 private static final int TIDYING=2<<COUNT_BITS;	
	 //����״̬TERMINATED��ֵΪ3����29λ��λ01100000000000000000000000000000
	 private static final int TERMINATED=1<<COUNT_BITS;
	 
	 
	 /**
	  * ��װ����װctl
	  */
	 /**
	  * �÷���������ȡrunState��ֵ��Capacity��λȡ��Ϊ11100000000000000000000000000000
	  * �봫��c(��ctl)���룬������29λ���㣬��������λ��RUNSTATE��ֵ
	  * @param c
	  * @return
	  */
	   private static int runStateOf(int c){
		   return c & ~CAPACITY;
	   }
	   /**
		  * �÷���������ȡworkCount��ֵ��CapacityΪ00011111111111111111111111111111
		  * �봫��c(��ctl)���룬������29λ����������λ���㼴�ɵõ�workCount��ֵ
		  * @param c
		  * @return
		  */
	   private static int workCountOf(int c){
		   return c&CAPACITY;
	   }
	   /**
	    * �������Ϊ�Ѿ��ƹ�ֵ��rs(runstate)��wc(workCount),���л����������������������һ��int��
	    * rs��䷵��ֵ�ĸ���λ��wc��䷵��ֵ�ĵ�29λ
	    * @param rs
	    * @param wc
	    * @return
	    */
	   private static int ctlOf(int rs,int wc){
		   return rs | wc;
	   }
	   /**
	    * �ж��̳߳ص�ǰ״̬�Ƿ�������״̬
	    * @param c
	    * @return
	    */
	   private static boolean isRunning(int c){
		   return c<SHUTDOWN;
	   }
	   /**
	    * �жϵ�ǰ����״̬�Ƿ���stop
	    * @param c
	    * @param s
	    * @return
	    */
	   private static boolean runStateAtLeast(int c,int s ){
		   return c>s;
	   }
	   /**
	    * �жϵ�ǰ����״̬�Ƿ�����Stop
	    * @param c
	    * @param s
	    * @return
	    */
	   private static boolean runStateLessThan(int c,int s){
		   return c<s;
	   }
	   /**���Ϊfalse����ôcore�̼߳�ʹ���ã�Ҳ�ܴ��;
	    * ���Ϊtrue,��ôcore�̳߳�ʱ�ȴ�����ʱ��ΪkeepAliveTime
	    * */
	    private volatile boolean allowCoreThreadTimeOut;
	    private  volatile int corePoolSize;
		private  volatile int maximumPoolSize;
		/**Ϊ����core�̵߳ȴ��������õĳ�ʱֵ(nanoseconds)�������ʱ����ֹ
		 * ���pool���г���corePoolSize�������allowCoreThreadTimeOutΪtrue,�߳̽���ʹ�������ʱֵ�������߳̽�����Զ�ȴ�����
		 * */
		private volatile long keepAliveTime;
		/**
		 * �ö��������������񣬲������񽻽Ӹ��߳�
		 */
		private final BlockingQueue<Runnable> workQueue;
		/**
		 * mainLock���з���workers set �����bookkeeping��Ȩ�ޡ�
		 * mainLock���ĳ�ִ���Ĳ���set��˵�Ǹ��õ�һ��ѡ������һ��ԭ����lockʹ�÷���interruptedIdleWorks()�������л���
		 * ���ܹ�����û��Ҫ���жϷ籩�������Ǵ���shutdown״̬ʱ�������˳����̻߳Ტ�����ж���Щ��û���жϵ��߳�;
		 * ��Ҳ�ܼ�largestPoolSize��ĳЩ��������bookkeeping��Ϊ��ȷ��workes set�ڵ�������ж�����Լ���ʵ�ж�ʱ��ƽ���ԣ�
		 * ����ͬ����Ҫ��shutdown()��shutdownNow()��������mainLock
		 * */
		private final ReentrantLock mainLock=new ReentrantLock();
		
		/**��set�����������̳߳��е�workers��ֻ���ڳ���mainLock��ʱ����ܷ���*/
		private final HashSet<Worker>workers=new HashSet<>();
		
		/**�ȴ�����������֧�ֵȴ�termination*/
		private final Condition termination=mainLock.newCondition();
		
		/**��¼���̳߳��г��ֹ�������߳�����(ÿ�������߳�ʱ������workers��size���ֵ���бȽϣ�������ڣ�����¸�ֵ)
		 * ֻ���ڳ���mainLock��ʱ����ʡ�
		 * */
		private int largestPoolSize;
		/**��¼��������������ֻ����worker thread �ж�ʱ���ܽ��и��£�ֻ�г���mainLockʱ���ܷ���*/
		private static int completedTaskCount;
		private  volatile ThreadFactory factory;
		private volatile RejectedExecutionHandler handler;
		/**
		 * Ĭ��rejected execution handler
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
		 * �ڽ���ĳ��ʱ���ִ��ָ����Runnable���񣬸�������ܱ�һ�����߳�ִ��Ҳ���ܱ��̳߳����Ѵ��ڵ��߳�ִ��
		 */
		public void execute(Runnable command) {
			if(command==null) throw new NullPointerException();
			/**
			 *����ִ�в���:
			 *(1)����̳߳������е��߳�������corePoolSize����ô�ʹ���һ�����̣߳�ָ��������Ϊ���̵߳�һ������
			 *(2)�������ɹ����������(queued),��ô��Ȼ��Ҫ����˫�ؼ�飬���������Ƿ��Ѿ�����һ���߳�(��Ϊ��������һ�μ��ʱ
			 *һ���߳���ȥ�����)�����̳߳عر��ˡ������������¼������״̬�������Ҫ�ػ������ֹͣ�ˣ��˻���Ӳ��������û���̣߳���ô
			 *����������һ���߳�
			 *(3)����������ʧ�ܣ���ô�����½�һ���̣߳����ͬ��ʧ�ܣ���ô�̳߳��Ѿ��رգ���������rejected������
			 */
			int c=ctl.get();//��ȡ����״̬
			if(workCountOf(c)<corePoolSize){//����߳���С��corePoolSize����ô����һ��worker����Ϊworker������߳�ָ��������
				if(addWorker(command, true))
					return;
				c=ctl.get();//������ʧ�ܣ����»�ȡ��ǰ״̬
			}
			if(isRunning(c)&&workQueue.offer(command)){//���Running״̬�����������������workQueue
				int reject=ctl.get();//���¼������״̬
				if(!isRunning(reject)&&remove(command)){//�����Running״̬����ô�Ƴ������񣬳ɹ���ִ��reject
					reject(command);
				}else if(workCountOf(reject)==0){//���Running״̬�����Ƴ�����ʧ�ܣ����ж��̳߳��Ƿ�Ϊ�յ��µģ�����ô������һ����������߳�
					addWorker(null, false);
				}
			}else if(!addWorker(command, false)){//����������������ʧ�ܣ��������һ��worker�����ͬ��ʧ�ܣ���ô�̳߳��Ѿ��ر���
				reject(command);
			}
		}
		
		/**
		 * Ϊָ�������񼤻�rejected handler
		 * @param command
		 */
		final void reject(Runnable command){
			handler.rejectedExecution(command, this);
		}
		/**
		 * ��ִ�г�����ڲ��������Ƴ�������������ڣ����Ӷ������δ��ʼ�����䲻�����С� 
         * �˷���������ȡ��������һ���֡��������޷��Ƴ��ڷ��õ��ڲ�����֮ǰ�Ѿ�ת��Ϊ������ʽ������
         *  ���磬ʹ�� submit �����������ܱ�ת��Ϊά�� Future ״̬����ʽ�����ǣ��ڴ�����£�purge() �����������Ƴ���Щ�ѱ�ȡ���� Future��
		 * @param task
		 * @return
		 */
		public boolean remove(Runnable task){
			boolean removed=workQueue.remove(task);
			tryTerminate();//��ֹ�̳߳��Ѿ�SHUTDOWNͬʱ�Ѿ�empty
			return removed;
		}
		/**
		 * �÷��������ж��ܷ�����µ������ڵ�ǰ���̳߳�״̬�͸����Ľ�����(Ҫô�Ǻ����߳�����Ҫô������߳���)��
		 * �����ӳɹ�����ô�߳���(workCount)Ҳ����Ӧ�����ӣ����ܵػ���һ���µĹ����߽��ᱻ������ִ�У����firstTask������
		 * ���¹������̵߳ĵ�һ��������ִ�У�addWorker �����¼���״���»ط���false:(1)�̳߳�״̬Ϊstopped���߿���ת��Ϊshutdown״̬
		 * (2)�̳߳ش����߳�ʧ�ܵ���Ҫ����ʱ,�̴߳���ʧ�ܿ�������Ϊ�̳߳ط���null���߿�������Ϊ�쳣(ͨ����OutOfMemoryException)
		 * 
		 * @param firstTask ���̳߳��߳�������corePoolSize���ߵ��������п�ӽ�����ʱ����ô�ܶ�workers���ᱻ�����������г�ʼ�����״�����
		 * ��ʼ����ɢ���߳�ͨ����ͨ��preStartCoreThread���������߻ᱻ�������һЩ���������߳�
		 * 
		 * 
		 * @param core
		 */
		private boolean addWorker(Runnable firstTask,boolean core){
			retry:
				for(;;){//��ѭ�����ж�����״̬
					//��ȡ��ǰ����״̬c���̳߳�����״̬
					int c=ctl.get();
					int rs=runStateOf(c);
					
					//���rs���ڵ���shutdownʱ����false�����ҽ���rs==shutdownͬʱfirstTask==null��queue.isEmpty()��Ϊ�յ��������
					//SHUTDOWN״̬���ٽ��������񣬵��ᴦ������е�����
					if(rs>=SHUTDOWN&&!(rs==SHUTDOWN&&firstTask==null&&!workQueue.isEmpty())){
						return false;
					}
					//����������������������(inner)ѭ�����ж��߳���
					for(;;){
						//��ȡ�߳�����������ں��������������(ͨ����������core����ֵ)��ֱ�ӷ���false
						int wc=workCountOf(c);
						if(wc>CAPACITY||wc>(core?corePoolSize:maximumPoolSize)){
							return false;
						}
						//���Խ��߳�����1,����ɹ�����retry,ʧ�����ж����̳߳�����״̬rs�ĸı��������߳������ӵ��µ�
						if(compareAndIncrement(c)){
							break retry;
						}
						//��ȡ��ǰ����״̬ctl�еõ�����״̬���ж�����״̬�Ƿ��Ѿ��ı䣬����ı��ˣ���ô���¿�ʼretry
						if(runStateOf(ctl.get())!=rs){
							continue retry;
						}
						//���е��⣬��˵��casʧ������Ϊ�߳��������ӵ��£���ô���¿�ʼ��ѭ��
					}
				}
		   boolean workerAdded=false;
		   boolean workerStarted=false;
		   Worker w=null;
		   try{
			   final ReentrantLock mainLock=this.mainLock;//�������
			   w=new Worker(firstTask);//����һ��worker��ÿ��������ר�ŵ��߳�������
			   final Thread t=w.thread;//��ȡw�Ĺ����߳�
			   //���t����null����˵��ThreadFactory����ʧ�ܣ�Ҳ���������add worker����
			   if(t!=null){
				   mainLock.lock();//��Ҫ����try�����
				   /**������״̬�£��������״̬
				    * �ж��߳�״̬
				    * */
				   try{
					   int c=ctl.get();//��ȡ����״̬
					   int rs=runStateOf(c);//��ȡ����״̬
					   //�������״̬��ֻ����running״̬����shutdown״̬ʱ��firstTask==null(���÷���shutDown())������£����ܽ�����Ӳ���
					   if(rs<SHUTDOWN || (rs==SHUTDOWN&& firstTask==null)){
						   if(t.isAlive()){//����߳��Ѿ���������״̬�ˣ���ô�׳��쳣
							   throw new IllegalThreadStateException();
						   }
						   workers.add(w);
						   int size=workers.size();
						   if(size>largestPoolSize){
							   largestPoolSize=size;
						   }
						   workerAdded=true;//�����������ɹ�
					   }
				   }finally{
					   mainLock.unlock();
				   }
			   }
			   if(workerAdded){//worker����гɹ��������̣߳�������workerStarted�ɹ�
				   t.start();
				   workerStarted=true;//�߳�������
			   }
		   }finally{
			 if(!workerStarted){
				 addWorkerFailed(w);
			 }  
		   }
		   return workerStarted;
		}
		/**�߳����ʧ�ܣ���(1)��ȡ�� (2)����w�ж��Ƿ��workers���Ƴ���worker(3)decrementWorkerCount()
		 * (4)tryTerminate()���Է�ɾ����worker����Termination״̬
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
		 * ֻ�����߳�ͻȻ�ж�ʱ�ſ��Ե��ã���processWorkerExit()
		 */
		private  void decrementWorkerCount(){
			do{}while(!compareAndDecrementWorker(ctl.get()));
		}
		public static class AbortPolicy implements RejectedExecutionHandler{
          public AbortPolicy(){}
			@Override
			/**
			 * ʼ���׳��쳣��abort:��ֹ
			 */
			public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
				throw new  RejectedExecutionException("Task:"+r.toString()+"rejected from "+executor.toString());
				
			}
			
		}
		
		/**
		 * Worker����Ҫ��������minor bookkeepingһ�����߳����е������жϿ���״̬����ά�֡�
		 * ����̳���AbstractQueuedSynchronizerֻ��Ϊ�˻�ȡ���ͷ�Χ������ִ�е�����
		 * ����ֹ��Щ�����ڻ��ѵȴ�����Ĺ����̵߳�interruptsȥ�ж�������������
		 * ����ʵ����һ����������(non-reentrant)�������������ǲ���ReentrantLock,����Ϊ���ǲ�ϣ�����������ڼ����̳߳ؿ��Ʒ�������setCorePoolSize()ʱ�ٴλ�ȡ����
		 * Ϊ���Ͻ����߳�������ʼһ����������֮ǰinterrupts�����Ǹ���״̬������һ�����������ҵ��߳�����ʱ�������
		 * @author heyw
		 *
		 */
		private final class Worker   extends AbstractQueuedSynchronizer
		implements Runnable{
			
			/**
			 * ������Զ���ᱻ���л����ṩһ��seralVersionUIDΪ�˽�ֹjavac����
			 */
			private static final long serialVersionUID = -9122931407671700319L;
			/**Worker����������̣߳���factoryʧ��ʱΪnull			 */
			final Thread thread;
			/** �������еĳ�ʼ������*/
			Runnable firstTask;
			/**ÿ���̵߳Ĺ������������ */
			volatile long completedTasks;
			
			/**����Worker���ô���Ĳ���firstTask��ThreadFactoryPool�еı���factory */
			public Worker(Runnable firstTask){
				setState(-1);//-1��ʾ��ǰ״̬�Ͻ��ж�
				this.firstTask=firstTask;
				this.thread=getFactory().newThread(this);
			}
			/**
			 * ����ѭ���ŵ��ⲿ���runWorker()�����У��ڲ�����Է����ⲿ�෽��������
			 */
			@Override
			public void run() {
				runWorker(this);
			}
			//Lock������0����unlock,1����lock
			//�������������������ʹ�õķ���isHeldExclusively()��tryAcquire(),tryRelease()
			/**�жϵ�ǰ�߳��Ƿ�ӵ�л�������true��ʾӵ�У���ʱstateΪ1*/
			protected boolean isHeldExclusively(){
				return getState()!=0;
			}
			/**��ȡ��*/
			protected boolean tryAcquire(int unused){
				if(compareAndSetState(0,1)){//�жϵ�ǰ״̬�Ƿ�Ϊ0�������������Ϊ1�������ȡ���Ĺ���
					setExclusiveOwnerThread(Thread.currentThread());//�÷�����AbstractQueuedSynchronizer�еķ���
					return true;
				}
				return false;
			}
			/**�ͷ���*/
			protected boolean tryRelease(int unused){
				setExclusiveOwnerThread(null);
				setState(0);
				return true;
			}
			//�ṩ�����ʹ�õķ��� lock(),unlock(),tryLock(),islock(),interruptIfStarted()
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
		 * ��ҪWorker������ѭ����������Ӧ��һЩ����ʱ�����ϵش�getTasks()������ȡ���񣬲�ִ�У�
		 * (1)���Դ�һ����ʼ����ʼ���У����ͨ��û��Ҫ�Ӷ����л�ȡ��һ����������ֻҪ�̳߳��������У���ô�Ϳ���ͨ������getTasks()���ϻ�ȡ����
		 * ���getTasks()����null,��ôworker�˳�����Ϊ�̳߳�runstate����configuration parameters�ı仯���µģ�
		 * worker�˳�������ԭ������Ϊ�ⲿ�����׳����쳣���£����������������completedAbruptly holds���⽫����processWorkerExit�����ǰ�߳�
		 * (2)�������κ�����֮ǰ����ȡ����Ϊ��ֹ����������ʱ�����̳߳��жϸ����񣬵���clearInterruptsForTaskRun��Ϊ��ȷ��ֻ�е��̳߳ش���stopped״̬��
		 * ��ô��ǰ�̲߳���������interrupt set��
		 * (3)�������κ�����֮ǰ������÷���beforeExecute()���÷������ܻ��׳�һ���쳣���⽫�����߳�����(completedAbruptly Ϊtrueʱ���˳�ѭ��)��������Ҳ�����ᱻ����
		 * (4)���beforeExecute()��������ɣ���ô����task��ͬʱ���׳��������쳣�ۼ���afterExecute()�����С����Ƿֿ�����RuntimeException,Error�Լ��ֶ��쳣��
		 * ��ΪRunnable.run()�������������׳��쳣���������ǽ�������UncaughtExceptionHandlerת����error���κ��׳����쳣���ᵼ���߳�������
		 * (5)��task.run()����ִ����ɺ󣬽����÷���afterExecute(),�������Ҳ���׳��쳣����Ҳ�������߳�������According to JLS Sec 14.20, this exception is the one that
          * will be in effect even if task.run throws
          * �쳣���Ƶ�ʵ��Ч��(net effective)���Ǿ����ܵ��ṩ����ʹ���ߴ��뵼�µ��κ������׼ȷ��Ϣ��
		 * @param worker
		 */
	   final void runWorker(Worker w){
			Thread wt=Thread.currentThread();
			Runnable task=w.firstTask;
			w.firstTask=null;
			w.unlock();//�����ж�
			boolean completedAbruptly=true;
			try{
				 while(task!=null && (task=getTask())!=null){
					 w.lock();
					 //����̳߳ش���stopping,��ôȷ���߳��Ѿ��ж���
					 //����̳߳ز�����stopping����ôȷ���߳�û���жϡ�������Ҫ������״̬���еڶ��μ��Ϊ�˴��������жϱ�־ʱshutDownNow race
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
	    * �÷������ڸ������߳�wt��ִ�и���������task֮ǰ������÷����Ǳ��߳�wt����ģ����ܱ��������³�ʼ��ThreadLocals�������������־
	    * ��ʵ�ֲ�û�����κ��£�����������Խ��ж��ƣ�Ϊ�˺����д�������Ƿ�����������Ҫ����д������β�������super.beforeExecute()
	    * @param wt
	    * @param task
	    */
		private void beforeExecute(Thread wt, Runnable task) {
			
		}
		/**
		 * ��ָ������ִ����ɺ�ִ��������߳̽�����÷��������Throwable��Ϊ�գ���ôThrowable������uncaught(��RuntimeException)����error���⽫�����쳣ͻȻ����ֹ
		 * ��ʵ�ֲ�û�����κ����飬���ǿ����������н��ж��ƣ�ͬ��Ϊ�˺����д�������Ƿ�������Ҫ����д������ʼ������÷���(super.afterExecute());
		 * ��task(FutureTask)��������ʱ�������Ƿ�ͨ����������(�磺submit()),task���󶼻Ჶ��ͱ�����Щ���Դ�����쳣����Щ�쳣���ᴫ�ݸ�afterExecute()�����ϣ����������
		 * ��ô�����������н���ʵ�֡�
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
		 * ��������Ϊһ������������worker��������ͱ�ǣ�ֻ����worker�߳��е��ã�����completedAbrubtlyΪtrue��������ΪworkerCount�Ѿ��������ˣ�׼���˳���
		 * ����Ὣ�̴߳�worker set�г�ȥ�����ҿ��ܻ��ж��̳߳ػ����滻һЩworker�����Ҫô��Щworker�˳�ʱ����Ϊuser task�쳣���£�
		 * ����Ҫô��ʱ����corePoolSize��worker�����л���work queue ��Ϊ�յ���û�ж����work
		 * @param w
		 * @param completedAbruptly
		 */
		private void processWorkerExit(Worker w,boolean completedAbruptly){
			if(completedAbruptly){//���completedAbruptlyΪtrue��˵��workCounterû�б�����
				decrementWorkerCount();
			}
			final ReentrantLock mainLock=this.mainLock;
			mainLock.lock();
			try{
				//����completedTaskCount���Ƴ�w
			       completedTaskCount+=w.completedTasks;
			       workers.remove(w);
			}finally{
				mainLock.unlock();
			}
			//��workers���Ƴ�worker,��Ҫ����tryTerminate()����
			tryTerminate();
			int c=ctl.get();
			//�̳߳�״ֻ̬�д���stop��TYDING����TERMINATED
			if(runStateLessThan(c, STOP)){
				if(!completedAbruptly){
					int min=allowCoreThreadTimeOut?0:corePoolSize;
					if(min==0 && !workQueue.isEmpty()){
						min=1;
					}
					if(workCountOf(c)>min){
						return ;//�����滻
					}
				}
				addWorker(null, false);
			}
		}
		
		/**
		 * ���ShutDown�Լ�pool��queueΪ�գ��������STOP�Լ�poolΪ�գ���ת�䵽TERMINATED״̬;
		 * ����������ʸ��жϵ���������workerCount��Ϊ�㣬��ô�ж�һ�����õ��߳���ȷ��shutdown�źŵĴ��
		 * �÷����ĵ��ñ�������κο��ܵ���termination�ķ���֮���磺��shutdownʱ����work count���ߴӶ������Ƴ�����
		 * Ϊ�˴�ScheduleThreadPoolExecutor�л�ȡ����Ȩ�ޣ��÷���������private��
		 */
		private void tryTerminate() {
			for(;;){
				//�����Ƚ���״̬�ж�
				int c=ctl.get();
				//���״̬��Ϊstop��shutdownʱ����ΪshutdownʱworkQueue��Ϊ�գ����ܽ���terminate
				if(isRunning(c)||runStateAtLeast(c, TIDYING)||(runStateOf(c)==SHUTDOWN&&!workQueue.isEmpty())){
					return ;
				}
				//����̳߳ز�Ϊ�գ������ж�����һ��worker�󷵻�
				if(workCountOf(c)!=0){
					interruptIdleWorkers(ONLY_ONE);
					return ;
				}
				//�������е��⣬�̳߳�״̬�϶�Ϊshutdown��workQueueΪ�ջ���stop��ͬʱ�̳߳�û���߳�
				final ReentrantLock mainLock=this.mainLock;
				mainLock.lock();
				try{
					//������������״̬ΪTIDYING,�ɹ������terminated()����������ʾ�Ѿ���ֹ�̳߳أ�����������״̬Ϊterminated��
					 if(ctl.compareAndSet(c, TIDYING)){
						 try{
							 terminated();
						 }finally{
							  ctl.compareAndSet(c, TERMINATED);
							  termination.signalAll();//֪ͨ���еȴ��̣߳��Ѿ���ֹ�̳߳���	 
						 }
						 return ;
					 }
					 //���casʧ�ܣ�����ѭ������
				}finally{
					mainLock.unlock();
				}
			}
		}
		/**
		 * �÷����ж���Щ�������ڵȴ�task���߳�(����ͨ���Ƿ���Ա�����ȷ��)��������Щ�߳̿��Լ��terminated�����ñ����
		 * ����SecurityExceptions(����������£��߳̿��ܲ��ܱ��ж�)
		 * @param onlyone ���Ϊtrue����ô���ֻ���ж�һ���̡߳���termination����������ʽ����ģ���һ���滹������workers,��ô�÷���ֻ�ܴ�tryTerminate()����
		 * �б����á�����������£�����ж�һ��worker������shutdown�ź��Է����е��̶߳����ڵȴ���
		 * �ж����⣨arbitrary���߳��ܹ�ȷ����Ȼshutdown��������ô�������߳�����Ҳ�ܹ��˳���Ϊ��ȷ������termination,�÷�����Ҫ���������ж�һ���̣߳�
		*����shutdown()�������������߳��Ա��㹻���߳��ܹ������˳��������õȴ����һ����������
		 */
		private void interruptIdleWorkers(boolean onlyone){
			final ReentrantLock mainLock=this.mainLock;
			mainLock.lock();
			try{
				 for(Worker w:workers){
					 Thread t=w.thread;
					 if(!t.isInterrupted()&&w.tryLock()){//�ж���Щ�ȴ�task���߳�
						 try{
							t.interrupt(); 
						 }catch(SecurityException ingore){}
						 finally{
							 w.unlock();
						 }
					 }
					 if(onlyone){//����ж�һ��idle�߳�
						 break;
					 }
				 }
			}finally{
				mainLock.unlock();
			}
			
		}
		
		private static boolean ONLY_ONE=true;
		/**
		 * ���ݵ�ǰ�����趨��ͬ���÷���������Ϊ�������߶�ʱ�ȴ������߷���null,����������¼����������worker�����˳�:
		 * (1)�̳߳�����״̬Ϊstop
		 * (2)�̳߳�����״̬Ϊshutdown����workqueueΪ��
		 * (3)����maximumPoolSize��workers
		 * (4)worker��ʱ�ȴ�һ������ͬʱ��ʱ������ᱻ��ֹ(ͨ��allowCoreThreadTimeOut || workerCount>corePoolSize���ж�),�������ڶ�ʱ����֮ǰ����֮��
		 * @return task������null�����worker�����˳����������workerCount�������
		 */
		private Runnable getTask() {
		    boolean timeOut=false;//�ϴ�poll()�Ƿ��Ѿ���ʱ��
		    
		    retry:
		    	//��ѭ�����жϵ�ǰ����״̬�Ƿ�������״̬�󣬽�����ѭ��
		    	for(;;){
		    		   int c=ctl.get();//��ȡ��ǰ����״̬
		    		   int rs=runStateOf(c);//��ȡ��ǰ����״̬
		    		 //  int ws=workCountOf(c);//�˴�����ȡ��ǰ�߳�������Ϊû�����壬�����Ϊrunning״̬�򷵻أ�ws�Ͳ�������
		    		   //�������״̬Ϊstop�����ϻ���Ϊshutdown��workQueue��Ϊ�գ�����ٹ����߳�����������null
		    		   if((rs>=SHUTDOWN) &&(rs>=STOP||workQueue.isEmpty())){
		    			   decrementWorkerCount();
		    			   return null;
		    		   }
		    		   boolean timed;//���ö�ʱ��־���ж�����ws>corePoolSize����allowCoreThreadTimeOut
		    		   //��ʼ��ѭ��
		    		   for(;;){
		    			   int ws=workCountOf(c);//�ڴ˴���ȡ��ǰ�����߳���
		    			   timed=allowCoreThreadTimeOut||(ws>corePoolSize);//�ж��Ƿ�timed
		    			   //�ж��Ƿ����������ѭ������ȡ��������ΪwsС������߳����Ҳ�û�г�ʱ(timeOut&&timed)=true
		    			   if(ws<=maximumPoolSize&&!(timeOut&&timed)){
		    				   break;
		    			   }
		    			   //cas���Լ��ٹ����̣߳����ʧ�������ԭ��ѡ��������ѭ��������ѭ��
		    			   if(compareAndDecrementWorker(c)){
		    				   return null;//�ɹ�����null
		    			   }
		    			   //���casʧ�ܣ�ʧ������Ϊrs�仯������ѭ��
		    			   c=ctl.get();//���»�ȡ����״̬c
		    			   if(runStateOf(c)!=rs){
		    				   continue retry;
		    			   }
		    			   //���casʧ�ܣ�����Ϊ�����߳������ı仯����ѭ��
		    		   }
		    		   try{
		    		   //����ѭ������ʼ��ȡ���������Ƿ�timed��ѡ���������poll��������take()
		    		   Runnable result=timed?workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS):workQueue.take();
		    		   //�ж�result�Ƿ�Ϊ��
		    		   if(result!=null){
		    			   return result;
		    		   }
		    		   //���Ϊ�գ����ж�Ϊ�Ѿ���ʱ
		    		   timeOut=true;
		    		   }catch(InterruptedException retry){//�쳣����������ѭ��
		    			   timeOut=false;
		    		   }
		    	}
			
		}
		
		/**
		 * ���̳߳���ֹʱ���ø÷������÷���Ĭ��ʵ�ֲ����κ��¡�note:Ϊ�˺���Ƕ�״�����д������ʵ����Ҫʹ�ø÷���������super.terminated()����
		 */
		private void terminated(){}
	
}
