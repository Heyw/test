package concurrent.lock;

import java.nio.channels.InterruptedByTimeoutException;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractOwnableSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;

import javax.jws.Oneway;


/**
 * ��1������Ϊʵ���������Ƚ��ȳ� (FIFO) �ȴ����е������������ͬ�������ź������¼����ȵȣ��ṩһ����ܡ�
 * ��������Ŀ���ǳ�Ϊ��������ԭ����intֵ������״̬�Ĵ����ͬ������
 * ���û�����basis��;������붨���ܸı�״̬��protected����������������״̬���ڴ˶�����ζ�Ż�ȡ�����ͷţ�������Щ��������ô���������Ϳ���ʵ���Ŷ��Լ��������ơ�
 * �������ά������״̬�ֶΣ�����Ϊ��ʵ��ͬ����Ŀ�ģ�ֻ��ͨ��ʹ��getState(),setSate()�Լ�compareAndSetState()��ԭ�ӵĸ��´���״̬��intֵ��
 * ��2��������뱻����Ϊ�ǹ�����non-public���ڲ������࣬���ǿ�������ʵ���ڲ�������ͬ�����ԡ�AbstractQueuedSynchronizer��ʵ���κ�ͬ���Ľӿڣ�����
 * ������һЩ����acquireInterruptibly�ķ��������ʵ�ʱ�򱻾�����������ͬ����������ʵ�����ǹ���(public)����;
 * ��3������֧�ֻ���ģʽ����ռ���͹���ģʽ������ͬʱ֧��������ģʽ�����ڶ�ռģʽ��ʱ�������߳���ͼ��ȡ�������޷�ȡ�óɹ���
 * �ڹ���ģʽ�£�����̻߳�ȡĳ�������ܣ�������һ�������óɹ���
 * ���ಢ�����˽⡱��Щ��ͬ�����˻�е����ʶ�����ڹ���ģʽ�³ɹ���ȡĳһ��ʱ����һ���ȴ��̣߳�������ڣ�Ҳ����ȷ���Լ��Ƿ���Գɹ���ȡ������
 * ���ڲ�ͬģʽ�µĵȴ��߳̿��Թ�����ͬ�� FIFO ���С�ͨ����ʵ������ֻ֧������һ��ģʽ��������ģʽ�������ڣ����磩ReadWriteLock �з������á�
 * ֻ֧�ֶ�ռģʽ����ֻ֧�ֹ���ģʽ�����಻�ض���֧��δʹ��ģʽ�ķ����� 
 * ��4������ͨ��֧�ֶ�ռģʽ�����ඨ����һ��Ƕ�׵� AbstractQueuedSynchronizer.ConditionObject �࣬���Խ���������� Condition ʵ�֡�
 * isHeldExclusively() ����������ͬ�����ڵ�ǰ�߳��Ƿ��Ƕ�ռ�ģ�ʹ�õ�ǰ getState() ֵ���� release(int) �����������ȫ�ͷŴ˶���
 * ������������״ֵ̬����ô acquire(int) �������Խ��˶������ջָ�Ϊ����ǰ��ȡ��״̬��û�б�� AbstractQueuedSynchronizer ��������������������
 * ��ˣ�����޷������Լ������Ҫʹ������AbstractQueuedSynchronizer.ConditionObject ����Ϊ��Ȼȡ������ͬ����ʵ�ֵ����塣 
 * ��5������Ϊ�ڲ������ṩ�˼�顢���ͼ��ӷ�������Ϊ condition �����ṩ�����Ʒ�����
 * ���Ը�����Ҫʹ��������ͬ�����Ƶ� AbstractQueuedSynchronizer ����Щ��������������
 * ��6����������л�ֻ�洢ά��״̬�Ļ���ԭ����������������л��Ķ���ӵ�пյ��̶߳��С�
 * ��Ҫ�����л��ĵ������ཫ����һ�� readObject �������÷����ڷ����л�ʱ���˶���ָ���ĳ����֪��ʼ״̬��
 * @author heyw
 *
 */
public abstract class AbstractQueuedSynchronizer extends AbstractOwnableSynchronizer implements java.io.Serializable{


	private static final long serialVersionUID = -5643646764308509643L;
	/**
	 * �ȴ����нڵ���
	 * ��1���õȴ�������"CLH"�����еı��壬��CLH����������Ҫ���������������������ǽ�����������ͬ������
	 * ��������ʹ��һ���Ļ����ֶ�����ȡ����ǰ�νڵ��е��̵߳Ŀ�����Ϣ��
	 * һ���ڵ㵱ǰ�νڵ��ͷ�ʱ�����ᱻ֪ͨ���������κγ��е����ȴ��̵߳Ľڵ㶼������specific-notification-style�ļ�������״̬�ֶβ����Ա�֤���������߳̽��п��ƣ�
	 * ���һ���߳�λ�ڶ����еĵ�һλ����ô���п��ܻ�ȡ���������Ⲣ����ζ��һ���ܳɹ���ȡ����ֻ�����ʸ�ȥ�����������Ե�ǰ�ͷ����ľ����߳�Ҳ����Ҫ�ȴ�����
	 * ��2��Ϊ�˽���һ��CLH���У�ֻ��Ҫ�ڶ�β��ƴ��һ���½ڵ㣬Ϊ�˴Ӹö��г�ȥ��ֻ������ͷ���
	 * ��3����CLH���в���Ԫ�أ�ֻ��Ҫ�ڶ�β����һ��ԭ���ԵĲ�������������Ӷ��⵽������һ��ԭ�ӷֽ�㣬���Ƶس���ֻ��Ҫ��head���и��²�����
	 * Ȼ���ڵ�Ҳ��Ҫ���Ѹ���һ��ʱ�����ж����ļ��������ĸ�������ʱ����Ϊ�˴������ڳ�ʱ�����жϵ��¿��ܵ�ȡ����
	 * ��4��prev������Ҫ��������ȡ�������һ���ڵ�ļ����߱�ȡ���ˣ���ô�ýڵ���Ҫ��������һ��δ��ȡ���Ľڵ�
	 * ��5��next����Ҳ��Ϊ��ʵ���������ƣ�����ÿ���ڵ���߳�id�����������Լ��Ľڵ����棬���ǰ�νڵ�ͨ��������һ��������֪ͨ������һ���ڵ㣬�Ӷ��������ĸ��̣߳�
	 * ���ڼ��νڵ���жϱ���������ǰ�νڵ�����next�ֶε������ڵ������ͻ�����ڱ�Ҫ��ʱ�����ͨ����һ���ڵ�ļ��νڵ��Ϊnull��ʱ��
	 * ���Զ����µ�β�ڵ�Ӻ������õ������
	 * ��6��cancellation�����㷨����һЩ���ء����Ǳ��뽫һЩ����ȡ���ڵ��Ƴ����������ǿ��ֲܷ���ýڵ㾿�����ڵ�ǰ�ڵ�ǰ���Ǻ��棬
	 * ���������cancellationʱ����unpark�����߽ڵ㣬�����������ӵ��µ�ǰ�νڵ��������д����������ǿ����жϳ�һ�����Գе����ε�δȡ����ǰ�νڵ㡣
	 * ��7��CLH������Ҫһ���ٵ�ͷ���ڵ������������Ǹýڵ㲻���ڹ������д�������Ϊ���û�о������ͻ�����˷ѡ��෴��һ�������˾������ýڵ��Լ�ͷβָ�뽫��
	 * �����á�
	 * ��8����Conditions�ϵȴ����߳�ʹ����ͬ�Ľڵ㣬���ǻ�ʹ��һ�����������link������ΪConditionsֻ���ڳ��л�����ʱ���ܻ�ȡ���������ǿ��Խ��ڵ����һ����ʽ�����У�
	 * ����await��һ���ڵ㽫�ᱻ���뵽condition�����У�����signal,�ڵ㽫�ᱻת�Ƶ��������У�status�ֶε������ֵ���ڱ�ǽڵ㴦���ĸ����С�
	 */
      static final class Node{
    	  /**����Ԥʾһ���ڵ������Թ���ģʽ�ȴ��ı��*/
    	 static final Node SHARED=new Node();
    	 /**����Ԥʾһ���ڵ������Զ�ռģʽ�ȴ��ı��*/
    	 static final Node EXCLUSIVE=null;
    	 
    	 /**��ʾ���߳��Ѿ���ȡ���˵�waitStatusֵ*/
    	 static final int CANCELLED=1;
    	 /**��ʾ�ü������߳���Ҫunpark��waitStatus*/
    	 static final int SIGNAL=-1;
    	 /**��ʾ���߳����ڵȴ�condition��waitStatus�����Ǵ����������У���Ϊ����Condition.await������*/
    	 static final int CONDITION=-2;
    	 /**��ʾ��һ��acquireShare��Ҫ���������ݣ�������������*/
    	 static final int PROGATE=-3;
    	 
