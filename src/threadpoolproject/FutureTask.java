package threadpoolproject;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * 可取消的异步计算。该类提供了对Future的基本实现，利用方法来开始和取消计算，查询计算是否完成，获取计算的结果。
 * 只有当计算完成时才可以获取计算结果。如果计算还没有完成，那么方法get()将会进入阻塞，一旦获取了计算结果，
 * 那么该任务将不会在被启动和取消（除非是通过runAndReset()方法激活计算的）
 * FutureTask同时实现了Runnable和Future，因为实现了Runnable，因此该FutureTask可以submit给Executor来执行；
 * 此外作为一个独立的类，FutureTask还提供了protected功能，该功能对于创建自定义任务时很有用
 * @author heyw
 *
 */
public class FutureTask<V>  implements RunnableFuture<V> {
	/**
      * 该任务的运行状态，初始值是NEW;运行状态只能在方法set,setException,cancel中转换成最终状态；
      * 运行状态可能取暂时值COMPLETING(当正在设置结果时)或者INTERRUPTING(当为了满足cancel(true)时去中断一个运行的线程时)；
      * 因为这些状态都是唯一不可改变的，因此这些从中间态到最终态的转化采用cheaper ordered/lazy writes
      * 可能的状态转换：
      * NEW->COMPLETING->NORMAL
      * NEW->COMPLETING->EXCEPTIONAL
      * NEW->CANCELED
      * NEW->INTERRUPTING->INTERRUPTED
      */
	private  AtomicInteger  state;
	private final static int NEW=0;
	private final static int COMPLETING=1;
	private final static int NORMAL=2;
	private final static int EXCEPTIONAL=3;
	private final static int CANCELED=4;
	private final static int INTERRUPTING=5;
	private final static int INTERRUPTED=6;
	/**潜在(underlying)任务*/
	private Callable<V>callable;
	/**从get()方法返回的结果或者抛出的异常*/
	private Object outcome;
	/**运行callable的线程*/
	private AtomicReference<Thread>  runner;
	/**等待线程的Treiber栈*/
	private AtomicReference<WaitNode> waiters;
	
	
	/**
	 * 为已经完成的任务返回结果或者抛出异常
	 * @param s
	 * @return
	 * @throws ExecutionException
	 */
	@SuppressWarnings("unchecked")
	private V report(int s) throws ExecutionException{
		Object x=outcome;
		if(s==NORMAL){
			return (V)x;
		}
		if(s==CANCELED){
			throw new CancellationException();
		}
		throw new ExecutionException((Throwable)x);
	}
	/**
	 * 创建一个FutureTask，它将在running的条件上执行指定Callable
	 * @param callable
	 */
	public FutureTask(Callable<V> callable) {
		if(callable==null) throw new NullPointerException();
		this.callable=callable;
		state=new AtomicInteger(NEW);
		}

	 public FutureTask(Runnable runnable,V result){
		 this.callable=Executors.callable(runnable, result);
		 this.state=new AtomicInteger(NEW);
	 }
	 
	public boolean  cancel(boolean mayInterruptIfRunning){
       if(state.get()!=NEW) return false;//state只有在new状态下，才能进行取消操作
       if(mayInterruptIfRunning){
    	   if(!state.compareAndSet(NEW ,INTERRUPTING)) return false;//如果设置任务状态失败，则返回false
    	   Thread t=runner.get();//获取运行任务的线程
    	   if(t!=null){
    		   t.interrupt();
    	   }
    	   state.set(INTERRUPTED);
       }else if(!state.compareAndSet(NEW, CANCELED)){
    	   return false;}
       finishCompletion();
       return true;
	}


	public boolean isCancelled() {
		return state.get()>=CANCELED;
	}

	public boolean isDone() {
		return state.get()!=NEW;
	}

	public V get() throws InterruptedException, ExecutionException {
		 int s=state.get();
		 if(s<COMPLETING){
			 awaitDone(false, 0L);
		 }
		 return (V)report(s);
	}

	public V get(long timeout, TimeUnit unit) throws InterruptedException,
			ExecutionException, TimeoutException {
		int s=state.get();
		if(unit==null) throw new NullPointerException();
		if(s<=COMPLETING && (s=awaitDone(true, unit.toNanos(timeout)))<=COMPLETING)
			throw new TimeoutException();
		return (V)report(s);
	}

	public void run() {
		/**run()方法的前提：state为NEW同时runner为null
		 */
		if(state.get()!=NEW || !runner.compareAndSet(null, Thread.currentThread()))
			return ;
		Callable<V>c=this.callable;
		int s=state.get();
		try{
			  if(s==NEW&&c!=null){
				  V result;
				  boolean ran;
				  try{
					  result=c.call();
					  ran=true;
				  }catch(Throwable ex){
					  result=null;
					  ran=false;
					  setException(ex);
				  }
				  if(ran){
					  set(result);
				  }
			  }
		}finally{
			//为了防止对run方法并发调用，在state被设置之前保持no-null
			runner=new AtomicReference<>();
			//重新读取state，为了组织中断泄漏
			s=state.get();
			if(s>=INTERRUPTING){
				handlePossibleCancellationInterrupt(s);
			}
		}
	}
	
