package threadpoolproject;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;



/**
 * SynchronousQueue������һ��BlockingQueue,�����в����������ȴ���һ���̵߳��Ƴ���������֮��Ȼ��
 * SynchronousQueue����û���κε��ڲ�������һ������������û�С��㲻����ͨ��peek()�����Ӷ���ͷ����ȡԪ�أ���ΪԪ��ֻ�е�����ͼ�Ƴ���ʱ�Ŵ���;
 * �㲻�������κη����������в���Ԫ�أ�ֻ�е������̳߳���removeԪ��;��Ҳ����iterator�ö��У���Ϊ����û��Ԫ�ؿ���iterator������ͷ����Ԫ�����׸�
 * ������еĲ����߳���ͼ��������ӵ�Ԫ�أ����û�������Ķ��У���ô��û�п������Ƴ���Ԫ�ز���poll()������null;
 * ����Collection������������SynchronousQueue���ֵ���һ����Collection��SynchronouseQueue���в������Ԫ�ء�
 * SynchronouseQueue�ǳ���CSP��ADa�н����ŵ����ǳ��ʺϴ��������(handoff design)������������У�������һ���߳��еĶ�������뽫��Ϣ���¼�������
 * ���ݸ���һ���̵߳Ķ�����ô�������������ͬ��(sync up)
 * ���ڵȴ��������������ߣ��ö����ṩ�˿�ѡ��Ĺ�ƽ���ԡ�Ĭ������£�������֤���й�ƽ�ԡ����ǿ����ڹ��캯����set true ����֤���еĹ�ƽ�ԣ��Ѵﵽ�߳�
 * �������еĴ���ΪFIFO;
 * ���༰��iteratorʵ����Collection��Iterator�����÷���;
 * ������Java Collection Framework�ĳ�Ա
 * @author heyw
 *
 * @param <E>
 */