    	 /**
    	  * ״̬�ֶΣ�ֻ��ȥ������Щֵ��
    	  * SIGNAL:��ǰ�ڵ�ļ����߽ڵ��Ѿ���������Ҫblocked�����ǵ�ǰ�ڵ��ͷŻ���ȡ��ʱ����unpark�����߽ڵ㡣Ϊ�˱��⾺����acquire()������Ҫ���ȱ�������
    	  * ��Ҫsignal��Ȼ����ԭ��acquire()������ʧ����ô����block
    	  * CANCELLED:����ڵ����ڳ�ʱ�����ж϶���ȡ�����ýڵ㽫����ı����״̬��Ҳ����˵���иýڵ���̲߳����ڱ�������
    	  * CONDITION���ýڵ㴦��һ��condition�����У��������ᱻ����ͬ�����нڵ�ʹ�ã����Ǳ�ת�ƣ���ʱ��״̬�����������ó�0��(������ط�ʹ�����ֵ
    	  * ������ֶε�����ʹ��û�й�ϵ�������ܹ�ʹ�û��Ƽ�)
    	  * PROPAGATE:releaseShared���봫�ݸ������ڵ㣬�⽫��doReleaseShared()�����б����ã�ֻ��head�ڵ����ã����Ա㱣֤���ݵĳ�������ʹ�Ѿ�����������������
    	  * 0��������Ķ���һ����
    	  * 
    	  * waitStatusֵ���ֻ����ÿ��Լ��ʹ�á�û�и�ֵ��ζ��һ���ڵ㲻��Ҫ��signal����˴�����ڵ㲻��ҪΪ��ͨ��ʹ�ö���飬���ֻ��Ϊ��signal��
    	  * ����ֶα���ʼ��Ϊ����Ϊͬ���ڵ㣬����Ϊcondition�ڵ�����Ϊcondition��ͨ��CAS��ʽ���á�
    	  *  	
    	  *  waitStatus��ʾ�ڵ��״̬�����а�����״̬�У�
    	  *    CANCELLED��ֵΪ1����ʾ��ǰ���̱߳�ȡ���� SIGNAL��ֵΪ-1����ʾ��ǰ�ڵ�ĺ�̽ڵ�������߳���Ҫ���У�Ҳ����unpark��
    	  *    CONDITION��ֵΪ-2����ʾ��ǰ�ڵ��ڵȴ�condition��Ҳ������condition�����У�
    	  *    PROPAGATE��ֵΪ-3����ʾ��ǰ�����º�����acquireShared�ܹ�����ִ�У�
    	  *    ֵΪ0����ʾ��ǰ�ڵ���sync�����У��ȴ��Ż�ȡ����

    	  */
    	 AtomicInteger waitStatus=new AtomicInteger();
    	 
    	 /**
    	  * ����ǰ�νڵ㣬��ǰ�ڵ������ýڵ������awaitStatus�����ʱ�ᱻָ�ɣ�����ʱ��Ϊnull��һ��ǰ�νڵ������ȡ��������
    	  * ������Ƿ���һ���ڵ㲻Ϊnull�Ļ�������head�ڵ㾭����Ϊnull������ͨ�����ҵ���һ���ڵ�����ɹ�acquire��ô�ͻ��Ϊhead������ô���������ڡ�
    	  * ȡ�����߳̽�����ɹ�acquire��ͬʱһ���߳�ֻ��ȡ���Լ��Ľڵ㣬������ȡ�������ڵ㡣
    	  */
    	 volatile Node prev;
    	 
    	 /**
    	  * �������νڵ㣬һ��release����ǰ�ڵ�����߳̽���unpark�ýڵ㡣���ʱ���ᱻָ�ɣ�����ȡ���ڵ�ᱻ����������ʱ������Ϊnull��
    	  * ֱ��attachment��enq������������ǰ�ε�next�ֶΣ���˿���next�ֶ�Ϊnull�Ľڵ㲢����ζ�Ŵ��ڶ����е�β����
    	  * Ȼ��next�ֶν����null�Ļ������ǿ���can scan prev's from the tail to double-check(?).
    	  * ȡ���ڵ㽫������next�ֶ�ָ���������������Ϊnull��to make life easier for isOnSyncQueue.
    	  */
    	AtomicReference<Node> next=new AtomicReference<>();
    	 
    	 /**���ڵ������е��̣߳��ڹ������г�ʼ����ʹ�ú󽫱�����Ϊnull*/
    	 volatile Thread thread;
    	 
    	 /**
    	  * ��������һ����condition�ȴ���������ֵSHARED�Ľڵ㣬��Ϊֻ�г��ж�ռģʽʱ�ſ��Խ���condition���У�
    	  * ����������Ҫһ���򵥵Ķ��������ܵȴ�condition�Ľڵ㡣�ڵ���Żᱻת�Ƶ����������re-acquire��ͬʱ����Ϊcondition����ֻ���Ƕ�ռģʽ�ģ�
    	  * ����ͨ��ʹ��һ��ר��ֵ����һ���ֶΣ�����ʾ����ģʽ��
    	  */
    	 Node nextWaiter;
    	 
    
    	    /**����ȴ��ڵ���SHARED����ô����true*/
    	    final boolean isShared(){
    	    	return nextWaiter==SHARED;
    	    }
            
    	    /**��ָ�����ǿ��Ժ��Եģ������ڴ˴��Ĵ�����Ϊ�˰�����������*/
    	    final Node predecessor()throws NullPointerException{
    	    	Node p=prev;
    	    	if(p==null)
    	    		throw new NullPointerException();
    	    	else
    	    		return p;
    	    }
    	    Node(){//������������ʼ����head��shared�ı��
    	    }
    	    
    	    Node(Thread thread,Node mode){//��addWaiterʹ��
    	    	this.thread=thread;
    	    	this.nextWaiter=mode;
    	    }
    	    Node(Thread thread,int waitStatus){//��Conditionʹ��
    	    	this.thread=thread;
    	    	this.waitStatus.set(waitStatus);
    	    }
      }
      
	  /**
	     * �ȴ����е�ͷ��㣬����ʼ������ȥ��ʼ����ֻ��ͨ������setHead()�������޸ġ�ע�⣺���ͷ�����ڣ�����waitStatus���ᱣ֤�����ΪCANCELLED��
	     * ͷ������ǰ���������������еĽڵ㡣
	     */
	    private transient AtomicReference<Node>  head=new AtomicReference<>();

	    /**
	     * �ȴ����е�β�ڵ㣬����ʼ��.  ֻ��ͨ��enq������β����ӵȴ��ڵ����޸ġ�
	     */
	    private transient AtomicReference<Node> tail=new AtomicReference<>();

	    /**
	     *ͬ��״̬��state������ʾ��ǰ״̬�Ƿ�����
	     */
	    private AtomicInteger state=new AtomicInteger();
	    
	    /**���ص�ǰͬ��״̬��state��volatile��volatile��ʹ��ʱ����ֵ�������ڵ�ǰֵ*/
	    protected final int getState(){
	    	return state.get();
	    }
	    /**����ͬ��״̬��ֵ*/
	    protected final void setState(int newState){
	    	state.set(newState);
	    }
	    
	    /**CAS����stateֵ*/
	    protected final boolean casState(int expect,int update){
	    	return state.compareAndSet(expect, update);
	    }
	    
	    /**��ֵ�����ж���ѡ����������ѡ��ʱpark*/
	    static final long spinForTimeOutThreshold=1000L;
	    
	    /**���ڵ������У������Ҫʱ��Ҫ�����г�ʼ��*/
	    private Node enq(Node node){
	    	for(;;){
	    		Node t=tail.get();//
	    		if(t==null){//��βΪ�գ���Ҫ��ʼ����
	    			if(tail.compareAndSet(t, node)){
	    				head=tail;
	    			}
	    		}else{
	    			node.prev=t;
	    			if(tail.compareAndSet(t, node)){//�������ö�β
	    				t.next.set(node);
	    				return t;//����β�ڵ��ǰ�νڵ�
	    			}
	    		}
	    	}
	    }
	    
	    /**Ϊ��ǰ�̺߳�ָ����mode����һ���ڵ㲢�������
	     */
	    private Node addWaiter(Node mode){
	    	Node node=new Node(Thread.currentThread(),mode);
	    	Node pred=tail.get();//һ��ʼ����ٵļ�����У����ʧ����enq���������������
	    	if(pred!=null){
	    		node.prev=pred;
	    		if(tail.compareAndSet(pred, node)){//������гɹ����򷵻�
	    			pred.next.set(node);
	    			return node;
	    		}
	    	}
	    	enq(node);
	    	return node;
	    }
	    
	    /**������ͷ����Ϊnode��ֻ����acquire�����е��ã����ڰ�������������ѹ��û��Ҫ��signal��travels��Ŀ�ģ�����������Ϊnull*/
	    private void setHead(Node node){
	    	head.set(node);
	    	node.thread=null;
	    	node.prev=null;
	    }
	    
