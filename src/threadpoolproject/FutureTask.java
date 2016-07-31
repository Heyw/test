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
 * ��ȡ�����첽���㡣�����ṩ�˶�Future�Ļ���ʵ�֣����÷�������ʼ��ȡ�����㣬��ѯ�����Ƿ���ɣ���ȡ����Ľ����
 * ֻ�е��������ʱ�ſ��Ի�ȡ��������������㻹û����ɣ���ô����get()�������������һ����ȡ�˼�������
 * ��ô�����񽫲����ڱ�������ȡ����������ͨ��runAndReset()�����������ģ�
 * FutureTaskͬʱʵ����Runnable��Future����Ϊʵ����Runnable����˸�FutureTask����submit��Executor��ִ�У�
 * ������Ϊһ���������࣬FutureTask���ṩ��protected���ܣ��ù��ܶ��ڴ����Զ�������ʱ������
 * @author heyw
 *
 */
public class FutureTask<V>  implements RunnableFuture<V> {
	/**
      * �����������״̬����ʼֵ��NEW;����״ֻ̬���ڷ���set,setException,cancel��ת��������״̬��
      * ����״̬����ȡ��ʱֵCOMPLETING(���������ý��ʱ)����INTERRUPTING(��Ϊ������cancel(true)ʱȥ�ж�һ�����е��߳�ʱ)��
      * ��Ϊ��Щ״̬����Ψһ���ɸı�ģ������Щ���м�̬������̬��ת������cheaper ordered/lazy writes
      * ���ܵ�״̬ת����
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
	/**Ǳ��(underlying)����*/
	private Callable<V>callable;
	/**��get()�������صĽ�������׳����쳣*/
	private Object outcome;
	/**����callable���߳�*/
	private AtomicReference<Thread>  runner;
	/**�ȴ��̵߳�Treiberջ*/
	private AtomicReference<WaitNode> waiters;
	
	
	/**
	 * Ϊ�Ѿ���ɵ����񷵻ؽ�������׳��쳣
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
	 * ����һ��FutureTask��������running��������ִ��ָ��Callable
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
       if(state.get()!=NEW) return false;//stateֻ����new״̬�£����ܽ���ȡ������
       if(mayInterruptIfRunning){
    	   if(!state.compareAndSet(NEW ,INTERRUPTING)) return false;//�����������״̬ʧ�ܣ��򷵻�false
    	   Thread t=runner.get();//��ȡ����������߳�
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
		/**run()������ǰ�᣺stateΪNEWͬʱrunnerΪnull
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
			//Ϊ�˷�ֹ��run�����������ã���state������֮ǰ����no-null
			runner=new AtomicReference<>();
			//���¶�ȡstate��Ϊ����֯�ж�й©
			s=state.get();
			if(s>=INTERRUPTING){
				handlePossibleCancellationInterrupt(s);
			}
		}
	}
	
	/**
	 * �÷���ִ�м������������������Ȼ�󽫸�Future���óɳ�ʼ״̬������������������쳣���߱�ȡ���ˣ���ô������ʧ��
	 * �ò��������������Щ������Ҫִ�ж�ε�����
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
			//Ϊ�˷�ֹ��run�����������ã���state������֮ǰ����no-null
			runner=new AtomicReference<>();
			//���¶�ȡstate��Ϊ����֯�ж�й©
			s=state.get();
			if(s>=INTERRUPTING){
				handlePossibleCancellationInterrupt(s);
			}
		}
		return ran&&s==NEW;
	}
	/**
	 * ���Ǵ�Future�Ѿ����û���ȡ������������ֵ����Ϊ������ֵ���ڼ������ʱͨ��run()���������ڲ����ô˷���
	 * @param v
	 */
	protected void set(V v){
		if(state.compareAndSet(NEW, COMPLETING)){
			outcome=v;
			state.set(NORMAL);//����״̬
			finishCompletion();
		}
	}
	/**
	 * ���Ǵ�Future�Ѿ������û���ȡ����������������һ��ExecutionException,������ԭ������Ϊ������Throwable���ڼ���ʧ��ʱrun()�ڲ����ø÷���
	 * @param t
	 */
	protected void setException(Throwable t){
		if(state.compareAndSet(NEW, COMPLETING)){
			outcome=t;
			state.set(EXCEPTIONAL);//����״̬
			finishCompletion();
		}
	}
	
