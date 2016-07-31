package threadpoolproject;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;



/**
 * SynchronousQueue队列是一种BlockingQueue,在其中插入操作必须等待另一个线程的移除操作，反之亦然。
 * SynchronousQueue队列没有任何的内部容量，一个队列容量都没有。你不可能通过peek()方法从队列头部获取元素，因为元素只有当你试图移除它时才存在;
 * 你不能利用任何方法往队列中插入元素，只有当其他线程尝试remove元素;你也不能iterator该队列，因为里面没有元素可以iterator；队列头部的元素是首个
 * 进入队列的插入线程试图往队列添加的元素，如果没有这样的队列，那么就没有可用于移除的元素并且poll()将返回null;
 * 对于Collection的其他方法，SynchronousQueue表现的像一个空Collection。SynchronouseQueue队列不允许空元素。
 * SynchronouseQueue非常像CSP和ADa中交汇信道，非常适合传递性设计(handoff design)，在这种设计中，运行在一个线程中的对象如果想将信息，事件和任务
 * 传递给另一个线程的对象，那么这两个对象必须同步(sync up)
 * 对于等待的生产者消费者，该队列提供了可选择的公平策略。默认情况下，并不保证队列公平性。但是可以在构造函数中set true 来保证队列的公平性，已达到线程
 * 进出队列的次序为FIFO;
 * 该类及其iterator实现类Collection和Iterator的所用方法;
 * 该类是Java Collection Framework的成员
 * @author heyw
 *
 * @param <E>
 */