	    /**����ĳ���ڵ�ļ����ߣ��������*/
	    private void unparkSuccessor(Node node){
	    	/*��ȡ�ýڵ��waitStatus�����С����˵����Ҫsignal��Ϊ��׼�������������Ϊ0
	    	 * ���waitStatus���ȴ��߳��޸��ˣ���ô������ʧ�ܣ����ǿ��������
	    	 * */
	    	int ws=node.waitStatus.get();
	    	if(ws<0)
	    		node.waitStatus.compareAndSet(ws, 0);//��ͷ���ws����Ϊ0�����ͬ�������е�ͷ����ws��������ô���ܵ��ø÷���
	    	
	    	  /*�ýڵ�ļ����߳���Ҫunpark���̣߳����������Ϊnull����waitStatus�����㣬��ô�Ӷ�β��ǰ�ң��ҵ�һ��û�б�ȡ���ļ�����*/
	    	/*Ϊʲô�Ӻ�����ǰ���һ��ѽڵ㣬�����Ǵ�ǰ�����ΪCLH���к����׷����жϣ����жϵĽڵ��waitStatus��CANCELLED����CANCELLED�Ľڵ�ͻᱻ
	    	 * �߳����У�����߳�?ǰ�νڵ��next����ָ��ýڵ㣬����ָ����һ����CANCELLED�Ľڵ㣬���ýڵ��nextָ������
	    	 * �Ӷ���ǰ����Ѱ�һ���ֲ��ϲ�������Ŀ��ܣ���˴Ӻ���ǰ����*/
		    Node s=node.next.get();
		    if(s==null || s.waitStatus.get()>0){//��̽ڵ㲻���ڻ����Ѿ�ȡ������ô�Ӷ�β�ҵ�����ýڵ������һ����CANCELLED��̽ڵ���Ϊ���ѽڵ�
		    	s=null;
		    	for(Node t=tail.get();t!=null&&t!=node;t=t.prev){
		    		if(t.waitStatus.get()<0){
		    			s=t;
		    		}
		    	}
		    }
		    if(s!=null)
		    LockSupport.unpark(s.thread);//���Ѹ��߳�
	    }
	  
	    /**����ģʽ��release������֪ͨsuccessor����֤propagation������exclusive ģʽ��releaseֻ�ǹ�ϵ�����head��Ҫsignalʱ��ôunparkSuccessor��
	     * */
	    private void doReleaseShared(){
	    	/**
	    	 * ��֤һ��release�Ĵ��ݣ���ʹ�����������ڴ����acquires/release�����ִ�����ͨ���ķ�ʽ���Խ�unparkSuccessorͷ����successor�������Ҫsignal�Ļ���
	    	 * ���������successor����Ҫsignal����ô����״̬Ϊpropagate������֤һ��release��propagation��������
	    	 * ���⣬���ǻ���Ҫ����ѭ����Ϊ�˷�ֹ�������ڽ������ִ���ʱ���������µĽڵ㡣ͬ����������unparkSuccesso��һ�����ǣ�
	    	 * ������Ҫ֪������״̬��cas�Ƿ�ɹ������ʧ����ô���¼��
	    	 */
	    	Node h=head.get();
	    	for(;;){
		    	if(h!=null && h!=tail.get()){//�����㲻Ϊ���Ҳ�����β�ڵ�
		    		int ws=h.waitStatus.get();
		    		if(ws==Node.SIGNAL){//�ýڵ�״̬ΪSIGNAL����ô����Ϊ״̬Ϊ0
		    			if(!h.waitStatus.compareAndSet(Node.SIGNAL, 0))
		    				continue;//���Ͻ�h��waitStatus����Ϊ0
		    			unparkSuccessor(h);
		    		}else if(ws==0&&!h.waitStatus.compareAndSet(0, Node.PROGATE)){//���casʧ������ѭ��
		    			continue;
		    		}
		    	}
		    	if(h==head.get())//���head�ı��ˣ���ô����ѭ���������˳�
		    		break;
	    	}
	    }
	    
	    /**���ö���ͷ�ڵ㣬���Ҽ��ͷ�ڵ��successor�Ƿ���waiting״̬������ǣ���ô���propagate����������Ѿ��������˾ͽ��д��ݻ��ѣ�
	     * ���ѵȴ���������һ���ȴ��ڵ㣩��
	     * �÷�����Ҫ����������ͷ��㣬�ͷ���
	     */
	    private void setHeadAndPropagate(Node node,int propagate){
	    	Node h=head.get();
	    	head.set(node);
	    	/*
	    	 * ����֪ͨ��һ�������еĽڵ㣺propate��������ָʾ�����߱�ǰһ��������¼��ע�⣺���waitStatusʹ�����źż�飬��ΪPROPAGATE����ת���SIGNAL��
	    	 * ͬʱ����һ���ڵ��Թ���ģʽ�ȴ������߿���Ϊnull��
	    	 * ����Щ����б��ز��Կ��������������Ҫ�Ļ��ѣ�����ֻ���ڴ���������acquire/release������²Ż���֣���˴������Ҫ���ڻ��߾����signal
	    	 */
	    	if(h==null || h.waitStatus.get()<0||propagate>0){
	    		Node s=node.next.get();
	    		if(s==null || s.isShared()){
	    			doReleaseShared();
	    		}
	    	}
	    }
	    
	    /*tryAcquire,tryRelease,tryAcquireShared,tryReleaseShared��ʵ����ʵ�ָ������Ĺؼ�������״̬stateҲ����Ҫ
	     * */
	    /**
	     * ��ͼ�ڶ�ռģʽ�»�ȡ����״̬���˷���Ӧ�ò�ѯ�Ƿ��������ڶ�ռģʽ�»�ȡ����״̬������������ȡ���� 
	     * �˷���������ִ�� acquire ���߳������á�����˷�������ʧ�ܣ��� acquire �������Խ��̼߳�����У������û�н���������У���
	     * ֱ���������ĳ���߳��ͷ��˸��̵߳��źš������ô˷�����ʵ�� Lock.tryLock() ������ Ĭ��ʵ�ֽ��׳� UnsupportedOperationException�� 
	     * @param arg-acquire ��������ֵ���Ǵ��ݸ� acquire �������Ǹ�ֵ����������ĳ�������ȴ�����������Ŀ�ϵ�ֵ����ֵ�ǲ���ϵģ����ҿ��Ա�ʾ�κ����ݡ� 
	     * @return
	     * @throws IllegalMonitorStateException-������ڽ��еĻ�ȡ�������ڷǷ�״̬�·��ô�ͬ������������һ�µķ�ʽ�׳����쳣���Ա�ͬ����ȷ���С�
	     */
	    protected boolean tryAcquire(int arg){
	    	throw new UnsupportedOperationException();
	    }
	    
	    /**
	     * ��ͼ����״̬����ӳ��ռģʽ�µ�һ���ͷš��˷�������������ִ���ͷŵ��̵߳��á� Ĭ��ʵ�ֽ��׳� UnsupportedOperationException��
	     * @param arg- release ��������ֵ���Ǵ��ݸ� release �������Ǹ�ֵ����������ĳ�������ȴ�����������Ŀ�ϵĵ�ǰ״ֵ̬����ֵ�ǲ���ϵģ����ҿ��Ա�ʾ�κ����ݡ� 
	     * @return
	     * @throws IllegalMonitorStateException������ڽ��е��ͷŲ������ڷǷ�״̬�·��ô�ͬ������������һ�µķ�ʽ�׳����쳣��һ��ͬ����ȷ���С�
	     */
	    protected boolean tryRealease(int arg){
	    	throw new UnsupportedOperationException();
	    }
	    
	    /**
	     * ��ͼ�ڹ���ģʽ�»�ȡ����״̬���˷���Ӧ�ò�ѯ�Ƿ��������ڹ���ģʽ�»�ȡ����״̬������������ȡ���� 
	     * �˷���������ִ�� acquire �߳������á�����˷�������ʧ�ܣ��� acquire �������Խ��̼߳�����У������û�н���������У���
	     * ֱ���������ĳ���߳��ͷ��˸��̵߳��źš� 
	     * Ĭ��ʵ�ֽ��׳� UnsupportedOperationException�� 
	     * @param arg
	     * @return
	     */
	    protected int tryAcquireShared(int arg){
	    	throw new UnsupportedOperationException();
	    }
	    
	    /**
	     * ��ͼ����״̬����ӳ����ģʽ�µ�һ���ͷš� �˷�������������ִ���ͷŵ��̵߳��á� Ĭ��ʵ�ֽ��׳� UnsupportedOperationException�� 
	     * @param arg
	     * @return
	     */
	    protected boolean tryReleaseShared(int arg){
	    	throw new UnsupportedOperationException();
	    }
	    
	    /**
	     * ������ڵ�ǰ�������õģ��̣߳�ͬ�����Զ�ռ��ʽ���еģ��򷵻� true��
	     * �˷�������ÿ�ε��÷ǵȴ� AbstractQueuedSynchronizer.ConditionObject ����ʱ���õġ����ȴ���������� release(int)�� 
	     * Ĭ��ʵ�ֽ��׳� UnsupportedOperationException���˷���ֻ�� AbstractQueuedSynchronizer.ConditionObject �����ڽ����ڲ����ã�
	     * ��ˣ������ʹ������������Ҫ�������� 
	     * @return
	     */
	    protected boolean isHeldExclusively(){
	    	throw new UnsupportedOperationException();
	    }
	    