	/**
	 * ȷ�����Կ��ܵ�cancel(true)�������κ��жϣ�ֻ�ܴ��ݸ�����run()����runAndReset()�����е�����
	 * @param s
	 */
	private void handlePossibleCancellationInterrupt(int s){
		if(s==INTERRUPTING)
			while(state.get()==INTERRUPTING)
				Thread.yield();
	}
	  /**
     * ��Treiber ջ����ʽ��¼�ȴ��̵߳ļ�����ڵ�
     * st.  See other classes such as Phaser and SynchronousQueue
     * for more detailed explanation.
     */
    static final class WaitNode {
        volatile Thread thread;
        volatile WaitNode next;
        WaitNode() { thread = Thread.currentThread(); }
    }
    
    /**
     * �����Ƴ���֪ͨ���еȵ��̣߳�����done��������������callable����Ϊnull
     */
     private void finishCompletion(){
    	 //assert state->COMPLETING  ������state����completing����²���ʹ��
    	 for(WaitNode q;(q=waiters.get())!=null;){//��ȡ����waiters
    		 if(waiters.compareAndSet(q, null)){//casʧ�����ȡ����waiters
    			 for(;;){
    				 //�Ƴ���֪ͨ�ȴ��߳�
    				 Thread t=q.thread;
    				 if(t!=null){
    					 q.thread=null;
    					 LockSupport.unpark(t);
    				 }
    				 //����õȴ��ڵ�
    				 WaitNode next=q.next;
    				 if(next==null) break;
    				 q.next=null;//�������������gc
    				 q=next;//��ȡ��һ��waitnode������ѭ��
    			 }
    			 break;
    		 }
    	 }
    	 done();
    	 callable=null;
     }
     
     /**
      * ��������������ͨ��cancellation��ɵ�����תΪisDone״̬ʱ��������ø÷���;FutureTask�ĸ÷�����û�����κ���
      * ��������̳�ʱ��������д�÷��������������callbacks����bookkeeping(�ǲ�)��ע�⣬����ͨ����ѯ�˷���ʵ�����״̬����ȷ���Ƿ�����Ѿ�ȡ����
      */
	protected void done() {
	}
	
	/**
	 * �ȴ�������ɻ���  �� �߳��жϻ��ߵȴ���ʱʱ��ֹ������
	 * @param timed �Ƿ�ʱ��ȡ���
	 * @param nanos ��ʱʱ�䳤��
	 * @return
	 * @throws InterruptedException 
	 */
	private int awaitDone(boolean timed,long nanos) throws InterruptedException{
		boolean queued=false;//�����жϸýڵ��Ƿ��Ѿ�����ȴ�����
		WaitNode q=null;//ѹ��waitersջ�Ľڵ�
		final long deadTime=timed?System.nanoTime()+nanos:0;
		for(;;){
			if(Thread.interrupted()){
				removeWaiter(q);
				throw new InterruptedException();
			}
			int s=state.get();//��ȡ����״̬
			if(s>COMPLETING){
				if(q!=null)
					q.thread=null;//���������Ľڵ�thread��Ϊnull���Ա�����
				return s;
			}
			if(s==COMPLETING)
				Thread.yield();
			else if(q==null)
				q=new WaitNode();
			else if(!queued)//ѹ��waitersջ,q���ջ��,q��next���ԭ����ջ��
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
	 * �÷��������Ƴ�waiterջ�У������Ѿ���ʱ�����̱߳��жϵĽڵ�
	 * ��ʱ�����̱߳��жϵĽڵ���Thread���Զ�Ϊnull��ע��㣬�ڵ���ܴ��ھ�̬���������־�̬����ô���±�����waitersջ
	 * �˳�����Ψһ����:������waitersջβ
	 * @param node
	 */
	private void removeWaiter(WaitNode node){
		if(node!=null){
			node.thread=null;//����Ҫ�Ƴ��Ľڵ��Thread������Ϊnull
			retry:
				for(;;){//������־�̬��ô����ѭ��remove����ʱ������waiters���Գ�ʼ��ѭ����������Ҳ����ѭ��������
					for(WaitNode pred=null,q=waiters.get(),s;q!=null;q=s){
						s=q.next;//s����ǰ�ڵ��next
						if(q.thread!=null){//�����ǰ�ڵ�q��thread��Ϊnull�����ж���һ���ڵ��Ƿ����
							pred=q;
						}else if(pred!=null){//�����ǰ�ڵ�q��threadΪnull����ô����ǰһ���ڵ��nextΪ��ǰ�ڵ��next�����Ƴ���ǰ�ڵ�
							pred.next=s;
							if(pred.thread==null) continue retry;
						}else if(!waiters.compareAndSet(q, s)){//���waitersջֻ��һ����Աq����ô�滻waiters
							continue retry;
						}
					}
				break;
				}		
		}
	}
}