	/**
	 * 该方法执行计算而无需设置其结果，然后将该Future设置成初始状态，但是如果计算遇到异常或者被取消了，那么操作会失败
	 * 该操作被设计用于那些本质上要执行多次的任务
	 * @return
	 */
	public boolean runAndReset(){
		if(state.get()!=NEW&&!runner.compareAndSet(null, Thread.currentThread()))
		return false;
		Callable<V>c=callable;
		int s=state.get();
		boolean ran=false;
		try{
			if(c!=null && s==NEW){
				try{
				    c.call();
				    ran=true;
				    }catch(Throwable ex){
				    	setException(ex);
				    }
			}
		}finally{
			//为了防止对run方法并发调用，在state被设置之前保持no-null
			runner=new AtomicReference<>();
			//重新读取state，为了组织中断泄漏
			s=state.get();
			if(s>=INTERRUPTING){
				handlePossibleCancellationInterrupt(s);
			}
		}
		return ran&&s==NEW;
	}
	/**
	 * 除非此Future已经设置或者取消，否则将其结果值设置为给定的值。在计算完成时通过run()方法将从内部调用此方法
	 * @param v
	 */
	protected void set(V v){
		if(state.compareAndSet(NEW, COMPLETING)){
			outcome=v;
			state.set(NORMAL);//最终状态
			finishCompletion();
		}
	}
	/**
	 * 除非此Future已经被设置或者取消，否则它将报告一个ExecutionException,并将其原因设置为给定的Throwable。在计算失败时run()内部调用该方法
	 * @param t
	 */
	protected void setException(Throwable t){
		if(state.compareAndSet(NEW, COMPLETING)){
			outcome=t;
			state.set(EXCEPTIONAL);//最终状态
			finishCompletion();
		}
	}
	
	/**
	 * 确保来自可能的cancel(true)方法的任何中断，只能传递给处于run()或者runAndReset()方法中的任务
	 * @param s
	 */
	private void handlePossibleCancellationInterrupt(int s){
		if(s==INTERRUPTING)
			while(state.get()==INTERRUPTING)
				Thread.yield();
	}
	  /**
     * 以Treiber 栈的形式记录等待线程的简单链表节点
     * st.  See other classes such as Phaser and SynchronousQueue
     * for more detailed explanation.
     */
    static final class WaitNode {
        volatile Thread thread;
        volatile WaitNode next;
        WaitNode() { thread = Thread.currentThread(); }
    }
    
    /**
     * 该类移除并通知所有等得线程，激活done（）方法，并将callable设置为null
     */
     private void finishCompletion(){
    	 //assert state->COMPLETING  断言在state大于completing情况下才能使用
    	 for(WaitNode q;(q=waiters.get())!=null;){//获取最新waiters
    		 if(waiters.compareAndSet(q, null)){//cas失败则获取最新waiters
    			 for(;;){
    				 //移除并通知等待线程
    				 Thread t=q.thread;
    				 if(t!=null){
    					 q.thread=null;
    					 LockSupport.unpark(t);
    				 }
    				 //清理该等待节点
    				 WaitNode next=q.next;
    				 if(next==null) break;
    				 q.next=null;//解除关联，利于gc
    				 q=next;//获取下一个waitnode，重新循环
    			 }
    			 break;
    		 }
    	 }
    	 done();
    	 callable=null;
     }
     
     /**
      * 无论是正常还是通过cancellation完成的任务转为isDone状态时，将会调用该方法;FutureTask的该方法并没有做任何事
      * 但是子类继承时，可以重写该方法，来调用完成callbacks或者bookkeeping(记簿)。注意，可以通过查询此方法实现类的状态，来确定是否真的已经取消了
      */
	protected void done() {
	}
	
	/**
	 * 等待计算完成或者  在 线程中断或者等待超时时中止该任务
	 * @param timed 是否定时获取结果
	 * @param nanos 定时时间长度
	 * @return
	 * @throws InterruptedException 
	 */
	private int awaitDone(boolean timed,long nanos) throws InterruptedException{
		boolean queued=false;//用来判断该节点是否已经进入等待队列
		WaitNode q=null;//压入waiters栈的节点
		final long deadTime=timed?System.nanoTime()+nanos:0;
		for(;;){
			if(Thread.interrupted()){
				removeWaiter(q);
				throw new InterruptedException();
			}
			int s=state.get();//获取任务状态
			if(s>COMPLETING){
				if(q!=null)
					q.thread=null;//将完成任务的节点thread设为null，以便清理
				return s;
			}
			if(s==COMPLETING)
				Thread.yield();
			else if(q==null)
				q=new WaitNode();
			else if(!queued)//压入waiters栈,q变成栈顶,q的next变成原来的栈顶
				waiters.compareAndSet(q.next=waiters.get(), q);
			else if(timed){
				 nanos=deadTime-System.nanoTime();
	           if(nanos<=0l){
	        	   removeWaiter(q);
	        	   return state.get();
	           }
	           LockSupport.parkNanos(this,nanos);
			}else{
				LockSupport.park(this);
			}
		}
	}
	/**
	 * 该方法用来移除waiter栈中，所有已经超时或者线程被中断的节点
	 * 超时或者线程被中断的节点其Thread属性都为null，注意点，节点可能存在竟态条件，存现竞态，那么重新遍历该waiters栈
	 * 退出方法唯一条件:遍历到waiters栈尾
	 * @param node
	 */
	private void removeWaiter(WaitNode node){
		if(node!=null){
			node.thread=null;//将需要移除的节点的Thread属性设为null
			retry:
				for(;;){//如果出现竞态那么重现循环remove，此时将根据waiters属性初始化循环条件，这也是外循环的作用
					for(WaitNode pred=null,q=waiters.get(),s;q!=null;q=s){
						s=q.next;//s代表当前节点的next
						if(q.thread!=null){//如果当前节点q的thread不为null，则判断下一个节点是否符合
							pred=q;
						}else if(pred!=null){//如果当前节点q的thread为null，那么设置前一个节点的next为当前节点的next，来移除当前节点
							pred.next=s;
							if(pred.thread==null) continue retry;
						}else if(!waiters.compareAndSet(q, s)){//如果waiters栈只有一个成员q，那么替换waiters
							continue retry;
						}
					}
				break;
				}		
		}
	}
}