	    /**
	     * �÷�����Ҫ�������Ѿ�׷�ӵ����е��̵߳Ľڵ㣨addWaiter��������ֵ������������
	     * ������ǰ���ǰ���ڵ�Ϊͷ��㣬��ô����ͨ������tryAcquire()�����ܷ�������Դ������ɹ�������������ֱ�ӷ���
	     * �÷���������ͬ�������в���ѭ���ȴ���ȡ����Դ
	     * @param node
	     * @param arg
	     * @return
	     */
	    final boolean acquiredQueued(Node node,int arg){//�÷�����ʵ���ǲ���ѭ�����Ի�ȡ����Դ���ʧ�����������״̬
	    	boolean failed=true;
	    	try{
	    		boolean interrupted=false;
	    		for(;;){
	    			Node p=node.prev;
	    			//�˴�Ҳʵ������ͷ����˳�ͬ������
	    			if(p==head.get()&&tryAcquire(arg)){//�����ǰ�νڵ���ͷ��㲢���ܹ���ȡ״̬������ǰ�ڵ�ռ��������ô���õ�ǰ�ڵ�Ϊͷ���
	    				head.set(node);
	    				p.next=null;
	    				failed=false;
	    				return interrupted;
	    			}else if(shouldParkAfterFailedAcquire(p, node)&&parkAndCheckInterrupt()){//���ʧ�������ȴ�״̬
	    				interrupted=true;
	    			}
	    		}
	    	}finally{
	    		if(failed)//�������ֻ���ǵ�ǰ�ڵ��Ѿ��������Դ���߷����쳣
	    			cancelAcquire(node);
	    	}
	    }
	    
	    /**
	     * ȡ��һ����ͼ��ȡ����Դ�Ľڵ�
	     * @param node
	     */
	    private void cancelAcquire(Node node){
	    	if(node==null)
	    		return ;
	    	node.thread=null;//��ȡ���ڵ��߳�����Ϊnull������gc
	    	Node pred=node.prev;
	    	//�����Ѿ�ȡ���Ľڵ�
	    	while(pred.waitStatus.get()>0){
	    		node.prev=pred=pred.prev;
	    	}
	    	//predNext����Ҫ����Ľڵ�
	    	Node predNext=pred.next.get();
	    	
	    	//ԭ���Եؽ�node����Ϊcancelled,������������ڵ�����Դ��������ýڵ㣬������֮ǰ������ڵ�������ڵ����κι���
	    	node.waitStatus.set(Node.CANCELLED);
	    	
	    	if(node==tail.get()&&tail.compareAndSet(node, pred)){//���nodeΪtail����ô����tailΪpred��Ȼ������pred��nextΪnull
	    		pred.next.compareAndSet(predNext, null);
	    	}else{
	    		int ws;
	    		//���pred�ڵ㲻Ϊͷ����ҵȴ�״̬Ϊsignal���߿�������Ϊsignal�������̲߳�Ϊnull����ô����pred��nextΪnode��next�������ͷ�node�ļ���������Դ
	    		if(pred!=head.get()&&((ws=pred.waitStatus.get())==Node.SIGNAL||pred.waitStatus.compareAndSet(ws, Node.SIGNAL))
	    				&&pred.thread!=null){
	    			Node next=node.next.get();
	    			if(next!=null&&next.waitStatus.get()<=0)//���next.waitStatus�������㣬��û�б�ȡ��
	    			   pred.next.compareAndSet(predNext, next);
	    		}else{//���predΪͷ������waitStatus��Ϊsignal����ô�����߳�������Դ����Ҫ�ͷ�
	    			unparkSuccessor(node);
	    		}
	    	}
	    	
	    	node.next.set(node);//help gc
	    }
	    /**
	     * ��鲢���»�ȡ��ʧ�ܵĽڵ㡣�����ǰ�߳���Ҫ��������ô����true��������acquireѭ���У�������Ҫ��signal���ơ�������Ҫpred=node.prev
	     * @param pred
	     * @param node
	     * @return
	     */
	    private static boolean shouldParkAfterFailedAcquire(Node pred,Node node){
	    	int ws=pred.waitStatus.get();
	    	if(ws==Node.SIGNAL){//���ǰ�νڵ�waitStatusΪsignal����ô��ǰ�ڵ��������
	    		/*˵����ǰ�ڵ��Ѿ�����״̬�ˣ���Ҫһ��release��signal�������ǿ��Խ���ǰ�ڵ�park*/
	    		return true;
	    	}
	    	if(ws>0){//���ǰ�νڵ��Ѿ�ȡ������ô�����ýڵ㲻����ǰѭ����ֱ���ҵ�û��ȡ����ǰ�νڵ㣬��������Ϊnode��ǰ�νڵ�
	    		do{
	    		     node.prev=pred=pred.prev;
	    		     }while(pred.waitStatus.get()>0);
	    		pred.next.set(node);
	    	}else{
	    		/*waitStatus������0����PROPAGATE������������Ҫһ��signal������park����Ҫ���³�����ȷ����parking֮ǰ���ܻ�ȡ��*/
	    		pred.waitStatus.compareAndSet(ws, Node.SIGNAL);//��δ���ȷ����ͬ�������еĽڵ�״̬�����϶���SIGNAL
	    	}
	    	/*������Ҫ����false������Ϊǰ�νڵ��ʱ�п����Ѿ��ͷ����ˣ�������waitStatus���ͷ���ʱ��δ������ΪSIGNAL��δ�������ѵȴ��̲߳���
	    	 * ��˱��뷵��false�����»�ȡһ����*/
	    	return false;
	    }
	    
	    /**
	     * ����ǰ�߳�������ͬʱ����Ƿ��߳��Ƿ��ж�
	     * @return
	     */
	    private final boolean parkAndCheckInterrupt(){//����������ǰ�̣߳�Ȼ�󷵻ص�ǰ�Ƿ��Ѿ��ж��ˣ�ͬʱ����жϱ�־
	    	LockSupport.park(this);
	    	return Thread.interrupted();
	    }
	    
	    /**�жϵ�ǰ�̵߳ķ���*/
	    private static void selfInterrupt(){
	    	Thread.currentThread().interrupt();
	    }
	    
	    /**
	     * �Զ�ռģʽ��ȡ���󣬺����жϡ�ͨ�����ٵ���һ�� tryAcquire(int) ��ʵ�ִ˷��������ڳɹ�ʱ���ء�
	     * �����ڳɹ�֮ǰ��һֱ���� tryAcquire(int) ���̼߳�����У��߳̿����ظ��������򲻱�����������ʹ�ô˷�����ʵ�� Lock.lock() ������
	     * @param arg
	     */
	    public final void acquire(int arg){
	    	if(!tryAcquire(arg)&&acquiredQueued(addWaiter(Node.EXCLUSIVE), arg))//�����ȡ��ʧ������ӵȴ��ڵ㵽ͬ������
	    		selfInterrupt();
	    }
	    /**
	     * �Զ�ռģʽ��ȡ����������ж�����ֹ��
	     * ͨ���ȼ���ж�״̬��Ȼ�����ٵ���һ�� tryAcquire(int) ��ʵ�ִ˷��������ڳɹ�ʱ���ء�
	     * �����ڳɹ�֮ǰ�������̱߳��ж�֮ǰ��һֱ���� tryAcquire(int) ���̼߳�����У��߳̿����ظ��������򲻱�������
	     * ����ʹ�ô˷�����ʵ�� Lock.lockInterruptibly() ������
	     * @param arg
	     * @throws InterruptedException
	     */
	    public final void acquireInterruptly(int arg) throws InterruptedException{
	    	if(Thread.interrupted())
	    		throw new InterruptedException();
	    	if(!tryAcquire(arg)){
	    		doAcquireInterruptily(arg);
	    	}	
	    }
	    
	    private void doAcquireInterruptily(int arg) throws InterruptedException{
	    	boolean failed=true;
	    	Node node=addWaiter(Node.EXCLUSIVE);
	    	try{
	    		for(;;){
		    		Node p=node.predecessor();
		    		if(p==head.get()&&tryAcquire(arg)){//�ɹ���ȡ��
		    			head.set(node);
		    			p.next=null;
		    			failed=false;
		    			return;
		    		}else if(shouldParkAfterFailedAcquire(p, node)&&parkAndCheckInterrupt()){
		    			throw new InterruptedException();
		    		}
		    	}
	    	}finally{
	    		if(failed)
	    			cancelAcquire(node);
	    	}
	    }
	    
	    private boolean doAcquireNanos(int arg,long nanos) throws InterruptedException{
	    	boolean failed=true;
	    	Node node=addWaiter(Node.EXCLUSIVE);
	    	long lastTime=System.nanoTime();
	    	try{
	    		for(;;){
		    		  Node p=node.predecessor();
		    		  if(p==head.get()&&tryAcquire(arg)){//�ɹ���ȡ��
		    			  head.set(node);
		    			  p.next=null;//help gc
		    			  failed=false;
		    			  return true;
		    		  }
		    		if(nanos<=0)
		    			return false;
		   
		    		if(shouldParkAfterFailedAcquire(p, node)&&nanos>=spinForTimeOutThreshold){
		    			LockSupport.parkNanos(this, nanos);
		    		}
		     		long now=System.nanoTime();
		    		nanos-=now-lastTime;
		    	    lastTime=now;
		    	    if(Thread.interrupted())
		    	    	throw new InterruptedException();
	    		}
	    	}finally{
	    		if(failed)
	    			cancelAcquire(node);
	    	}
	    }
	    