public class SynchronousQueue<E> extends AbstractQueue<E>
                                                           implements BlockingQueue<E>{

	
	/*
	 * �����ǶԱ���Ϊ"�����������ͬ������"�ġ�dual stack and dual queue���㷨һ����չʵ�֡������ṩ�����־������ƣ�FIFO(�Ƚ��ȳ�)ģʽ��FILO(�Ƚ����)ģʽ��
	 * ������ģʽ�������ܲ�࣬������һ��Ӧ���У�FIFOģʽ����֧�ָ����������,FILO���Ը���̶��ϱ����̱߳��ػ�(thread locality).
	 * dual queue ����dual stack ������ʱ��ֻ���������:(1)����data:put()������Ԫ�أ�(2)����request:take()������ȡԪ�أ�(3)Ϊnull
	 * (1)��fullfil(����:�Գ���data�Ķ���requestһ�����󣬷�֮��Ȼ)�ĵ��ý�����ӣ�dequeue��һ������node;�ö��������˸���Ȥ�������ǣ�
	 * �κβ���������ͨ���жϵ�ǰ�Ķ���������mode��ִ�У�������������
	 * (2)queue��stack�����ݽṹ�����˱Ƚ����ƵĹ��������ʵ���ϵ�ϸ�ڡ��ͼ��Զ��ԣ����Ǳ��ֲ�ͬ�Ա��Ժ��ܹ��õ��������ݻ���
	 * (3)������Ҫ��ͨ��LockSupport�ķ���park()/unpark()����ɵģ�ֻ�Ƕ�����Щ�ƺ��������һ����Ҫfulfilled��node��node���Ȼ�����(spin)һ��ʱ��(ֻҪ�ڶദ�����������);
	 * ��synchronouse queue�ǳ���æ������£������ܹ�����������������ͬʱsynchronouse queue�������æ����ô���������㹻С������������ע��.
	 * (4)��queue��stack������ķ�ʽ�ǲ���ͬ��:
	 * ���ڶ��У���ȡ��ʱ��������o(1)��ʱ����ֱ���Ƴ�һ��node(����һ����У��ĳ���),���������ǰ��βspinʱ��Ҫ�ȴ���ֱ��һЩ������(subsequent)ȡ����
	 * ����ջ��˵,ȡ����������Ҫo(n)��ʱ�����ջ�Ա��������Ƴ�node�����ǿ���������ʹ�ø�ջ���̲߳���ִ�С�
	 * (5)��������������������node�Ļ����¼�ʱ����Ҫע��������Щ���ܱ������̳߳�ʱ����еĶ����ݣ������ڵ㣬�̵߳�����;
	 */
	abstract static  class Transferer{
		/**
		 * put ����take�����ĺϲ�
		 * @param e �����Ϊ�գ��򴫵ݸ�һ�������ߣ����Ϊ�����ȡtransfer���������ṩ��һ�����ض���
		 * @param timed �����ò����Ƿ���ʱ����Ƶģ���ʱ��
		 * @param nanos ��ʱ����Ƶ�ʱ��
		 * @return
		 */
		abstract Object transfer(Object e,boolean timed,long nanos);
	}
	
	/**��������Ŀ����������spin*/
	static final int NCPU=Runtime.getRuntime().availableProcessors();
	/**�ڶ�ʱ�ȴ�����֮ǰ������(spin)�Ĵ�������õ�ֵ�ǲ�Ӧ�����Ŵ���������Ŀ(����2)���ı�ģ������һ������
	 * */
	static final int maxTimedSpins=(NCPU<2)?0:32;
	/**�ڷǶ�ʱ�ȴ�����֮ǰ�������Ĵ��������ֵ�ȶ�ʱ��������Ϊ�Ƕ�ʱ����£����ڲ���ÿ�����������times,�������ٶȸ���*/
	static final int maxUntimedSpins=maxTimedSpins*16;
	/**������Ŀ���������ֵ���������Ȳ��ö�ʱ�ж��ٶȸ���*/
	static final long spinForTimeoutThreshold=1000L;
	
	
	/**dual stack*/
	static final class TransferStack extends Transferer{
        /**node����һ��unfulfilled��������*/
		static final int REQUEST=0;
		
		/**node����һ��unfulfilled��������*/
		static final int DATA=1;
		
		/**node����fulfiling��һ��unfulfilled��DATA����REQUEST*/
		static final int FULFILLING=2;
		
		/**mֵ�������0����1���ܷ���true*/
		static boolean isFulFilling(int m){return (m&FULFILLING)!=0;}
		
		/**TransferStack��NODE��*/
		static final class SNode{
			AtomicReference<SNode>next=new AtomicReference<>();//stack�е���һ���ڵ�
			AtomicReference<SNode>match=new AtomicReference<>();//ƥ�䵱ǰ�ڵ�Ľڵ�
			/*����park��unpark(),������ǰ�ڵ������waiter��ɫ���ȴ������ڵ����ƥ�䣬��ǰ�ڵ���ջ�ṹ���������ڵ��next
			 * ��ˣ�s.next()=null˵����ǰ�ڵ�sû��waiter�������Ҫ����ƥ��
			 */
			volatile Thread waiter;
			Object item;//data;���mode��REQUEST,��ô��Ϊnull
			int mode;
			//item��mode����Ҫ���ó�volatile����atomic����Ϊ������д֮ǰ��֮��������volatile��atomic����;
			SNode(Object item){
				this.item=item;
			}
			
			boolean casNext(SNode cmp,SNode val){
				return next.get().equals(cmp)&&next.compareAndSet(cmp, val);//ע���ָ����ж�
			}
			
			/**
			 * ����ƥ��Node s����ǰnode(���ø÷�����),����ɹ������߳�
			 * FulFillers(����ƥ���node)����������������ȷ�������waiters(�ȴ��߳�)
			 * Waiters������ֱ����Щ�̵߳�node���ƥ��
			 * @param s
			 * @return
			 */
			boolean tryMatch(SNode s){
				//�ж�match�Ƿ�Ϊnull�����Ϊnull�����ж�waiter�Ƿ�Ϊ�գ����Ϊ��Ȼ��unpark���߳�����һ��
				if(match.get()==null && match.compareAndSet(null, s)){
					Thread w=waiter;//��ǰnode��waiter�߳�
					if(w!=null){
						LockSupport.unpark(w);
					}
					return true;
				}
				//���match��Ϊnull���򷵻�s��match�Ƿ����
				return match.get()==s;
			}
			
			/**
			 * ͨ��ƥ��node s����ǰnode��match��ȡ����ǰnode��wait�߳�
			 */
			void tryCancel(){
				match.compareAndSet(null, this);
			}
			
			boolean isCanceled(){
				return match.get()==this;
			}
			
		}
		/**TransferStack�е�ͷ���*/
		AtomicReference<SNode> head =new AtomicReference<>();
		/**cas��ǰ�߳�*/
		boolean casHead(SNode h,SNode nh){
			return h==head.get()&&head.compareAndSet(h, nh);
		}
		
		/**
		 * ����������������һ��node������ֵ���÷���ֻ����transfer()�����е��ã��ڸ÷�����ѹ��ջ��node�����������ظ�ʹ�õģ�
		 * ��ʱ���������ڼ��ٶ�head��ȡ��CASer��ʱ����ͬʱҲ�ܱ�������ѹ��nodeʧ�ܵ�CASer��ɵ���������
		 * @param s
		 * @param e
		 * @param next
		 * @param mode
		 * @return
		 */
		static SNode snode(SNode s,Object e,SNode next,int mode){
			if(s==null) {
				s=new SNode(e);
				s.match=new AtomicReference<>();
			}
			s.next=new AtomicReference<SNode>(next);
			s.mode=mode;
			return s;
		}
		/**
		 * put �� take һ����Ŀ
		 */
		@SuppressWarnings("null")
		@Override
		Object transfer(Object e, boolean timed, long nanos) {
			/*
			 * �����㷨����ѭ��������������֮һ:
			 * 1.���Ϊ�ջ��߰�����ͬmode������nodes����ô���Խ���nodeѹ��ջ���ȴ�match��Ȼ�󷵻����node�����ȡ���ɹ��򷵻�null
			 * 2.���ջ��node��mode�ǻ���mode����ô���Խ�һ��fulfilling nodeѹ��ջ��ƥ��(match)��صĵȴ�node��Ȼ��������node������ջ��
			 * ����matched��item���ԡ�ƥ����߽�������û�б�Ҫ����Ϊ�����߳����ڽ��е���������;
			 * 3.���ջ���Ѿ���������һ��modeΪfulfilling�Ľڵ㣬��ôͨ������ƥ�����pop����������������Ȼ�����ѭ�������������fulfilling������һ����Ҫ�ģ�
			 * ֻ�ǲ���Ҫ����item����
			 * ��������������(1)ջ��Ϊ�ջ���ջ����mode������mode��ͬ�����(2)ջ����mode��Ϊ����������mode���������(3)ջ����modeΪfulfilling�����
			 * ������Կ���(1)��ǰ�ڵ�Ϊ�ȴ��߽�ɫ���ȴ������ڵ����ƥ��(2)ƥ���߽�ɫ��ƥ���Ƚ���ջ����һ���ڵ�(��ջ�ṹ������next�ڵ�)
			 * (3)������߽�ɫ����ô��������ɫpop������
			 */
			SNode s=null;//���嵱ǰ�ڵ�
			int mode=(e==null?REQUEST:DATA);
			
			//��ѭ��
			for(;;){
				SNode h=head.get();//��ȡͷ���
				if(h==null || h.mode==mode){//ջ��Ϊ�ջ���ջ��modeΪ�����mode��ͬ����ǰ�ڵ�Ϊ�ȴ��߽�ɫ
					if(timed &&nanos<=0){//�����������ȡ����
						if(h!=null && h.isCanceled()){//�ж�ջ���ڵ��Ƿ��Ѿ�cancel�ˣ����������������head�������жϣ�����ֱ�ӷ���null
							head.compareAndSet(h, h.next.get());
						}else{
							return null;
						}
					}else if(head.compareAndSet(h, s=snode(s,e,h,mode))){//�������������ȡ���������ȴ��߽�ɫ��Ϊ��ǰ�ڵ㸳ֵ������ջ���ڵ�Ϊ��ǰ�ڵ�next
						SNode m=awaitFulfill(s, timed, nanos);//�������أ�˵���ȴ���ϣ�ԭ��Ҫô�������ڵ�ƥ���˸õȴ��ڵ㣬Ҫô�Ǹýڵ��Ѿ�cancel��
						if(m==s){// s is canceled
							clean(s);  //��������canceled�ڵ�
							return null;
						}
						if((h=head.get())!=null &&h==m){//�ж�ջ���ڵ��Ƿ��ǵ�ǰ�ڵ�s��ƥ��ڵ�
							head.compareAndSet(h, s.next.get());
						}
						return mode==REQUEST?m.item:s.item;//�����RUQUEST�򷵻�ƥ����item�����߷�������item
					}
				}else if(!isFulFilling(mode)){//���mode��ΪFUlFIll����˵����ǰ�ڵ���ƥ���߽ڵ�
					if(h.isCanceled()){//�ж�ͷ�ڵ��Ƿ��Ѿ�cancel�ˣ�����Ǿ�����ͷ���
						head.compareAndSet(h, h.next.get());//pop head ����������ѭ��
					}else if(head.compareAndSet(h, s=snode(s, e, h, FULFILLING|mode))){//��ƥ������������mode
						for(;;){//��ͣѭ����ֱ���ҵ��ȴ��߽ڵ���ߵȴ��߽ڵ�ȫ��û����
							SNode m=s.next.get();//ƥ���߽ڵ�s��next�ڵ�϶��ǵȴ��߽ڵ㣬��ôm is s's match
							if(m==null){ //m=null,˵���Ѿ�û�еȴ��߽ڵ���
								head.compareAndSet(s, null);
								s=null;
								break;//���¿�ʼ��ѭ��
							}
							SNode mn=m.next.get();
							if(m.tryMatch(s)){//���ȴ��߽ڵ㽫ƥ���߽ڵ����ƥ�䣬����ɹ���pop�������ڵ�
								head.compareAndSet(s, mn);
								return mode==REQUEST?m.item:s.item;
							}else{//ʧ�����滻�ȴ��߽ڵ㣬������һ��ƥ��
								s.casNext(m, mn);
							}
						}
					}
				}else{//mode��Ϊ1Ҳ��Ϊ0��˵����ǰ�ڵ㲻�Ǵ���ģ�ֻ����ѭ���������Ѿ�fulfill�Ľڵ㣬��ʱΪhead��Ҳ����˵ͷ���Ϊfulfiller
					SNode m=h.next.get();//m�ǵȴ��߽ڵ㣬Ϊh.match
					if(m==null){
						head.compareAndSet(h, null);
					}else{//�˴�Ҫ��else����Ϊǰ��û��break��Ϊɶ��ǰ�治һ������Ϊ��δ��벻��Ҫ��ѭ��������д��if���һ��Ҫ�жϺ���Ҫ��Ҫдelse���
						SNode mn=m.next.get();
						if(m.tryMatch(h)){//mΪ�ȴ��߽ڵ㣬��waiter���Բ�Ϊ��,��˽�ƥ����ͷ���ƥ���m��match
							head.compareAndSet(h, mn);
						}else{
							head.get().casNext(m, mn);
						}
					}
				}
			}
			
		}
		/**
		 * ƥ��SNode s֮ǰһֱ��spin��������������
		 * @param s   ƥ��ڵ�
		 * @param timed �Ƿ����ö�ʱ
		 * @param nanos ��ʱʱ��
		 * @return
		 */
		SNode awaitFulfill(SNode s,boolean timed,long nanos){
			/*
			 * ��һ���ڵ�����߳̽�Ҫ����ʱ����Ҫ���øýڵ��waiter���Բ�����ʵ������֮ǰҪ���¼��һ��״̬�����covering race vs fulfill�ͻ�ע�⵽��node��waiter��Ϊ
			 * �գ�Ӧ�ñ�����
			 * ���̱߳�ջ���ڵ�ĵ���������ʱ����������������(spin)�������ߺ������߷ǳ�����ʱ�������ڵ���park��������ֻ�ڶദ���������������
			 * ������ѭ���Ĵ���ӳ��:interrupte()������Ȩ��ߣ�spin�ڶ������Ϊpark
			 */
			long lastTime=timed?System.nanoTime():0;//������ʼʱ������;
			Thread w=Thread.currentThread();
			@SuppressWarnings("unused")
			SNode h=head.get();//��һ���øø�ʲô��
			int spins=(shouldSpin(s)?(timed?maxTimedSpins:maxUntimedSpins):0);
			for(;;){
				if(w.isInterrupted()){//�ж�����Ȩ��ߣ�����ж��ˣ���ȡ���ýڵ�
					s.tryCancel();
				}
				SNode m=s.match.get();
				if(m!=null){
					return m;
				}
				if(timed){//��ʱ�����ڶ�ʱʱ��������������cancel
					long now =System.nanoTime();
					nanos-=now-lastTime;
					lastTime=now;
					if(nanos<=0){
						s.tryCancel();
						continue;//���ж��������
					}
				}
				
				if(spins>0) {
					spins=shouldSpin(s)?spins-1:0;
					}else if(s.waiter==null){//���waiter���ԣ���һ�����park
					s.waiter=w;
				   }else if(!timed){
					   LockSupport.park(this);//this ����transfer()�ĵ�����
				   }else if(nanos>spinForTimeoutThreshold){//���ʣ��nanos����spinForTimeOutThreshold������߳�
					   LockSupport.parkNanos(this, nanos);
				   }
			}
		}
		/**
		 * ���ڵ�s��stackջ�������ȥ������������ͷ��㵽��ǰ�ڵ�s.next()�����������Ѿ�cancel�Ľڵ�
		 * @param s
		 */
		void clean(SNode s){
			s.item=null;
			s.waiter=null;//��Ϊnull
			
			//����߲�������£�s�ڵ�����Ѿ���remove�����ѡ��s����һ���ڵ�
			SNode past=s.next.get();
			if(past!=null && past.isCanceled()){
				past=past.next.get();
			}
			
			SNode h;
			while((h=head.get())!=null && h!=past&& h.isCanceled()){//����ѭ��ͷ��㣬ֱ���и�ͷ���û��canceled,���ͷ������Ѿ�cancel�ģ���ô��ͷ����next�滻ͷ���
				head.compareAndSet(h, h.next.get());
			}
			//���ͷ�ڵ�û��cancel����ô��ͷ�ڵ����һ���ڵ㿪ʼ����������cancel�Ľڵ�
			while(h!=null && h!=past){
				SNode n=h.next.get();
				if(n!=null && n.isCanceled()){
					h.casNext(n, n.next.get());//�滻��һ�ڵ�
				}else{
					h=n;
				}
			}
		}
	
		/**
		 * ��ͷ���Ϊnull���ߵ��ڵ�ǰ�ڵ�s����ͷ����Ѿ�fulfillʱ����true
		 * @param s
		 * @return
		 */
		boolean shouldSpin(SNode s){
			SNode h=head.get();
			return h==null || h==s || isFulFilling(h.mode);
		}
		
	}
	
	/**��̬�ڲ���ֻ�ܷ����ⲿ��ľ�̬ʵ���ͷ��������������ⲿ���ʵ������*/
	static final class TransferQueue extends Transferer{
		/*
		 * TransferQueue ��һ��ʵ����Transferer�Ķ��У���head��tail���нڵ㶼�ǵȴ�ƥ��Ľڵ㣬����Ӧ��ȡ����û���ü�ȡ���Ľڵ㣻
		 * ƥ���߽ڵ㽫��ӵ�headƥ�䣬ƥ�����advanceHead()����һ���ȴ��߳�Ϊ�µ�head,,�����ĵȴ��߽ڵ���ӵ�tail���棬��ͨ��advanceTail()��������tail
		 * ��ջTransferStack��ͬ��TransferStack��Զ�������Ľڵ�ƥ��ջ���Ľڵ㣬���ƥ�������׳����ߣ����ջ���ڵ��Ѿ�ƥ�����������ѭ��ƥ��ڵ�ֱ��ƥ����
		 * 
		 */
		
		static final class QNode{
			AtomicReference<QNode> next;//��������һ��QNode
			AtomicReference<Object>item;//����CAS��ȡ���߸������ݣ���ȡ�������null���not null �������������not null ���null
			volatile Thread waiter;// ���������park��unpark
			boolean isData; //����Ǹ������ݻ��ǻ�ȡ����
			QNode(Object e,boolean isData){
				this.item=new AtomicReference<Object>(e);
				this.isData=isData;
			}
			/**
			 * ɾ����ǰ����һ�ڵ㣬ֻ�е���һ�ڵ��next����ʱ
			 * @param cmp
			 * @param val
			 * @return
			 */
			boolean casNext(QNode cmp,QNode val){
				return cmp==next.get()&&next.compareAndSet(cmp, val);
			}
			boolean casItem(Object cmp,Object val){
				return cmp==item.get()&&item.compareAndSet(cmp, val);
			}
			/**
			 * ���ڵ�����ֵ������item������Ǹ�node��Ҫ�Ӷ�����ɾ��
			 * @param cmp
			 * @return
			 */
			boolean tryCancel(Object cmp){
				return cmp==item.get()&&item.compareAndSet(cmp, this);
			}
			boolean isCanceled(){
				return item.get()==this;
			}
			/**
			 * ���true��˵���Ѿ��뿪�����ˣ���Ϊ��node��nextָ������һ��advanceHead()�����Ѿ�������
			 * @return
			 */
			boolean isOffList(){
				return this==next.get();
			}
		}
        AtomicReference<QNode> head;
        AtomicReference<QNode> tail;
        /*
         * ������Ҫɾ���Ľڵ㣬���clean()����ʱ��cleanMeΪnull������Ҫɾ����node ��ǰһ���ڵ�����ΪcleanMe���б�ǣ�
         * ��ʾcleanMe����һ���ڵ���Ҫɾ����һ������ͨ��setNext(null)�ķ�ʽɾ����һ���ڵ㣬��Ϊ��Ҫɾ���Ľڵ����Ҳ��next�ڵ㣬��Ҫƴ�ӵ�������
         * �ȵ���һ�ε���clean����ʱ��ɾ��
         * ע�������ϵĽڵ�һ����������ĳ�ڵ��next����Ϊnull�ķ�����ɾ���ýڵ����һ���ڵ㣬Ҫ�뽫��Ҫɾ���Ľڵ�Ӷ����Ƴ��������øýڵ�next=����
         */
        AtomicReference<QNode>cleanMe;
        /**'
         * �����Ҫɾ���ڵ��ǰ�νڵ�
         * @param cmp
         * @param val
         */
        boolean casCleanMe(QNode cmp,QNode val){
        	return cmp==cleanMe.get()&&cleanMe.compareAndSet(cmp, val);
        }
        
        /**
         * TransferQueue����ʱhead��tailָ��ͬһ���ڵ�
         *     head|tail->null
         */
        TransferQueue(){
        	QNode h=new QNode(null,false);
        	head.set(h);
        	tail.set(h);
        }
        /**
         * ����ͷ��㲢ɾ����ǰ��ͷ���
         * @param cmp
         * @param val
         * @return
         */
        void advanceHead(QNode cmp,QNode val){
        	if(cmp==head.get()&&head.compareAndSet(cmp, val)){
        		cmp=cmp.next.get();
        	}
        }
        /**
         * ����β�ڵ�
         * @param cmp
         * @param val
         * @return
         */
        void advanceTail(QNode cmp,QNode val){
        	 if(cmp==tail.get()){
        	   tail.compareAndSet(cmp, val);
        	   }
        }
        /**
         * ת�����ݣ����Ա���Ϊput����take��ʽ
         */
		@Override
		Object transfer(Object e, boolean timed, long nanos) {
			/*
			 * �½����Ľڵ�s�����ֽ�ɫ��һ���ǵȴ��ߣ�һ����ƥ���ߣ��Ӷ�ͷhead����βtail���ǵȴ��ߣ������ĵȴ��߳�Ϊ�µĶ�β
			 * ƥ�������ܵ���ͷȥƥ��ȴ��ߣ�ƥ��ɹ�����һ���ȴ�����Ϊ��ͷ
			 * head->waiter->waiter->.......->waiter->tail
			 */
			QNode s=null;
			boolean isData=(e!=null);
			for(;;){
				QNode h=head.get();
				QNode t=tail.get();
				if(h==null || t==null)   //�����δ���죬������ѭ��
					continue;
				if(h==t || t.isData==isData){//���Ϊtrue������ȴ��߽�ɫ����tail�������QNode
					QNode tn=t.next.get();//����1  ��ȡt����һ���ڵ㣬�����ж��Ƿ����Ѿ����µ�tail�ˣ�������˾͸���tail
					if(t!=tail.get()){//����2  ���tail�Ѿ����£�������ѭ������ȡ����tail
						continue;
					}
					/*
					 * �˴�֮������Ҫ���в���2�Ͳ���3�жϣ�����Ϊ���ֿ���tail.setNext(new QNode()),��ʱt==tail����t.next��Ϊ��
					 * head->waiter->waiter->.......->waiter->tail==t->waiter
					 */
					if(tn!=null){//����3   ���next��Ϊ�գ�����tail,
						advanceTail(t,tn);
						continue;
					}
					//���е��⣬��˵��t==tail&& tail.next==null
					if(s==null){
						s=new QNode(e,isData);//����4  ����һ��QNode
					}
					if(timed && nanos<=0){
						return null; //put�����ڴ˴����ʧ�ܣ�offer���ܻ�
					}
					/**
					 * ���ڲ���5��6����
					 * ���t==h������Ϊ�գ�(h=head)==(t=tail)->null,��������5�� (h=head)==(t=tail)->waiter=s  
					 *                   ��������6�� (h=head)==t->(waiter==tail)=s  ������ʵ����head ��tail ֮�䶼�ǵȴ��߽ڵ�
					 * ���h��=t,�������еȴ��ߣ���(h=head)->waiter->waiter->....->waiter->(tail=t),��������5��,���б��:
					 * (h=head)->waiter->waiter->....->waiter->(tail=t)->waiter=s
					 * ��������6������:(h=head)->waiter->waiter->....->waiter->t->(tail=waiter)=s
					 */
					if(!t.casNext(null, s)){//����5  ���casʧ�ܣ�˵���в����ĵȴ��ߣ�����ѭ��
						continue;
					}
					advanceTail(t,s);//����6  cas�ɹ���������tail=s
					Object x=awaitFulfill(s,s.item.get(),timed,nanos);//�ȴ����ƥ��
					if(x==s){//���Ϊtrue��˵��s�Ѿ�canceled
						clean(t,s);
						return null;//��ʱ���ʧ�ܷ���null��˵���߳��жϹ�
					}
					//��������Ҫȡ��s�ڵ�
					if(!s.isOffList()){
						advanceHead(t,s);//���tλ��head�����滻headΪs
						if(x!=null) {
							/*��һ����Ҫ����Ϊƥ����ƥ��ȴ�ʱ����Ҫ�жϸ��ݵȴ��ߵ�item�����Ƿ�cancel�����ж�
							 * ���Կ���ƥ����ƥ��ǰ������
							 */
							 s.item.compareAndSet(e, s);//���x��=null����isData=false����ȡ���ýڵ�
						}
						s.waiter=null;// ���߳���������Ϊnull
					}
					return  (x!=null)?x:e;
				}else{
					QNode hn=h.next.get();
					if(t!=tail.get()||t.next.get()!=null ||h!=head.get()){//tail��head���Ѿ���cas�����ˣ���ȡ������ȷ�Ķ�ͷ��β�����¿�ʼ
						continue;
					}
					if(hn!=null && hn.isCanceled()){//����Ѿ�canceled�ڵ�
						head.compareAndSet(h, hn);
					}
				/*ƥ��ǰ����������ͨ�������¿�ʼ
				 * (1)isData=true����ȴ���hn��item x==null,����isData=false����item x��=null ��һ�����д����(isData!=(x!=null))=true
				 * (2)�ȴ���hn��item x��=hn
				 * (3)cas�滻item�ɹ� 
				 */
					Object x=hn.item.get();
					if(isData==(x!=null) || x==hn || !hn.casItem(x, e)){
						advanceHead(h,hn);//�滻head
						continue;//������ƥ������������ƥ��
					}
					//ƥ��ͨ��
					advanceHead(h,hn);//�滻head�����ܻ�ʧ��
					LockSupport.unpark(hn.waiter);
					return (x!=null)?x:e;
				}
				
			}
		}
        /**
         * ͨ��spin����lock���ȴ�ƥ��
         * @param s
         * @param timed
         * @param nanos
         * @return
         */
		Object awaitFulfill(QNode s,Object e,boolean timed,long nanos){
			int spins=(s==head.get().next.get())?((timed)?maxTimedSpins:maxUntimedSpins):0;
			long lastTime=(timed)?System.currentTimeMillis():0;
			Thread wt=Thread.currentThread();
			for(;;){
				if(wt.isInterrupted()){
					s.tryCancel(e);//�߳��ж���ȡ���õȴ��߽ڵ�
				}
				Object x=s.item;
				if(x!=e){
					return x; //Ψһ����
				}
				if(timed){
					long nowTime=System.currentTimeMillis();
					nanos-=nowTime-lastTime;
					lastTime=nowTime;
					if(nanos<=0){
						s.tryCancel(e);
						continue;
					}
				}
				if(spins>=0) spins--;
				else if(s.waiter==null){
					s.waiter=wt;
				}else if(!timed){
					LockSupport.park(this);
				}else if(nanos>spinForTimeoutThreshold){
					LockSupport.parkNanos(this, nanos);
				}
			}
		}
		/**
		 * clean�����������ջ���Ѿ�canceled�ڵ㣬ͬʱ��ȡcleanMe dp��Ҫɾ���Ľڵ㣬dp.casNext()��ɾ���ýڵ㣬
		 * ͬʱ����Ҫɾ���Ľڵ�s��ǰ��pred���ΪcleanMe
		 * @param pred
		 * @param next
		 */
		@SuppressWarnings("unused")
		void clean(QNode pred,QNode s){
			s.waiter=null;//����waiter
			while(pred.next.get()==s){//ѭ��ǰ�ᣬpred�ڵ�Ϊ��Ҫ�༭
				QNode h=head.get();
				QNode hn=h.next.get();
				if(hn!=null && hn.isCanceled()){//��������head
					advanceHead(h,hn);
					continue;
				}
				QNode t=tail.get();
				QNode tn=t.next.get();
				if(t!=tail.get()){
					continue;
				}
				if(tn!=null){
					advanceTail(t,tn);
					continue;
				}
				QNode sn=s.next.get();
				if(s!=t){//���s����tail�ڵ㣬��ýڵ����ֱ��ɾ��
					if(s==sn|| pred.casNext(s, sn)){//s==sn��ʾ�Ѿ����ٶ����ϣ�pre.casNext(s,sn)��ʾsn����s��Ϊpred��next����sɾ������
						return;
					}
				}
				//sλ�ڶ�β����ɾ����ǰ��ȡ���Ľڵ㣬������s��ǰ��pred���ΪcleanMe
				QNode dp=cleanMe.get();//��ȡ��ǰ�����cleanMe�ڵ�
				QNode d=dp.next.get();//��ȡ��Ҫɾ���Ľڵ�
				if(dp!=null){
					QNode dn;
					/*
					 * d==null˵����Ҫɾ���Ľڵ��Ѿ���������,dp==d˵��d�Ѿ����ٶ������ˣ���d.isCanceled˵��dû�б�ȡ�����������������Ҫ��predȥ��cleanMe��־
					 * ���������ǰ���֣���ô�Ϳ�ʼɾ��d(ɾ��������d����Ϊ��β��d.nextҪ���ڣ�d �ڶ�����)������dp��next�滻��d��next���滻�ɹ�Ҳ��Ҫ���pred��cleanMe��־
					 */
					if(d==null || dp==d || !d.isCanceled()||((d!=t&&(dn=d.next.get())!=null)&&dn!=d&&dp.casNext(d, dn))){
						casCleanMe(dp, null);
					}
					if(pred==dp){
						return ;
					}
				}else if(casCleanMe(null,pred)){
					return;//�Ƴ����s�ڵ�
				}
			}
		}
	}
	
	private volatile Transferer transfer;
	
	public SynchronousQueue(){
		this(false);
	}
	public SynchronousQueue(boolean isTransferQueue){
		transfer=isTransferQueue?new TransferQueue():new TransferStack();
	}
	@Override
	/**
	 * �Ƴ�����ͷ��Ԫ�ز���ȡ��Ԫ��
	 */
	public E poll() {
		return (E) transfer.transfer(null, true, 0);
	}
	/**
	 * SynchronousQueue���Ƿ���null
	 */
	@Override
	public E peek() {
		return null;
	}

	/**
	 * �����������ָ��Ԫ�أ��ɹ�����true��ʧ�ܷ���false
	 */
	@Override
	public boolean offer(E e) {
	    if(e==null){
	    	throw new NullPointerException();
	    }
		return transfer.transfer(e, true, 0)!=null;//�������ز����ܳ���null������nullֻ�������ʧ�ܻ����߳��ж�
	}

	@Override
	public void put(E e) throws InterruptedException {
		if(e==null) throw new NullPointerException();
		if(transfer.transfer(e, false, 0)==null){
			Thread.interrupted();
			throw new InterruptedException();
		}
	}

	@Override
	public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
		if(e==null) throw new NullPointerException();
		if(transfer.transfer(e, true, unit.toNanos(timeout))!=null){
			return true;
		}
		if(!Thread.interrupted()) return false;//�����ǰ�߳�û���жϹ�������false
		throw new InterruptedException();//�����׳��ж��쳣
	}

	@Override
	public E take() throws InterruptedException {
		Object o=transfer.transfer(null, false, 0);
		if(o==null){
			Thread.interrupted();
			throw new InterruptedException();
		}
		return (E)o;
	}

	@Override
	public E poll(long timeout, TimeUnit unit) throws InterruptedException {
		   Object e=transfer.transfer(null, true, unit.toNanos(timeout));
		   if(e!=null || !Thread.interrupted()){
			   return (E)e;
		   }
		   throw new InterruptedException();
	}
	/**
	 * SynchronousQueue�����в������κ�Ԫ��
	 */
	@Override
	public int remainingCapacity() {
		return 0;
	}
	/**
	 * SynchronousQueue�����в������κ�Ԫ��
	 * �Ƴ������е�Ԫ�أ�����ӵ�Collection c��
	 */
	@Override
	public int drainTo(Collection<? super E> c) {
		if(c==null) throw new NullPointerException();
		if(c==this)throw new IllegalArgumentException();
		int n=0;
		E e;
		while((e=poll())!=null){
			c.add(e);
			n++;
		}
		return n;
	}
	/**
	 * SynchronousQueue�����в������κ�Ԫ��
	 * �Ƴ������е�Ԫ�أ�����ӵ�Collection c��
	 */
	@Override
	public int drainTo(Collection<? super E> c, int maxElements) {
		if(c==null) throw new NullPointerException();
		if(c==this)throw new IllegalArgumentException();
		int n=0;
		E e;
		while((e=poll())!=null){
			c.add(e);
			n++;
			if(n==maxElements) break;
		}
		return n;
	}

	/**
	 * SynchronousQueue�����в������κ�Ԫ��
	 */
	@Override
	public Iterator<E> iterator() {
		return Collections.emptyIterator();
	}
	/**
	 * SynchronousQueue�����в������κ�Ԫ��
	 */
	@Override
	public int size() {
		return 0;
	}
   public void clear(){
   }
   
   public Object[] toArray(){
	return new Object[0];
   }
   public <T> T[] toArray(T[] a){
	   if(a.length>0){
		   a[0]=null;
	   }
	   return a;
   }
   
   
}