public class SynchronousQueue<E> extends AbstractQueue<E>
                                                           implements BlockingQueue<E>{

	
	/*
	 * 该类是对被称为"非阻塞对象的同步条件"的“dual stack and dual queue”算法一种扩展实现。该类提供了两种竞争机制：FIFO(先进先出)模式和FILO(先进后出)模式；
	 * 这两种模式表现性能差不多，但是在一般应用中，FIFO模式可以支持更大的吞吐量,FILO可以更大程度上保持线程本地化(thread locality).
	 * dual queue 或者dual stack 在任意时刻只有三种情况:(1)持有data:put()方法的元素，(2)持有request:take()方法获取元素，(3)为null
	 * (1)对fullfil(比如:对持有data的队列request一个对象，反之亦然)的调用将会出队（dequeue）一个互补node;该队列最令人感兴趣的特征是：
	 * 任何操作都可以通过判断当前的队列所处的mode来执行，无需利用锁；
	 * (2)queue和stack的数据结构共享了比较类似的观念，但几乎实质上的细节。就简单性而言，它们保持不同以便以后能够得到独立的演化。
	 * (3)阻塞主要是通过LockSupport的方法park()/unpark()来完成的，只是对于那些似乎将变成下一个将要fulfilled的node的node首先会自旋(spin)一段时间(只要在多处理器的情况下);
	 * 在synchronouse queue非常繁忙的情况下，自旋能够显著提升吞吐量，同时synchronouse queue如果不繁忙，那么自旋将会足够小以至不会引起注意.
	 * (4)在queue和stack中清理的方式是不相同的:
	 * 对于队列，当取消时，我们在o(1)的时间内直接移除一个node(几次一致性校验的尝试),但是如果当前队尾spin时需要等待，直到一些后来的(subsequent)取消；
	 * 对于栈来说,取消根本上需要o(n)的时间遍历栈以便我们能移除node，但是可以与其他使用该栈的线程并发执行。
	 * (5)当垃圾回收器处理大多数node的回收事件时，需要注意遗忘那些可能被阻塞线程长时间持有的对数据，其他节点，线程的引用;
	 */
	abstract static  class Transferer{
		/**
		 * put 或者take方法的合并
		 * @param e 如果不为空，则传递给一个消费者，如果为空则获取transfer从生产者提供的一个返回对象
		 * @param timed 表明该操作是否受时间控制的（定时）
		 * @param nanos 受时间控制的时间
		 * @return
		 */
		abstract Object transfer(Object e,boolean timed,long nanos);
	}
	
	/**处理器数目，用来控制spin*/
	static final int NCPU=Runtime.getRuntime().availableProcessors();
	/**在定时等待阻塞之前，自旋(spin)的次数，最好的值是不应该随着处理器的数目(大于2)而改变的，因此是一个常量
	 * */
	static final int maxTimedSpins=(NCPU<2)?0:32;
	/**在非定时等待阻塞之前，自旋的次数，这个值比定时更大是因为非定时情况下，由于不用每次自旋都检查times,自旋的速度更快*/
	static final int maxUntimedSpins=maxTimedSpins*16;
	/**纳秒数目，对于这个值，自旋将比采用定时中断速度更快*/
	static final long spinForTimeoutThreshold=1000L;
	
	
	/**dual stack*/
	static final class TransferStack extends Transferer{
        /**node代表一个unfulfilled的消费者*/
		static final int REQUEST=0;
		
		/**node代表一个unfulfilled的生产者*/
		static final int DATA=1;
		
		/**node正在fulfiling另一个unfulfilled的DATA或者REQUEST*/
		static final int FULFILLING=2;
		
		/**m值必须大于0或者1才能反回true*/
		static boolean isFulFilling(int m){return (m&FULFILLING)!=0;}
		
		/**TransferStack的NODE类*/
		static final class SNode{
			AtomicReference<SNode>next=new AtomicReference<>();//stack中的下一个节点
			AtomicReference<SNode>match=new AtomicReference<>();//匹配当前节点的节点
			/*控制park和unpark(),表明当前节点进入了waiter角色，等待新来节点进行匹配，当前节点在栈结构上是新来节点的next
			 * 因此，s.next()=null说明当前节点s没有waiter，因此需要重新匹配
			 */
			volatile Thread waiter;
			Object item;//data;如果mode是REQUEST,那么就为null
			int mode;
			//item和mode不必要设置成volatile或者atomic，因为它们在写之前读之后有其它volatile和atomic操作;
			SNode(Object item){
				this.item=item;
			}
			
			boolean casNext(SNode cmp,SNode val){
				return next.get().equals(cmp)&&next.compareAndSet(cmp, val);//注意空指针的判断
			}
			
			/**
			 * 尝试匹配Node s到当前node(调用该方法者),如果成功则唤醒线程
			 * FulFillers(进入匹配的node)将会调用这个方法以确定自身的waiters(等待线程)
			 * Waiters将阻塞直到这些线程的node获得匹配
			 * @param s
			 * @return
			 */
			boolean tryMatch(SNode s){
				//判断match是否为null，如果为null，则判断waiter是否为空，如果为空然后unpark该线程至少一次
				if(match.get()==null && match.compareAndSet(null, s)){
					Thread w=waiter;//当前node的waiter线程
					if(w!=null){
						LockSupport.unpark(w);
					}
					return true;
				}
				//如果match不为null，则返回s与match是否相等
				return match.get()==s;
			}
			
			/**
			 * 通过匹配node s到当前node的match来取消当前node的wait线程
			 */
			void tryCancel(){
				match.compareAndSet(null, this);
			}
			
			boolean isCanceled(){
				return match.get()==this;
			}
			
		}
		/**TransferStack中的头结点*/
		AtomicReference<SNode> head =new AtomicReference<>();
		/**cas当前线程*/
		boolean casHead(SNode h,SNode nh){
			return h==head.get()&&head.compareAndSet(h, nh);
		}
		
		/**
		 * 创建或者重新设置一个node的属性值。该方法只能在transfer()方法中调用，在该方法中压入栈的node是懒创建的重复使用的，
		 * 此时可能有助于减少对head读取和CASer的时间间隔同时也能避免由于压入node失败的CASer造成的垃圾激增
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
		 * put 或 take 一个项目
		 */
		@SuppressWarnings("null")
		@Override
		Object transfer(Object e, boolean timed, long nanos) {
			/*
			 * 基本算法就是循环下面三个动作之一:
			 * 1.如果为空或者包含了同mode的其他nodes，那么尝试将该node压入栈，等待match，然后返回这个node，如果取消成功则返回null
			 * 2.如果栈顶node的mode是互补mode，那么尝试将一个fulfilling node压入栈，匹配(match)相关的等待node，然后将这两个node都弹出栈，
			 * 返回matched的item属性。匹配或者解链可能没有必要，因为其他线程正在进行第三个动作;
			 * 3.如果栈顶已经持有了另一个mode为fulfilling的节点，那么通过给它匹配或者pop操作帮助它出来，然后接着循环，帮助代码和fulfilling代码是一样必要的，
			 * 只是不需要返回item属性
			 * 概括起来，就是(1)栈顶为空或者栈顶的mode与进入的mode相同的情况(2)栈顶的mode不为空且与进入的mode互补的情况(3)栈顶的mode为fulfilling的情况
			 * 另外可以看成(1)当前节点为等待者角色，等待新来节点进行匹配(2)匹配者角色，匹配先进入栈的上一个节点(从栈结构来看是next节点)
			 * (3)是完成者角色，怎么将完成这角色pop出队列
			 */
			SNode s=null;//定义当前节点
			int mode=(e==null?REQUEST:DATA);
			
			//主循环
			for(;;){
				SNode h=head.get();//获取头结点
				if(h==null || h.mode==mode){//栈顶为空或者栈顶mode为进入的mode相同，则当前节点为等待者角色
					if(timed &&nanos<=0){//如果想立即获取对象
						if(h!=null && h.isCanceled()){//判断栈顶节点是否已经cancel了，如果是则重新设置head，重新判断，否则直接返回null
							head.compareAndSet(h, h.next.get());
						}else{
							return null;
						}
					}else if(head.compareAndSet(h, s=snode(s,e,h,mode))){//如果不是立即获取对象，则进入等待者角色，为当前节点赋值，设置栈顶节点为当前节点next
						SNode m=awaitFulfill(s, timed, nanos);//方法返回，说明等待完毕，原因要么是新来节点匹配了该等待节点，要么是该节点已经cancel了
						if(m==s){// s is canceled
							clean(s);  //清理所有canceled节点
							return null;
						}
						if((h=head.get())!=null &&h==m){//判断栈顶节点是否是当前节点s的匹配节点
							head.compareAndSet(h, s.next.get());
						}
						return mode==REQUEST?m.item:s.item;//如果是RUQUEST则返回匹配者item，否者返回自身item
					}
				}else if(!isFulFilling(mode)){//如果mode不为FUlFIll，就说明当前节点是匹配者节点
					if(h.isCanceled()){//判断头节点是否已经cancel了，如果是就重设头结点
						head.compareAndSet(h, h.next.get());//pop head 并且重新主循环
					}else if(head.compareAndSet(h, s=snode(s, e, h, FULFILLING|mode))){//给匹配者重新设置mode
						for(;;){//不停循环，直到找到等待者节点或者等待者节点全部没有了
							SNode m=s.next.get();//匹配者节点s的next节点肯定是等待者节点，那么m is s's match
							if(m==null){ //m=null,说明已经没有等待者节点了
								head.compareAndSet(s, null);
								s=null;
								break;//重新开始主循环
							}
							SNode mn=m.next.get();
							if(m.tryMatch(s)){//将等待者节点将匹配者节点进行匹配，如果成功则pop这两个节点
								head.compareAndSet(s, mn);
								return mode==REQUEST?m.item:s.item;
							}else{//失败则替换等待者节点，进行下一次匹配
								s.casNext(m, mn);
							}
						}
					}
				}else{//mode不为1也不为0，说明当前节点不是传入的，只能是循环过程中已经fulfill的节点，此时为head，也就是说头结点为fulfiller
					SNode m=h.next.get();//m是等待者节点，为h.match
					if(m==null){
						head.compareAndSet(h, null);
					}else{//此处要加else，因为前者没有break，为啥和前面不一样，因为这段代码不需要内循环，另外写了if语句一定要判断后面要不要写else语句
						SNode mn=m.next.get();
						if(m.tryMatch(h)){//m为等待者节点，其waiter属性不为空,因此将匹配者头结点匹配给m的match
							head.compareAndSet(h, mn);
						}else{
							head.get().casNext(m, mn);
						}
					}
				}
			}
			
		}
		/**
		 * 匹配SNode s之前一直（spin）自旋或者阻塞
		 * @param s   匹配节点
		 * @param timed 是否设置定时
		 * @param nanos 定时时间
		 * @return
		 */
		SNode awaitFulfill(SNode s,boolean timed,long nanos){
			/*
			 * 当一个节点或者线程将要阻塞时，需要设置该节点的waiter属性并且在实际阻塞之前要重新检查一下状态，因此covering race vs fulfill就会注意到该node的waiter不为
			 * 空，应该被唤醒
			 * 当线程被栈顶节点的调用所唤醒时，避免阻塞的自旋(spin)在生产者和消费者非常靠近时将优先于调用park方法，这只在多处理器情况下起作用
			 * 跳出主循环的次序反映了:interrupte()的优先权最高，spin第二，最次为park
			 */
			long lastTime=timed?System.nanoTime():0;//自旋开始时间设置;
			Thread w=Thread.currentThread();
			@SuppressWarnings("unused")
			SNode h=head.get();//这一步用该干什么？
			int spins=(shouldSpin(s)?(timed?maxTimedSpins:maxUntimedSpins):0);
			for(;;){
				if(w.isInterrupted()){//中断优先权最高，如果中断了，则取消该节点
					s.tryCancel();
				}
				SNode m=s.match.get();
				if(m!=null){
					return m;
				}
				if(timed){//定时，则在定时时间内自旋，否则cancel
					long now =System.nanoTime();
					nanos-=now-lastTime;
					lastTime=now;
					if(nanos<=0){
						s.tryCancel();
						continue;//不判断下面语句
					}
				}
				
				if(spins>0) {
					spins=shouldSpin(s)?spins-1:0;
					}else if(s.waiter==null){//检查waiter属性，下一次则就park
					s.waiter=w;
				   }else if(!timed){
					   LockSupport.park(this);//this 代表transfer()的调用者
				   }else if(nanos>spinForTimeoutThreshold){//如果剩余nanos大于spinForTimeOutThreshold则挂起线程
					   LockSupport.parkNanos(this, nanos);
				   }
			}
		}
		/**
		 * 将节点s从stack栈中清理出去，方法：遍历头结点到当前节点s.next()，清理所有已经cancel的节点
		 * @param s
		 */
		void clean(SNode s){
			s.item=null;
			s.waiter=null;//设为null
			
			//如果高并发情况下，s节点可能已经被remove，因此选择s的下一个节点
			SNode past=s.next.get();
			if(past!=null && past.isCanceled()){
				past=past.next.get();
			}
			
			SNode h;
			while((h=head.get())!=null && h!=past&& h.isCanceled()){//不断循环头结点，直到有个头结点没有canceled,如果头结点有已经cancel的，那么就头结点的next替换头结点
				head.compareAndSet(h, h.next.get());
			}
			//如果头节点没有cancel，那么从头节点的下一个节点开始遍历，清理cancel的节点
			while(h!=null && h!=past){
				SNode n=h.next.get();
				if(n!=null && n.isCanceled()){
					h.casNext(n, n.next.get());//替换下一节点
				}else{
					h=n;
				}
			}
		}
	
		/**
		 * 当头结点为null或者等于当前节点s或者头结点已经fulfill时返回true
		 * @param s
		 * @return
		 */
		boolean shouldSpin(SNode s){
			SNode h=head.get();
			return h==null || h==s || isFulFilling(h.mode);
		}
		
	}
	
	/**静态内部类只能访问外部类的静态实例和方法，不依赖于外部类的实例变量*/
	static final class TransferQueue extends Transferer{
		/*
		 * TransferQueue 是一个实现了Transferer的队列，从head到tail所有节点都是等待匹配的节点，包括应该取消但没来得及取消的节点；
		 * 匹配者节点将会从到head匹配，匹配完就advanceHead()将下一个等待者成为新的head,,新来的等待者节点添加到tail后面，并通过advanceTail()是自身变成tail
		 * 与栈TransferStack不同，TransferStack永远是新来的节点匹配栈顶的节点，如果匹配上则抛出两者，如果栈顶节点已经匹配则继续向下循环匹配节点直到匹配上
		 * 
		 */
		
		static final class QNode{
			AtomicReference<QNode> next;//队列中下一个QNode
			AtomicReference<Object>item;//利用CAS获取或者给与数据，获取数据则从null变成not null ，给与数据则从not null 变成null
			volatile Thread waiter;// 控制任务的park和unpark
			boolean isData; //标记是给予数据还是获取数据
			QNode(Object e,boolean isData){
				this.item=new AtomicReference<Object>(e);
				this.isData=isData;
			}
			/**
			 * 删除当前的下一节点，只有当下一节点的next存在时
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
			 * 将节点自身赋值给自身item，来标记该node需要从队列中删除
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
			 * 如果true则说明已经离开队列了，因为该node的next指针由于一次advanceHead()操作已经被遗忘
			 * @return
			 */
			boolean isOffList(){
				return this==next.get();
			}
		}
        AtomicReference<QNode> head;
        AtomicReference<QNode> tail;
        /*
         * 表明需要删除的节点，如果clean()方法时，cleanMe为null，则将需要删除的node 的前一个节点设置为cleanMe进行标记，
         * 表示cleanMe的下一个节点需要删除，一定不能通过setNext(null)的方式删除下一个节点，因为需要删除的节点后面也有next节点，需要拼接到队列上
         * 等到下一次调用clean方法时再删除
         * 注：链表上的节点一定不能设置某节点的next属性为null的方法来删除该节点的下一个节点，要想将需要删除的节点从队列移除，并设置该节点next=自身
         */
        AtomicReference<QNode>cleanMe;
        /**'
         * 标记需要删除节点的前任节点
         * @param cmp
         * @param val
         */
        boolean casCleanMe(QNode cmp,QNode val){
        	return cmp==cleanMe.get()&&cleanMe.compareAndSet(cmp, val);
        }
        
        /**
         * TransferQueue构造时head和tail指向同一个节点
         *     head|tail->null
         */
        TransferQueue(){
        	QNode h=new QNode(null,false);
        	head.set(h);
        	tail.set(h);
        }
        /**
         * 更新头结点并删除以前的头结点
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
         * 更新尾节点
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
         * 转移数据，可以表现为put或者take形式
         */
		@Override
		Object transfer(Object e, boolean timed, long nanos) {
			/*
			 * 新进来的节点s有两种角色，一种是等待者，一种是匹配者；从队头head到队尾tail都是等待者，新来的等待者成为新的队尾
			 * 匹配者则跑到队头去匹配等待者，匹配成功则将下一个等待者设为队头
			 * head->waiter->waiter->.......->waiter->tail
			 */
			QNode s=null;
			boolean isData=(e!=null);
			for(;;){
				QNode h=head.get();
				QNode t=tail.get();
				if(h==null || t==null)   //如果尚未构造，则重新循环
					continue;
				if(h==t || t.isData==isData){//如果为true，进入等待者角色，往tail后面挂新QNode
					QNode tn=t.next.get();//步骤1  获取t的下一个节点，用来判断是否是已经有新的tail了，如果有了就更新tail
					if(t!=tail.get()){//步骤2  如果tail已经更新，则重新循环，获取最新tail
						continue;
					}
					/*
					 * 此处之所以需要运行步骤2和步骤3判断，是因为有种可能tail.setNext(new QNode()),此时t==tail但是t.next不为零
					 * head->waiter->waiter->.......->waiter->tail==t->waiter
					 */
					if(tn!=null){//步骤3   如果next不为空，更新tail,
						advanceTail(t,tn);
						continue;
					}
					//运行到这，则说明t==tail&& tail.next==null
					if(s==null){
						s=new QNode(e,isData);//步骤4  创建一个QNode
					}
					if(timed && nanos<=0){
						return null; //put不会在此处添加失败，offer可能会
					}
					/**
					 * 关于步骤5和6解析
					 * 如果t==h，队列为空，(h=head)==(t=tail)->null,经过步骤5后 (h=head)==(t=tail)->waiter=s  
					 *                   经过步骤6后 (h=head)==t->(waiter==tail)=s  这样就实现了head 到tail 之间都是等待者节点
					 * 如果h！=t,队列中有等待者，则(h=head)->waiter->waiter->....->waiter->(tail=t),经过步骤5后,队列变成:
					 * (h=head)->waiter->waiter->....->waiter->(tail=t)->waiter=s
					 * 经过步骤6后则变成:(h=head)->waiter->waiter->....->waiter->t->(tail=waiter)=s
					 */
					if(!t.casNext(null, s)){//步骤5  如果cas失败，说明有并发的等待者，重新循环
						continue;
					}
					advanceTail(t,s);//步骤6  cas成功，则设置tail=s
					Object x=awaitFulfill(s,s.item.get(),timed,nanos);//等待完成匹配
					if(x==s){//如果为true则说明s已经canceled
						clean(t,s);
						return null;//此时添加失败返回null，说明线程中断过
					}
					//接下来需要取消s节点
					if(!s.isOffList()){
						advanceHead(t,s);//如果t位于head，则替换head为s
						if(x!=null) {
							/*这一步主要是因为匹配者匹配等待时，需要判断根据等待者的item进行是否cancel进行判断
							 * 可以看看匹配者匹配前提条件
							 */
							 s.item.compareAndSet(e, s);//如果x！=null，则isData=false，则取消该节点
						}
						s.waiter=null;// 将线程属性设置为null
					}
					return  (x!=null)?x:e;
				}else{
					QNode hn=h.next.get();
					if(t!=tail.get()||t.next.get()!=null ||h!=head.get()){//tail和head都已经被cas更新了，读取不是正确的队头队尾，重新开始
						continue;
					}
					if(hn!=null && hn.isCanceled()){//清除已经canceled节点
						head.compareAndSet(h, hn);
					}
				/*匹配前提条件，不通过则重新开始
				 * (1)isData=true，则等待者hn的item x==null,或者isData=false，则item x！=null 用一条语句写就是(isData!=(x!=null))=true
				 * (2)等待者hn的item x！=hn
				 * (3)cas替换item成功 
				 */
					Object x=hn.item.get();
					if(isData==(x!=null) || x==hn || !hn.casItem(x, e)){
						advanceHead(h,hn);//替换head
						continue;//不满足匹配条件则重新匹配
					}
					//匹配通过
					advanceHead(h,hn);//替换head，可能会失败
					LockSupport.unpark(hn.waiter);
					return (x!=null)?x:e;
				}
				
			}
		}
        /**
         * 通过spin或者lock来等待匹配
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
					s.tryCancel(e);//线程中断则取消该等待者节点
				}
				Object x=s.item;
				if(x!=e){
					return x; //唯一出口
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
		 * clean方法用来清除栈顶已经canceled节点，同时获取cleanMe dp需要删除的节点，dp.casNext()来删除该节点，
		 * 同时将需要删除的节点s的前任pred标记为cleanMe
		 * @param pred
		 * @param next
		 */
		@SuppressWarnings("unused")
		void clean(QNode pred,QNode s){
			s.waiter=null;//遗忘waiter
			while(pred.next.get()==s){//循环前提，pred节点为需要编辑
				QNode h=head.get();
				QNode hn=h.next.get();
				if(hn!=null && hn.isCanceled()){//不断重设head
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
				if(s!=t){//如果s不是tail节点，则该节点可以直接删除
					if(s==sn|| pred.casNext(s, sn)){//s==sn表示已经不再队列上，pre.casNext(s,sn)表示sn接替s成为pred的next，将s删除掉了
						return;
					}
				}
				//s位于队尾，则删除以前的取消的节点，并将该s的前任pred标记为cleanMe
				QNode dp=cleanMe.get();//获取以前保存的cleanMe节点
				QNode d=dp.next.get();//获取需要删除的节点
				if(dp!=null){
					QNode dn;
					/*
					 * d==null说明需要删除的节点已经不存在了,dp==d说明d已经不再队列中了，！d.isCanceled说明d没有被取消，这三种情况都需要将pred去掉cleanMe标志
					 * 如果不满足前三种，那么就开始删除d(删除条件，d不能为队尾，d.next要存在，d 在队列上)，并将dp的next替换成d的next，替换成功也需要清除pred的cleanMe标志
					 */
					if(d==null || dp==d || !d.isCanceled()||((d!=t&&(dn=d.next.get())!=null)&&dn!=d&&dp.casNext(d, dn))){
						casCleanMe(dp, null);
					}
					if(pred==dp){
						return ;
					}
				}else if(casCleanMe(null,pred)){
					return;//推迟清除s节点
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
	 * 移除队列头部元素并获取该元素
	 */
	public E poll() {
		return (E) transfer.transfer(null, true, 0);
	}
	/**
	 * SynchronousQueue总是返回null
	 */
	@Override
	public E peek() {
		return null;
	}

	/**
	 * 往队列中添加指定元素，成功返回true，失败返回false
	 */
	@Override
	public boolean offer(E e) {
	    if(e==null){
	    	throw new NullPointerException();
	    }
		return transfer.transfer(e, true, 0)!=null;//正常返回不可能出现null，出现null只能是添加失败或者线程中断
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
		if(!Thread.interrupted()) return false;//如果当前线程没有中断过，返回false
		throw new InterruptedException();//否则抛出中断异常
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
	 * SynchronousQueue队列中不存在任何元素
	 */
	@Override
	public int remainingCapacity() {
		return 0;
	}
	/**
	 * SynchronousQueue队列中不存在任何元素
	 * 移除队列中的元素，并添加到Collection c中
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
	 * SynchronousQueue队列中不存在任何元素
	 * 移除队列中的元素，并添加到Collection c中
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
	 * SynchronousQueue队列中不存在任何元素
	 */
	@Override
	public Iterator<E> iterator() {
		return Collections.emptyIterator();
	}
	/**
	 * SynchronousQueue队列中不存在任何元素
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