	    /**
	     * �Թ���ģʽ��ȡ���󣬺����жϡ�ͨ�������ȵ���һ�� tryAcquireShared(int) ��ʵ�ִ˷��������ڳɹ�ʱ���ء�
	     * �����ڳɹ�֮ǰ��һֱ���� tryAcquireShared(int) ���̼߳�����У��߳̿����ظ��������򲻱�������
	     * @param arg
	     */
	    public final void acquireShared(int arg){
	    	if(tryAcquireShared(arg)<0)//������Ի�ȡ������ʧ��
	    		doAcquireShared(arg);
	    }
	    
	    /**
	     * �Թ���ģʽ��ȡ����������ж�����ֹ��ͨ���ȼ���ж�״̬��Ȼ�����ٵ���һ�� tryAcquireShared(int) ��ʵ�ִ˷��������ڳɹ�ʱ���ء�
	     * �����ڳɹ����̱߳��ж�֮ǰ��һֱ���� tryAcquireShared(int) ���̼߳�����У��߳̿����ظ��������򲻱������� 
	     * @param arg
	     * @throws InterruptedException
	     */
	    public final void acquireSharedInterruptedly(int arg) throws InterruptedException{
	    	if(Thread.interrupted())
	    		throw new InterruptedException();
	    	if(tryAcquireShared(arg)<0)
	    		doAcquireSharedInterruptedly(arg);
	    }
	    
	    /**
	     * �Թ���ģʽ��ȡ����������ж�����ֹ��ͨ���ȼ���ж�״̬��Ȼ�����ٵ���һ�� tryAcquireShared(int) ��ʵ�ִ˷��������ڳɹ�ʱ���ء�
	     * �����ڳɹ����̱߳��ж�֮ǰ��һֱ���� tryAcquireShared(int) ���̼߳�����У��߳̿����ظ��������򲻱������� 
	     * @param arg
	     * @param nanos
	     * @return
	     * @throws InterruptedException
	     */
	    public final boolean tryAcquireSharedNanos(int arg,long nanos) throws InterruptedException{
	    	if(Thread.interrupted())
	    		throw new InterruptedException();
	    	return tryAcquireShared(arg)>=0|| doAcquireSharedNanos(arg, nanos);
	    }
	    /**
	     * �Թ�������жϵķ�ʽ��ȡ��
	     * @param arg
	     */
	    private void doAcquireShared(int arg){
	    	boolean failed=true;
	    	Node node=addWaiter(Node.SHARED);//��ӹ���node
	    	try{
	    		boolean interrupted=false;
	    		  for(;;){
	    			  final Node p=node.predecessor();//��ȡǰ�νڵ�
	    			  if(p==head.get()){//���ǰ�νڵ�Ϊͷ�ڵ�
	    				  int r=tryAcquireShared(arg);
	    				  if(r>=0){//��ȡ����
	    					  setHeadAndPropagate(node, r);
	    					  p.next=null;
	    					  if(interrupted)//���Ӧ�ù�����ô�жϵ�ǰ�߳�
	    						  selfInterrupt();
	    					  failed=false;
	    					  return ;
	    				  }
	    			  }
	    			  if(shouldParkAfterFailedAcquire(p, node)&&parkAndCheckInterrupt()){//����Ƿ�Ӧ�ù��𣬽�������
	    				  interrupted=true;
	    			  }
	    		  }
	    	}finally{
	    		if(failed)
	    			cancelAcquire(node);
	    	}
	    }
	    
	    /**
	     * �Թ����������жϵķ�ʽ��ȡ��
	     * @param arg
	     * @throws InterruptedException 
	     */
	    private void doAcquireSharedInterruptedly(int arg) throws InterruptedException{
	    	boolean failed=true;
	    	Node node=addWaiter(Node.SHARED);
	    	try{
	    	     final Node p=node.predecessor();//��ȡǰ�νڵ�
	    	     for(;;){
	    	    	 if(p==head.get()){//�ж�ͷ����Ƿ�Ϊ��ǰ�νڵ㣬�������ô���Ի�ȡ��
	    	    		 int r=tryAcquireShared(arg);//���Ի�ȡ��
	    	    		 if(r>=0){//��ȡ��
	    	    			 setHeadAndPropagate(node, r);
	    	    			 p.next=null;//help gc
	    	    			 failed=false;
	    	    			 return;
	    	    		 }
	    	    		 //���û�л�ȡ������ô����ǰ�ڵ���𣬹�������Ƿ��жϣ�����ж��׳��쳣
	    	    		 if(shouldParkAfterFailedAcquire(p, node)&&parkAndCheckInterrupt()){
	    	    			 throw new InterruptedException();
	    	    		 }
	    	    	 }
	    	     }
	    	}finally{
	    		if(failed)
	    			cancelAcquire(node);
	    	}
	    }
	    
	    private boolean doAcquireSharedNanos(int arg,long nanos){
	    	boolean failed=true;
	    	Node node=addWaiter(Node.SHARED);
	    	long lastTime=System.nanoTime();//��ȡʱ��
	    	try{
	    		final Node p=node.predecessor();
	    		for(;;){
	    			if(p==head.get()){//���ǰ�νڵ���head�Ļ�����ô���Ի�ȡ��
		    			int r=tryAcquireShared(arg);
		    			if(r>=0){
		    				setHeadAndPropagate(p, r);//����head���Ҵ��ݻ�����һ���ȴ��̡߳�
		    				p.next=null;//help gc;
		    				failed=false;
		    				return true;
		    			}
		    			if(nanos<=0){
		    				return false;
		    			}
		    			long now=System.nanoTime();
		    			nanos-=now-lastTime;
		    			if(shouldParkAfterFailedAcquire(p, node)&&nanos>spinForTimeOutThreshold){
		    				LockSupport.parkNanos(this, nanos);
		    			}
		    		}
	    		}
	    	}finally{
	    		if(failed)
	    			cancelAcquire(node);
	    	}
	    }
	    
	    /**
	     * �Զ�ռģʽ�ͷŶ������ tryRelease(int) ���� true����ͨ������һ�������̵߳�������ʵ�ִ˷���������ʹ�ô˷�����ʵ�� Lock.unlock() ����
	     * @param arg
	     * @return
	     */
	    public final boolean release(int arg){
	    	if(tryRealease(arg)){//ʵ�������ͷ�����Դ���ͷųɹ�������head����һ��signal�ڵ�
	    		Node h=head.get();
	    		if(h!=null && h.waitStatus.get()!=0)//���ͷ���wsΪ0�����ܻ�����һ���ڵ�
	    			unparkSuccessor(h);
	    		return true;
	    	}
	    	return false;
	    }
	    
       /**
        * �Թ���ģʽ�ͷŶ������ tryReleaseShared(int) ���� true����ͨ������һ�������̵߳�������ʵ�ָ÷�����
        * @param arg
        * @return
        */
	    public final boolean releaseShared(int arg){
	    	if(tryReleaseShared(arg)){
	    		doReleaseShared();
	    		return true;
	    	}
	    	return false;
	    }
	    
	    /**
	     * ��ѯ�Ƿ������ڵȴ���ȡ���κ��̡߳�ע�⣬��ʱ������Ϊ�жϺͳ�ʱ������ȡ������������ true �����ܱ�֤�����κ��̶߳�����ȡ���� 
	     * �ڴ�ʵ���У��ò������Թ̶�ʱ�䷵�ص�
	     * @return
	     */
	    public final boolean hasQueuedThreads(){
	    	return head.get()!=tail.get();
	    }
	    
	    /**
	     * ��ѯ�Ƿ������߳�Ҳ�����Ż�ȡ��ͬ������Ҳ����˵���Ƿ�ĳ�� acquire �����Ѿ������� �ڴ�ʵ���У��ò������Թ̶�ʱ�䷵�صġ� 
	     * @return
	     */
	    public final boolean hasContended(){
	    	return head.get()!=null;
	    }
	    
	    /**
	     * ���ض����е�һ�����ȴ�ʱ����ģ��̣߳����Ŀǰû�н��κ��̼߳�����У��򷵻� null. 
	     * �ڴ�ʵ���У��ò������Թ̶�ʱ�䷵�صģ����ǣ���������߳�Ŀǰ���ڲ����޸ĸö��У�����ܳ���ѭ�����á�
	     * @return
	     */
	    public final Thread getFirstQueuedThread(){
	    	return (head.get()==tail.get())?null:fullGetFirstQueuedThread();
	    }
	    
	    private final Thread fullGetFirstQueuedThread(){
	    	  /*
	         * ��һ���ڵ������������head.next�����Ի�ȡ�ýڵ��Thread�ֶΣ����뱣֤��һ���ԣ����thread�ֶ�Ϊnull������s.prev����ָ��head��
	         * ��ôһЩ�����߳������Ƕ�ȡ�����п��ܲ���ִ����setHead()�������ڱ���֮ǰ��������
	         */
	    	Node h,s;
	    	Thread st;
	    	if(((h=head.get())!=null&&(s=h.next.get())!=null&&s.prev==head.get()&&(st=s.thread)!=null)
	    			||((h=head.get())!=null&&(s=h.next.get())!=null&&s.prev==head.get()&&(st=s.thread)!=null))
	    		return st;

	        /*
	         * Head's next field might not have been set yet, or may have
	         * been unset after setHead. So we must check to see if tail
	         * is actually first node. If not, we continue on, safely
	         * traversing from tail back to head to find first,
	         * guaranteeing termination.
	         * Head��next�ֶο��ܻ�û�б����ã�Ҳ������setHead()���µ�û�����á�������Ǳ�����tail�Ƿ���ʵ�ʵ�һ���ڵ㡣
	         * ������ǣ����Ǽ�����tail��head������Ѱ�ҵ�һ���ڵ�
	         */
	    	Node t=tail.get();
	    	Thread firstThread=null;
	    	while(t!=null&&t!=head.get()){
	    	    Thread tt=t.thread;
	    		if(tt!=null)
	    			firstThread=tt;
	    		t=t.prev;
	    	}
	    	return firstThread;
	    }
	    
	    /**
	     * ��������̵߳ĵ�ǰ�Ѽ�����У��򷵻� true�� ��ʵ�ֽ��������У���ȷ�������߳��Ƿ���ڡ� 
	     * @param thread
	     * @return
	     */
	    public final boolean isQueued(Thread thread){
	    	if(thread==null) throw new NullPointerException();
	    	for(Node p=tail.get();p!=null;p=p.prev){
	    		if(p.thread==thread)
	    			return true;
	    	}
	    	return false;
	    } 
	    
	    /**
	     * ��������Զ��׼��ص�һ��������е��߳���exclusive ģʽ�ģ���ô����true��
	     * ����÷�������true�����Ҹ��̳߳����Թ���ģʽ��ȡ������ô���Ա�֤���̲߳��ǵ�һ��������е��̡߳�
	     * �÷���ֻ������ReentrantReadWriteLock�С�
	     * @return
	     */
	    final boolean apparentlyFirstQueuedIsExcusive(){
	    	Node s,h;
	    	return (h=head.get())!=null&&(s=h.next.get())!=null
	    			   &&!s.isShared()&&s.thread!=null; 
	    }
	    
	    /**
	     * ��ͬ������β����ǰ���Ҹýڵ㣬��������ڷ���false
	     * @param node
	     * @return
	     */
	    final boolean findNodeFromTail(Node node){
	    	Node t=tail.get();
	    	while(t!=null){
	    		if(t==node)
	    			return true;
	    		t=t.prev;
	    	}
	    	return false;
	    }
	    
	    /**�÷��������ж�һ���ڵ��Ƿ���ͬ��������*/
	    final boolean isOnSyncQueued(Node node){
	    	if(node.waitStatus.get()==Node.CONDITION||node.prev==null)
	    		return false;
	    	if(node.next.get()!=null)//���nextΪnull������˵���ýڵ㲻������ͬ�������ϣ������Ϊnull����ô�϶�������������ϡ�
	    		return true;
	    	return findNodeFromTail(node);
	    }
	    
	    /**
	     * �õ�ǰ��״̬����releas���������ʧ���׳�IllegalMonitorException
	     * @param node
	     * @return
	     */
	    final int fullyRelease(Node node){
	    	boolean failed=true;
	    	try{
	    		int savedState=getState();
	    		if(release(savedState)){
	    			failed=false;
	    			return savedState;
	    		}else{
	    			throw new IllegalMonitorStateException();
	    		}
	    	}finally{
	    		if(failed)
	    			node.waitStatus.set(Node.CANCELLED);
	    	}
	    }
	    
	    /**
	     * �÷������ڽ�condition�����еĽڵ�ת�Ƶ�ͬ�������С��ɹ��򷵻�true��ʧ��˵���ýڵ��ڽ���signal֮ǰ�Ѿ�canceled
	     * @param node
	     * @return
	     */
	    final boolean transferForSignal(Node node){
	    	if(!node.waitStatus.compareAndSet(Node.CONDITION, 0)){
	    		return false;
	    	}
	    	Node q=enq(node);//��node���뵽ͬ�����У�����ǰ���ڵ�
	    	int ws=q.waitStatus.get();
	    	/*���ǰ���ڵ��Ѿ�canceled��������waitStatusʧ�ܣ���ô����ǰ���ڵ��̻߳���������ͬ����*/
	    	if(ws>0||!q.waitStatus.compareAndSet(ws, Node.SIGNAL))
	    		LockSupport.unpark(q.thread);
	    	return true;
	    }
	    
	    /**
	     * �����Ҫ�Ļ����ڱ�ȡ���ȴ�֮�󣬽��ڵ�ת�Ƶ�ͬ��������ȥ������ڽ���signal֮ǰת�Ƴɹ����򷵻�true
	     * @param node
	     * @return
	     */
	    final boolean transferAfterCancelledWait(Node node){
	    	if(node.waitStatus.compareAndSet(Node.CONDITION, 0)){//ȡ���ȴ���Ȼ����뵽ͬ�����У���ʱ�ýڵ�waitStatusΪ0
	    		enq(node);
	    		return true;
	    	}
	    	
	    	/*�������ʧȥһ��signal����ô�ڸýڵ����ͬ������֮ǰ���߳��������κ��¡�ͬ����Ϊ��δ���ת����ȡ���Ƿǳ������Ͷ��ݵģ���˲�������*/
	    	while(!isOnSyncQueued(node))
	    		Thread.yield();//����ǰ�̣߳����������߳�
	    	return false;
	    }
	    /**
	     * ��Condition�����У��ڵ��waitStatus��ΪCondition��˵���Ѿ�canceled
	     * @author heyw
	     *
	     */
	    public class ConditionObject implements java.io.Serializable,Condition{
			private static final long serialVersionUID = 5512533359500884566L;
			
			/*Condition�����е�һ���ڵ�*/
			private transient Node firstWaiter;
			/*Condition���������һ���ڵ�*/
			private transient Node lastWaiter;
			
			public ConditionObject(){}
			
			/*���һ���µĽڵ㵽�ȴ�����*/
			private Node addConditionWaiter(){
				Node t=lastWaiter;//��ȡ���һ���ȴ��ڵ�
				if(t!=null&&t.waitStatus.get()!=Node.CONDITION){//���lastWaiter�Ѿ�ȡ���ˣ���ô�޳��ýڵ�
					unlinkCanceledWaiters();
					t=lastWaiter;
				}
				Node node=new Node(Thread.currentThread(),Node.CONDITION);//����һ���µȴ��ڵ�
				 if(t==null){
					 firstWaiter=node;//���lastWaiter����null��˵������Ϊ��
				 }else{
					 t.nextWaiter=node;//��node���������
				 }
				 lastWaiter=node;
				 return node;
			}
			/**
			 * ��������Condition���У����Ѿ�ȡ���Ľڵ��޳���
			 */
			private void unlinkCanceledWaiters(){
				Node t=firstWaiter;
				Node trail=null;//����t����һ���ȴ��ڵ㣬Ϊnull��˵��tΪ��һ���ȴ��ڵ�
				while(t!=null){
					Node next=t.nextWaiter;//��ȡ��һ���ȴ��ڵ�
					if(t.waitStatus.get()!=Node.CONDITION){//�����ǰ�ȴ��ڵ��Ѿ�cancelled����ô�޳��ýڵ�
						t.nextWaiter=null;
						if(trail==null)
							firstWaiter=next;//���firstWaiter�Ѿ�ȡ��������һ���ȴ��ڵ�next����ΪfirstWaiter
						else
							trail.nextWaiter=next;//����һ���ڵ��nextWaiter����Ϊ��һ���ȴ��ڵ�next
						if(next==null){//�����ǰ�ڵ�ΪlastWaiter����ô��lastWaiter����Ϊtrail
						    lastWaiter=trail;	
						}
					}else
						trail=t;
			   t=next;
					
				}
				
			}
			/**
			 * �ڵȴ�״̬ʱ����߳��Ƿ��жϣ����û���жϣ�����0������ж����ҷ�����signal֮ǰ������REINTERRUPT��������signal֮�󣬷���THROW_IE
			 * @param node
			 * @return
			 */
			private  int checkInterruptWhileWaiting(Node node){
				return Thread.interrupted()?
						(transferAfterCancelledWait(node)?REINTERRUPT:THROW_IE):0;
			}
			
			/**
			 * ����mode���ж��̣߳������׳��쳣������ʲô������
			 * @param interruptMode
			 * @throws InterruptedException
			 */
			private final void reportInterrupteAfterWait(int interruptMode) throws InterruptedException{
				if(interruptMode==REINTERRUPT)
					selfInterrupt();
				else if(interruptMode==THROW_IE)
					throw new InterruptedException();
			}
			/**
			 * ��ɵ�ǰ�߳��ڽӵ��źŻ��ж�֮ǰһֱ���ڵȴ�״̬�� 
			 * ��� Condition ��ص�����ԭ�ӷ�ʽ�ͷţ����ҳ����̵߳��ȵ�Ŀ�ģ������õ�ǰ�̣߳����ڷ��������������֮һ ��ǰ����ǰ�߳̽�һֱ��������״̬�� 
			 * ����ĳ���̵߳��ô� Condition �� signal() �������������ɽ���ǰ�߳�ѡΪ�����ѵ��̣߳����� ����ĳ���̵߳��ô� Condition �� signalAll() ������
			 * ��������ĳ���߳��жϵ�ǰ�̣߳���֧���ж��̵߳Ĺ��𣻻��� ��������ٻ��ѡ� 
			 * ����������£��ڴ˷������Է��ص�ǰ�߳�֮ǰ�����������»�ȡ��������йص��������̷߳���ʱ�����Ա�֤ �����ִ����� 
			 * �����ǰ�̣߳� �ڽ���˷���ʱ�Ѿ������˸��̵߳��ж�״̬��
			 * ������֧�ֵȴ����ж��̹߳���ʱ���̱߳��жϣ� ���׳� InterruptedException���������ǰ�̵߳��ж�״̬���ڵ�һ������£�û��ָ���Ƿ����ͷ���֮ǰ�����жϲ��ԡ� 
			 * ʵ��ע������ 
			 * �ٶ����ô˷���ʱ����ǰ�̱߳�������� Condition �й�����������ȡ����ȷ���Ƿ�Ϊ��������Լ�����ʱ����ζԴ�������Ӧ��ʵ�֡�ͨ�������׳�һ���쳣������ IllegalMonitorStateException�����Ҹ�ʵ�ֱ���Դ˽��м�¼�� 
			 * ����Ӧĳ���źŶ����ص���ͨ������ȣ�ʵ�ֿ��ܸ�ϲ����Ӧĳ���жϡ�����������£�ʵ�ֱ���ȷ���źű��ض�����һ���ȴ��̣߳�����еĻ����� 
			 */
			public void await() throws InterruptedException {
				if(Thread.interrupted())
					throw new InterruptedException();
				Node node=addConditionWaiter();//���ӵȴ��ڵ�
				int savedState=fullyRelease(node);//�ͷ�������Դ��������״̬��Ҳ�����Ƴ�ͬ�������еĽڵ㣬���뵽condition������ȥ
				int interruptMode=0;//�ж�ģ��
				while(!isOnSyncQueued(node)){//����ڵ�û�д���ͬ�������У���û���ʸ��������жϸ��̣߳��������һ����ͨ��signal����
					LockSupport.park(this);
					if((interruptMode=checkInterruptWhileWaiting(node))!=0){//����ȴ������У��ж��ˣ���ô����ѭ��
						break;
					}
				}
				//��ǰ�̻߳��Ѻ󣬴��¾�������������������������˯
				if(acquiredQueued(node, savedState)&&interruptMode!=THROW_IE){//
					interruptMode=REINTERRUPT;
				}
				if(node.nextWaiter!=null)
					unlinkCanceledWaiters();
				if(interruptMode!=0)
					reportInterrupteAfterWait(interruptMode);
			}

		    /**
		     * ��ɵ�ǰ�߳��ڽӵ��ź�֮ǰһֱ���ڵȴ�״̬�� ���������ص�����ԭ�ӷ�ʽ�ͷţ����ҳ����̵߳��ȵ�Ŀ�ģ������õ�ǰ�̣߳�
		     * ���ڷ��������������֮һ ��ǰ����ǰ�߳̽�һֱ��������״̬�� 
		     * ����ĳ���̵߳��ô� Condition �� signal() �������������ɽ���ǰ�߳�ѡΪ�����ѵ��̣߳�
		     * ���� ����ĳ���̵߳��ô� Condition �� signalAll() ������
		     * ���� ��������ٻ��ѡ� 
		     * ����������£��ڴ˷������Է��ص�ǰ�߳�֮ǰ�����������»�ȡ��������йص��������̷߳���ʱ�����Ա�֤ �����ִ����� 
		     * ����ڽ���˷���ʱ�����˵�ǰ�̵߳��ж�״̬�������ڵȴ�ʱ���̱߳��жϣ���ô�ڽӵ�signal֮ǰ�����������ȴ��������մӴ˷�������ʱ����Ȼ���������ж�״̬�� 
		     * ʵ��ע������ 
		     * �ٶ����ô˷���ʱ����ǰ�̱߳�������� Condition �й���������
		     * ��ȡ����ȷ���Ƿ�Ϊ��������Լ�����ʱ����ζԴ�������Ӧ��ʵ�֡�ͨ�������׳�һ���쳣������ IllegalMonitorStateException�����Ҹ�ʵ�ֱ���Դ˽��м�¼��
		     */
			public final void awaitUninterruptibly() {
				Node node=addConditionWaiter();//������һ���ȴ��ڵ�
				boolean interrupted=false;//�����ж��߳��Ƿ��жϹ�
				int savedState=fullyRelease(node);//��ȫ�ͷ�����Դ,������״̬
				while(!isOnSyncQueued(node)){//��������ĵȴ��ڵ㲻��ͬ�������ϣ���park��ǰ�߳�
					LockSupport.park(this);
					if(Thread.interrupted())//����߳��жϹ�
						interrupted=true;
				}
				if(acquiredQueued(node, savedState)||interrupted)//���ݵ�ǰ�ڵ����״̬����ȡ��
					selfInterrupt();//�������̱߳��Ϊinterrupted
				
			}
            /**
             * ��ɵ�ǰ�߳��ڽӵ��źš����жϻ򵽴�ָ���ȴ�ʱ��֮ǰһֱ���ڵȴ�״̬�� 
             * ���������ص�����ԭ�ӷ�ʽ�ͷţ����ҳ����̵߳��ȵ�Ŀ�ģ������õ�ǰ�̣߳����ڷ��������������֮һ ��ǰ����ǰ�߳̽�һֱ��������״̬�� 
             * ����ĳ���̵߳��ô� Condition �� signal() �������������ɽ���ǰ�߳�ѡΪ�����ѵ��̣߳�
             * ��������ĳ���̵߳��ô� Condition �� signalAll() ������
             * ��������ĳ���߳��жϵ�ǰ�̣߳���֧���ж��̵߳Ĺ���
             * �����ѳ���ָ���ĵȴ�ʱ�䣻
             * ���߷�������ٻ��ѡ��� 
             * ����������£��ڴ˷������Է��ص�ǰ�߳�֮ǰ�����������»�ȡ��������йص��������̷߳���ʱ�����Ա�֤ �����ִ����� 
             * �����ǰ�̣߳� �ڽ���˷���ʱ�Ѿ������˸��̵߳��ж�״̬��������֧�ֵȴ����ж��̹߳���ʱ���̱߳��жϣ� ���׳� InterruptedException�����������ǰ�̵߳����ж�״̬��
             * �ڵ�һ������£�û��ָ���Ƿ����ͷ���֮ǰ�����жϲ��ԡ� 
             * �ڷ���ʱ���÷�����������ʣ��΢������һ������ֵ���Եȴ����ṩ�� nanosTimeout ֵ��ʱ�䣬�����ʱ���򷵻�һ��С�ڵ��� 0 ��ֵ�������ô�ֵ��ȷ���ڵȴ����ص�ĳһ�ȴ������Բ��߱�������£��Ƿ�Ҫ�ٴεȴ����Լ��ٴεȴ���ʱ�䡣�˷����ĵ����÷�����������ʽ�� 
             * synchronized boolean aMethod(long timeout, TimeUnit unit) {
             *    long nanosTimeout = unit.toNanos(timeout);
             *    while (!conditionBeingWaitedFor) {
             *          if (nanosTimeout > 0)
             *          nanosTimeout = theCondition.awaitNanos(nanosTimeout);
             *         else
             *          return false;  }
             * // ...  }
             * ���ע������˷�����Ҫһ�� nanosecond �������Ա����ڱ���ʣ��ʱ��ʱ���ֽضϴ���
             * �ڷ������µȴ�ʱ�����־�����ʧʹ�ó���Ա����ȷ���ܵĵȴ�ʱ�䲻����ָ���ȴ�ʱ�䡣 
             * ʵ��ע������ 
             * �ٶ����ô˷���ʱ����ǰ�̱߳�������� Condition �й�����������ȡ����ȷ���Ƿ�Ϊ��������Լ�����ʱ����ζԴ�������Ӧ��ʵ�֡�
             * ͨ�����׳�һ���쳣������ IllegalMonitorStateException�����Ҹ�ʵ�ֱ���Դ˽��м�¼�� 
             * ����Ӧĳ���źŶ����ص���ͨ������ȣ�������ָʾ��ʹ�õ�ָ���ȴ�ʱ����ȣ�ʵ�ֿ��ܸ�ϲ����Ӧĳ���жϡ�
             * ������һ������£�ʵ�ֱ���ȷ���źű��ض�����һ���ȴ��̣߳�����еĻ����� 
             */
			public long awaitNanos(long nanosTime)throws InterruptedException {
				if(Thread.interrupted())
					throw new InterruptedException();
				Node node=addConditionWaiter();//���condition�ȴ��ڵ�
				int savedState=fullyRelease(node);//�ͷ�����Դ����������״̬
				int interruptMode=0;//����һ���ж�ģ��
				long lastTime=System.nanoTime();//��ȡ��ǰʱ�䣬�������㻨��ʱ��
				while(!isOnSyncQueued(node)){
					if(nanosTime<=0l){
						transferAfterCancelledWait(node);//ȡ����ǰ�ڵ㣬��ת�Ƶ�ͬ��������
						break;//����ѭ��
					}
					LockSupport.parkNanos(this,nanosTime);//����жϸ���ʱ��
					if((interruptMode=checkInterruptWhileWaiting(node))!=0){//����ȴ��������жϹ�����ô����ѭ��
						break;
					}
					long nowTime=System.nanoTime();
					nanosTime-=nowTime-lastTime;
					lastTime=nowTime;
				}
				if(acquiredQueued(node, savedState)&&interruptMode!=THROW_IE)
					interruptMode=REINTERRUPT;
				if(node.nextWaiter!=null)//�����һ��nextWaiter��Ϊ�գ���ô��Ҫ����ȡ���ȴ��Ľڵ�
					unlinkCanceledWaiters();
				if(interruptMode!=0)//����ж�ģ�Ͳ�Ϊ0��˵���жϹ�
					reportInterrupteAfterWait(interruptMode);
				return nanosTime-(System.nanoTime()-lastTime);
			}
			/*��ģʽ��ζ�ӵȴ�״̬�˳�ʱ��Ҫ�����ж�*/
            private final static int REINTERRUPT=1;
            /*��ģʽ��ζ�ӵȴ�״̬�˳�ʱ��Ҫ�׳�InterruptedException*/
            private final static int THROW_IE=-1;
			
            /**
			 * �ɵ�ǰ�߳��ڽӵ��źš����жϻ򵽴�ָ���ȴ�ʱ��֮ǰһֱ���ڵȴ�״̬��
			 * �˷�������Ϊ�ϵ�Ч�ڣ�awaitNanos(unit.toNanos(time)) > 0
			 * @return ����ڴӴ˷�������ǰ��⵽�ȴ�ʱ�䳬ʱ���򷵻� false�����򷵻� true 
			 */
			public boolean await(long time, TimeUnit unit)throws InterruptedException {
				if(unit==null)
					throw new NullPointerException();
				if(Thread.interrupted())
					throw new InterruptedException();
				Node node =addConditionWaiter();//����condition�µȴ��ڵ�
				int savedState=fullyRelease(node);
				boolean timeout=false;//�����ж��Ƿ�ʱ
				int interruptMode=0;
				long lastTime=System.nanoTime();
				long nanoTime=unit.toNanos(time);
				while(!isOnSyncQueued(node)){
					if(nanoTime<=0L){
						timeout=transferAfterCancelledWait(node);//ȡ���ȴ��ڵ㣬��������뵽ͬ�������У����ص�boolean�����ж��Ƿ�ʱ,trueΪû�г�ʱ��false��ʱ��
						break;
					}
					if(nanoTime>spinForTimeOutThreshold)
						LockSupport.parkNanos(this, nanoTime);//�ȴ�
					if((interruptMode=checkInterruptWhileWaiting(node))!=0){//����ڵȴ��������жϹ�����ô����ѭ��
						break;
					}
					long nowTime=System.nanoTime();
					nanoTime-=nowTime-lastTime;
					lastTime=nowTime;
				}
				if(acquiredQueued(node, savedState)&&interruptMode!=THROW_IE)
					interruptMode=REINTERRUPT;
				if(node.nextWaiter!=null)
					unlinkCanceledWaiters();
				if(interruptMode!=0)
					reportInterrupteAfterWait(interruptMode);
				return !timeout;
			}

			/**
			 * ��ɵ�ǰ�߳��ڽӵ��źš����жϻ򵽴�ָ���������֮ǰһֱ���ڵȴ�״̬�� 
			 *���������ص�����ԭ�ӷ�ʽ�ͷţ����ҳ����̵߳��ȵ�Ŀ�ģ������õ�ǰ�̣߳����ڷ��������������֮һ ��ǰ����ǰ�߳̽�һֱ��������״̬�� 
			 *   ����ĳ���̵߳��ô� Condition �� signal() �������������ɽ���ǰ�߳�ѡΪ�����ѵ��̣߳�
			 *   ��������ĳ���̵߳��ô� Condition �� signalAll() ������
			 *   ��������ĳ���߳��жϵ�ǰ�̣߳���֧���ж��̵߳Ĺ���
			 *   ����ָ����������޵��ˣ�
			 *   ���߷�������ٻ��ѡ��� 
			 *����������£��ڴ˷������Է��ص�ǰ�߳�֮ǰ�����������»�ȡ��������йص��������̷߳���ʱ�����Ա�֤ �����ִ����� 
			 *�����ǰ�̣߳� 
			 *   �ڽ���˷���ʱ�Ѿ������˸��̵߳��ж�״̬��
			 *   ������֧�ֵȴ����ж��̹߳���ʱ���̱߳��жϣ����׳� InterruptedException�����������ǰ�̵߳����ж�״̬��
			 * �ڵ�һ������£�û��ָ���Ƿ����ͷ���֮ǰ�����жϲ��ԡ� ����ֵָʾ�Ƿ񵽴�������ޣ�ʹ�÷�ʽ���£� 
			 * synchronized boolean aMethod(Date deadline) {
			 *     boolean stillWaiting = true;
			 *     while (!conditionBeingWaitedFor) {
			 *          if (stillWaiting)
			 *             stillWaiting = theCondition.awaitUntil(deadline);
			 *          else
			 *              return false;
			 *              }
			 *              // ... 
			 *       }
			 * ʵ��ע������ 
			 * �ٶ����ô˷���ʱ����ǰ�̱߳�������� Condition �й�����������ȡ����ȷ���Ƿ�Ϊ��������Լ�����ʱ����ζԴ�������Ӧ��ʵ�֡�
			 * ͨ�������׳�һ���쳣������ IllegalMonitorStateException�����Ҹ�ʵ�ֱ���Դ˽��м�¼�� 
			 * ����Ӧĳ���źŶ����ص���ͨ������ȣ�������ָʾ�Ƿ񵽴�ָ������������ȣ�ʵ�ֿ��ܸ�ϲ����Ӧĳ���жϡ�
			 * ������һ������£�ʵ�ֱ���ȷ���źű��ض�����һ���ȴ��̣߳�����еĻ����� 
			 */
			public boolean awaitUntil(Date deadline)throws InterruptedException {
				if(deadline==null)
					throw new NullPointerException();
				long absTime=deadline.getTime();
				if(Thread.interrupted())
					throw new InterruptedException();
				Node node=addConditionWaiter();
				int savedState=fullyRelease(node);
				int interruptMode=0;
				boolean timeout=false;
				while(!isOnSyncQueued(node)){
					if(System.currentTimeMillis()>=absTime){
						timeout=transferAfterCancelledWait(node);
						break;
					}
					LockSupport.parkUntil(absTime);
					if((interruptMode=checkInterruptWhileWaiting(node))!=0)
						break;
				}
				if(acquiredQueued(node, savedState)&&interruptMode!=THROW_IE)
					interruptMode=REINTERRUPT;
				if(node.nextWaiter!=null)
					unlinkCanceledWaiters();
				if(interruptMode!=0)
					reportInterrupteAfterWait(interruptMode);
				return !timeout;
			}

			/**
			 * ���ȴ������еȴ�ʱ����Ľڵ�ת�Ƶ�ͬ��������
			 */
			public void signal() {
			  if(!isHeldExclusively()){//������Ƕ�ռ��,���׳��쳣
				  throw new IllegalMonitorStateException();
			  }
			  Node first=firstWaiter;
			  if(first!=null)
				  doSignal(first);//
			}
            
			/**
			 * ѭ�����ȴ������е�canceled�ڵ���߿սڵ��Ƴ�
			 * @param first
			 */
			private final void doSignal(Node first){
				do{
					//��condition�ȴ����е�һ���ڵ��nextWaiter����Ϊ�ȴ�����ͷ��㣬��Ϊ���ڵ�ͷ���Ҫת�Ƶ�ͬ��������ȥ
					if((firstWaiter=first.nextWaiter)==null)//�޸�ͷ��㣬��ɾ�ͷ�����Ƴ�
						lastWaiter=null;
				}while(!transferForSignal(first)&&(first=firstWaiter)!=null);//����ͷ�����뵽aqsͬ�������У��������ľ���
			}
			/**
			 * ѭ���Ƴ�����condition�ȴ����нڵ�
			 * first�������е�һ����Ϊnull�Ľڵ�
			 */
			private final void doSignalAll(Node first){
				lastWaiter=firstWaiter=null;//���ȴ��ڵ㶼����Ϊnull
				do{
					Node next=first.nextWaiter;
					first.nextWaiter=null;//help gc
					transferForSignal(first);
					first=next;
				}while(first!=null);
			}
				
			/**
			 * ��condition�����еĵȴ��ڵ�ȫ��ת�Ƶ�ͬ��������ȥ
			 */
			public void signalAll() {
				if(!isHeldExclusively())
					throw new IllegalMonitorStateException();
				Node first=firstWaiter;
				if(first!=null)
					doSignalAll(first);
			}
	    	
	    }
}
