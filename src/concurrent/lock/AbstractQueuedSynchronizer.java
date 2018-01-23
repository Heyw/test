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
 * （1）该类为实现依赖于先进先出 (FIFO) 等待队列的阻塞锁和相关同步器（信号量、事件，等等）提供一个框架。
 * 此类的设计目标是成为依靠单个原子型int值来代表状态的大多数同步器的
 * 有用基础（basis）;子类必须定义能改变状态的protected方法，并定义哪种状态对于此对象意味着获取或者释放；考虑这些条件，那么其他方法就可以实现排队以及阻塞机制。
 * 子类可以维持其他状态字段，但是为了实现同步的目的，只能通过使用getState(),setSate()以及compareAndSetState()来原子的更新代表状态的int值；
 * （2）子类必须被定义为非公共（non-public）内部帮助类，它们可以用来实现内部封闭类的同步属性。AbstractQueuedSynchronizer不实现任何同步的接口，而是
 * 定义了一些诸如acquireInterruptibly的方法，在适当时候被具体的锁和相关同步器激活来实现它们公共(public)方法;
 * （3）该类支持互斥模式（独占）和共享模式，或者同时支持这两种模式，处于独占模式下时，其他线程试图获取该锁将无法取得成功。
 * 在共享模式下，多个线程获取某个锁可能（但不是一定）会获得成功。
 * 此类并不“了解”这些不同，除了机械地意识到当在共享模式下成功获取某一锁时，下一个等待线程（如果存在）也必须确定自己是否可以成功获取该锁。
 * 处于不同模式下的等待线程可以共享相同的 FIFO 队列。通常，实现子类只支持其中一种模式，但两种模式都可以在（例如）ReadWriteLock 中发挥作用。
 * 只支持独占模式或者只支持共享模式的子类不必定义支持未使用模式的方法。 
 * （4）此类通过支持独占模式的子类定义了一个嵌套的 AbstractQueuedSynchronizer.ConditionObject 类，可以将这个类用作 Condition 实现。
 * isHeldExclusively() 方法将报告同步对于当前线程是否是独占的；使用当前 getState() 值调用 release(int) 方法则可以完全释放此对象；
 * 如果给定保存的状态值，那么 acquire(int) 方法可以将此对象最终恢复为它以前获取的状态。没有别的 AbstractQueuedSynchronizer 方法创建这样的条件，
 * 因此，如果无法满足此约束，则不要使用它。AbstractQueuedSynchronizer.ConditionObject 的行为当然取决于其同步器实现的语义。 
 * （5）此类为内部队列提供了检查、检测和监视方法，还为 condition 对象提供了类似方法。
 * 可以根据需要使用用于其同步机制的 AbstractQueuedSynchronizer 将这些方法导出到类中
 * （6）此类的序列化只存储维护状态的基础原子整数，因此已序列化的对象拥有空的线程队列。
 * 需要可序列化的典型子类将定义一个 readObject 方法，该方法在反序列化时将此对象恢复到某个已知初始状态。
 * @author heyw
 *
 */
public abstract class AbstractQueuedSynchronizer extends AbstractOwnableSynchronizer implements java.io.Serializable{


	private static final long serialVersionUID = -5643646764308509643L;
	/**
	 * 等待队列节点类
	 * （1）该等待队列是"CLH"锁队列的变体，“CLH”锁队列主要用于自旋锁。反过来我们将其用于阻塞同步器，
	 * 但是我们使用一样的基础手段来获取关于前任节点中的线程的控制信息；
	 * 一个节点当前任节点释放时，将会被通知。队列中任何持有单独等待线程的节点都服务于specific-notification-style的监视器，状态字段并不对保证持有锁的线程进行控制，
	 * 如果一个线程位于队列中的第一位，那么它有可能获取锁，但是这并不意味它一定能成功获取锁，只是有资格去竞争锁，所以当前释放锁的竞争线程也许需要等待锁。
	 * （2）为了进入一个CLH队列，只需要在队尾处拼接一个新节点，为了从该队列出去，只需设置头结点
	 * （3）往CLH队列插入元素，只需要在队尾进行一个原子性的操作，于是这里从队外到队中有一个原子分界点，相似地出队只需要对head进行更新操作，
	 * 然而节点也需要花费更多一点时间来判断它的继任者是哪个，部分时间是为了处理由于超时或者中断导致可能的取消。
	 * （4）prev链接主要用来处理取消。如果一个节点的继任者被取消了，那么该节点需要重新连接一个未被取消的节点
	 * （5）next链接也是为了实现阻塞机制，对于每个节点的线程id都保存在它自己的节点里面，因此前任节点通过传递下一个链接来通知唤醒下一个节点，从而决定是哪个线程；
	 * 对于继任节点的判断必须避免与给前任节点设置next字段的新来节点产生冲突。这在必要的时候可以通过当一个节点的继任节点变为null的时候
	 * 从自动更新的尾节点从后检查来得到解决。
	 * （6）cancellation给该算法带了一些保守。我们必须将一些其他取消节点移除，但是我们可能分不清该节点究竟是在当前节点前还是后面，
	 * 这个可以在cancellation时经常unpark继任者节点，允许它们连接到新的前任节点上来进行处理，除非我们可以判断出一个可以承担责任的未取消的前任节点。
	 * （7）CLH队列需要一个假的头部节点来启动，但是该节点不会在构造其中创建，因为如果没有竞争，就会造成浪费。相反，一旦产生了竞争，该节点以及头尾指针将会
	 * 被设置。
	 * （8）在Conditions上等待的线程使用相同的节点，但是会使用一个额外的链表（link）。因为Conditions只有在持有互斥锁时才能获取，所以它们可以将节点放入一个链式队列中；
	 * 关于await，一个节点将会被放入到condition队列中，关于signal,节点将会被转移到主队列中，status字段的特殊价值在于标记节点处于哪个队列。
	 */
      static final class Node{
    	  /**用来预示一个节点正在以共享模式等待的标记*/
    	 static final Node SHARED=new Node();
    	 /**用来预示一个节点正在以独占模式等待的标记*/
    	 static final Node EXCLUSIVE=null;
    	 
    	 /**表示该线程已经被取消了的waitStatus值*/
    	 static final int CANCELLED=1;
    	 /**表示该继任者线程需要unpark的waitStatus*/
    	 static final int SIGNAL=-1;
    	 /**表示该线程正在等待condition的waitStatus，就是处于条件队列，因为调用Condition.await而阻塞*/
    	 static final int CONDITION=-2;
    	 /**表示下一次acquireShare需要无条件传递，即传播共享锁*/
    	 static final int PROGATE=-3;
    	 
    	 /**
    	  * 状态字段，只能去以下这些值：
    	  * SIGNAL:当前节点的继任者节点已经或者马上要blocked，于是当前节点释放或者取消时必须unpark继任者节点。为了避免竞争，acquire()方法需要首先表明它们
    	  * 需要signal，然后尝试原子acquire()方法，失败那么进入block
    	  * CANCELLED:这个节点由于超时或者中断而被取消。该节点将不会改变这个状态，也就是说持有该节点的线程不会在被阻塞。
    	  * CONDITION：该节点处于一个condition队列中，它将不会被当成同步队列节点使用，除非被转移，此时，状态将被重新设置成0。(在这个地方使用这个值
    	  * 对这个字段的其他使用没有关系，但是能够使得机制简化)
    	  * PROPAGATE:releaseShared必须传递给其他节点，这将在doReleaseShared()方法中被设置（只对head节点有用），以便保证传递的持续，即使已经介入了其他操作。
    	  * 0：与上面的都不一样。
    	  * 
    	  * waitStatus值数字化设置可以简便使用。没有负值意味着一个节点不需要被signal，因此大多数节点不需要为了通常使用而检查，检查只是为了signal。
    	  * 这个字段被初始化为零因为同步节点，而因为condition节点设置为condition。通过CAS方式设置。
    	  *  	
    	  *  waitStatus表示节点的状态。其中包含的状态有：
    	  *    CANCELLED，值为1，表示当前的线程被取消； SIGNAL，值为-1，表示当前节点的后继节点包含的线程需要运行，也就是unpark；
    	  *    CONDITION，值为-2，表示当前节点在等待condition，也就是在condition队列中；
    	  *    PROPAGATE，值为-3，表示当前场景下后续的acquireShared能够得以执行；
    	  *    值为0，表示当前节点在sync队列中，等待着获取锁。

    	  */
    	 AtomicInteger waitStatus=new AtomicInteger();
    	 
    	 /**
    	  * 关联前任节点，当前节点以来该节点来检查awaitStatus。入队时会被指派，出队时将为null。一旦前任节点进行了取消操作，
    	  * 如果我们发现一个节点不为null的话（由于head节点经常不为null，所以通常能找到，一个节点如果成功acquire那么就会成为head），那么就缩短周期。
    	  * 取消的线程将不会成功acquire，同时一个线程只能取消自己的节点，而不能取消其他节点。
    	  */
    	 volatile Node prev;
    	 
    	 /**
    	  * 关联继任节点，一旦release，当前节点或者线程将会unpark该节点。入队时将会被指派，忽视取消节点会被调整，出队时被设置为null。
    	  * 直到attachment后enq操作将会设置前任的next字段，因此看到next字段为null的节点并不意味着处于队列中的尾部。
    	  * 然而next字段将变成null的话，我们可以can scan prev's from the tail to double-check(?).
    	  * 取消节点将会设置next字段指向自身而不是设置为null，to make life easier for isOnSyncQueue.
    	  */
    	AtomicReference<Node> next=new AtomicReference<>();
    	 
    	 /**将节点进入队列的线程，在构造器中初始化，使用后将被设置为null*/
    	 volatile Thread thread;
    	 
    	 /**
    	  * 关联到下一个在condition等待或者特殊值SHARED的节点，因为只有持有独占模式时才可以进入condition队列，
    	  * 所以我们需要一个简单的队列来保管等待condition的节点。节点接着会被转移到这个队列来re-acquire。同时，因为condition队列只能是独占模式的，
    	  * 我们通过使用一个专门值保留一个字段，来表示共享模式。
    	  */
    	 Node nextWaiter;
    	 
    
    	    /**如果等待节点是SHARED，那么返回true*/
    	    final boolean isShared(){
    	    	return nextWaiter==SHARED;
    	    }
            
    	    /**空指针检查是可以忽略的，但是在此处的存在是为了帮助垃圾回收*/
    	    final Node predecessor()throws NullPointerException{
    	    	Node p=prev;
    	    	if(p==null)
    	    		throw new NullPointerException();
    	    	else
    	    		return p;
    	    }
    	    Node(){//被用作建立初始化的head和shared的标记
    	    }
    	    
    	    Node(Thread thread,Node mode){//被addWaiter使用
    	    	this.thread=thread;
    	    	this.nextWaiter=mode;
    	    }
    	    Node(Thread thread,int waitStatus){//被Condition使用
    	    	this.thread=thread;
    	    	this.waitStatus.set(waitStatus);
    	    }
      }
      
	  /**
	     * 等待队列的头结点，懒初始化。除去初始化，只能通过方法setHead()来进行修改。注意：如果头结点存在，它的waitStatus将会保证不会成为CANCELLED。
	     * 头结点代表当前持有锁且正在运行的节点。
	     */
	    private transient AtomicReference<Node>  head=new AtomicReference<>();

	    /**
	     * 等待队列的尾节点，懒初始化.  只能通过enq操作在尾部添加等待节点来修改。
	     */
	    private transient AtomicReference<Node> tail=new AtomicReference<>();

	    /**
	     *同步状态。state用来表示当前状态是否有锁
	     */
	    private AtomicInteger state=new AtomicInteger();
	    
	    /**返回当前同步状态，state是volatile，volatile的使用时更新值不依赖于当前值*/
	    protected final int getState(){
	    	return state.get();
	    }
	    /**设置同步状态的值*/
	    protected final void setState(int newState){
	    	state.set(newState);
	    }
	    
	    /**CAS设置state值*/
	    protected final boolean casState(int expect,int update){
	    	return state.compareAndSet(expect, update);
	    }
	    
	    /**该值用来判断是选择自旋还是选择定时park*/
	    static final long spinForTimeOutThreshold=1000L;
	    
	    /**将节点加入队列，如果必要时需要将队列初始化*/
	    private Node enq(Node node){
	    	for(;;){
	    		Node t=tail.get();//
	    		if(t==null){//队尾为空，需要初始化。
	    			if(tail.compareAndSet(t, node)){
	    				head=tail;
	    			}
	    		}else{
	    			node.prev=t;
	    			if(tail.compareAndSet(t, node)){//重新设置队尾
	    				t.next.set(node);
	    				return t;//返回尾节点的前任节点
	    			}
	    		}
	    	}
	    }
	    
	    /**为当前线程和指定的mode创建一个节点并加入队列
	     */
	    private Node addWaiter(Node mode){
	    	Node node=new Node(Thread.currentThread(),mode);
	    	Node pred=tail.get();//一开始最快速的加入队列，如果失败则enq（）方法进入队列
	    	if(pred!=null){
	    		node.prev=pred;
	    		if(tail.compareAndSet(pred, node)){//加入队列成功，则返回
	    			pred.next.set(node);
	    			return node;
	    		}
	    	}
	    	enq(node);
	    	return node;
	    }
	    
	    /**将队列头设置为node，只能在acquire方法中调用，处于帮助回收垃圾和压制没必要的signal和travels的目的，将属性设置为null*/
	    private void setHead(Node node){
	    	head.set(node);
	    	node.thread=null;
	    	node.prev=null;
	    }
	    
	    /**唤醒某个节点的继任者，如果存在*/
	    private void unparkSuccessor(Node node){
	    	/*获取该节点的waitStatus，如果小于零说明需要signal，为了准备激活，将其设置为0
	    	 * 如果waitStatus被等待线程修改了，那么将导致失败，这是可以允许的
	    	 * */
	    	int ws=node.waitStatus.get();
	    	if(ws<0)
	    		node.waitStatus.compareAndSet(ws, 0);//将头结点ws设置为0，如果同步队列中的头结点的ws等于零那么不能调用该方法
	    	
	    	  /*该节点的继任者持有要unpark的线程，如果继任者为null或者waitStatus大于零，那么从队尾向前找，找到一个没有被取消的继任者*/
	    	/*为什么从后面向前查找唤醒节点，而不是从前向后。因为CLH队列很容易发生中断，被中断的节点的waitStatus被CANCELLED，被CANCELLED的节点就会被
	    	 * 踢出队列，如何踢出?前任节点的next不再指向该节点，而是指向下一个非CANCELLED的节点，而该节点的next指向自身，
	    	 * 从而从前往后寻找会出现不断查找自身的可能，因此从后往前查找*/
		    Node s=node.next.get();
		    if(s==null || s.waitStatus.get()>0){//后继节点不存在或者已经取消，那么从队尾找到距离该节点最近的一个非CANCELLED后继节点作为唤醒节点
		    	s=null;
		    	for(Node t=tail.get();t!=null&&t!=node;t=t.prev){
		    		if(t.waitStatus.get()<0){
		    			s=t;
		    		}
		    	}
		    }
		    if(s!=null)
		    LockSupport.unpark(s.thread);//唤醒该线程
	    }
	  
	    /**共享模式的release动作，通知successor并保证propagation（对于exclusive 模式，release只是关系到如果head需要signal时那么unparkSuccessor）
	     * */
	    private void doReleaseShared(){
	    	/**
	    	 * 保证一个release的传递，即使存在其他正在处理的acquires/release。这种处理以通常的方式尝试将unparkSuccessor头结点的successor，如果需要signal的话。
	    	 * 但是如果该successor不需要signal，那么设置状态为propagate，来保证一旦release，propagation将持续。
	    	 * 此外，我们还需要进行循环，为了防止我们正在进行这种处理时，加入了新的节点。同样，与其他unparkSuccesso不一样的是，
	    	 * 我们需要知道设置状态的cas是否成功，如果失败那么重新检查
	    	 */
	    	Node h=head.get();
	    	for(;;){
		    	if(h!=null && h!=tail.get()){//如果结点不为空且不等于尾节点
		    		int ws=h.waitStatus.get();
		    		if(ws==Node.SIGNAL){//该节点状态为SIGNAL，那么设置为状态为0
		    			if(!h.waitStatus.compareAndSet(Node.SIGNAL, 0))
		    				continue;//不断将h的waitStatus设置为0
		    			unparkSuccessor(h);
		    		}else if(ws==0&&!h.waitStatus.compareAndSet(0, Node.PROGATE)){//如果cas失败重新循环
		    			continue;
		    		}
		    	}
		    	if(h==head.get())//如果head改变了，那么重新循环，否则退出
		    		break;
	    	}
	    }
	    
	    /**设置队列头节点，并且检查头节点的successor是否处于waiting状态，如果是，那么如果propagate大于零或者已经被设置了就进行传递唤醒（
	     * 唤醒等待队列中下一个等待节点）。
	     * 该方法主要作用是设置头结点，释放锁
	     */
	    private void setHeadAndPropagate(Node node,int propagate){
	    	Node h=head.get();
	    	head.set(node);
	    	/*
	    	 * 尝试通知下一个队列中的节点：propate被调用者指示，或者被前一个操作记录（注意：这对waitStatus使用了信号检查，因为PROPAGATE可能转变成SIGNAL）
	    	 * 同时，下一个节点以共享模式等待，或者可能为null；
	    	 * 在这些检查中保守策略可能引起大量不必要的唤醒，但是只有在大量竞争的acquire/release的情况下才会出现，因此大多数需要现在或者尽快的signal
	    	 */
	    	if(h==null || h.waitStatus.get()<0||propagate>0){
	    		Node s=node.next.get();
	    		if(s==null || s.isShared()){
	    			doReleaseShared();
	    		}
	    	}
	    }
	    
	    /*tryAcquire,tryRelease,tryAcquireShared,tryReleaseShared的实现是实现各类锁的关键，其中状态state也很重要
	     * */
	    /**
	     * 试图在独占模式下获取对象状态。此方法应该查询是否允许它在独占模式下获取对象状态，如果允许，则获取它。 
	     * 此方法总是由执行 acquire 的线程来调用。如果此方法报告失败，则 acquire 方法可以将线程加入队列（如果还没有将它加入队列），
	     * 直到获得其他某个线程释放了该线程的信号。可以用此方法来实现 Lock.tryLock() 方法。 默认实现将抛出 UnsupportedOperationException。 
	     * @param arg-acquire 参数。该值总是传递给 acquire 方法的那个值，或者是因某个条件等待而保存在条目上的值。该值是不间断的，并且可以表示任何内容。 
	     * @return
	     * @throws IllegalMonitorStateException-如果正在进行的获取操作将在非法状态下放置此同步器。必须以一致的方式抛出此异常，以便同步正确运行。
	     */
	    protected boolean tryAcquire(int arg){
	    	throw new UnsupportedOperationException();
	    }
	    
	    /**
	     * 试图设置状态来反映独占模式下的一个释放。此方法总是由正在执行释放的线程调用。 默认实现将抛出 UnsupportedOperationException。
	     * @param arg- release 参数。该值总是传递给 release 方法的那个值，或者是因某个条件等待而保存在条目上的当前状态值。该值是不间断的，并且可以表示任何内容。 
	     * @return
	     * @throws IllegalMonitorStateException如果正在进行的释放操作将在非法状态下放置此同步器。必须以一致的方式抛出此异常，一边同步正确运行。
	     */
	    protected boolean tryRealease(int arg){
	    	throw new UnsupportedOperationException();
	    }
	    
	    /**
	     * 试图在共享模式下获取对象状态。此方法应该查询是否允许它在共享模式下获取对象状态，如果允许，则获取它。 
	     * 此方法总是由执行 acquire 线程来调用。如果此方法报告失败，则 acquire 方法可以将线程加入队列（如果还没有将它加入队列），
	     * 直到获得其他某个线程释放了该线程的信号。 
	     * 默认实现将抛出 UnsupportedOperationException。 
	     * @param arg
	     * @return
	     */
	    protected int tryAcquireShared(int arg){
	    	throw new UnsupportedOperationException();
	    }
	    
	    /**
	     * 试图设置状态来反映共享模式下的一个释放。 此方法总是由正在执行释放的线程调用。 默认实现将抛出 UnsupportedOperationException。 
	     * @param arg
	     * @return
	     */
	    protected boolean tryReleaseShared(int arg){
	    	throw new UnsupportedOperationException();
	    }
	    
	    /**
	     * 如果对于当前（正调用的）线程，同步是以独占方式进行的，则返回 true。
	     * 此方法是在每次调用非等待 AbstractQueuedSynchronizer.ConditionObject 方法时调用的。（等待方法则调用 release(int)） 
	     * 默认实现将抛出 UnsupportedOperationException。此方法只是 AbstractQueuedSynchronizer.ConditionObject 方法内进行内部调用，
	     * 因此，如果不使用条件，则不需要定义它。 
	     * @return
	     */
	    protected boolean isHeldExclusively(){
	    	throw new UnsupportedOperationException();
	    }
	    
	    /**
	     * 该方法主要用来将已经追加到队列的线程的节点（addWaiter方法返回值）进行阻塞，
	     * 但阻塞前如果前驱节点为头结点，那么尝试通过方法tryAcquire()重试能否获得锁资源，如果成功则无需阻塞，直接返回
	     * 该方法就是在同步队列中不断循环等待获取锁资源
	     * @param node
	     * @param arg
	     * @return
	     */
	    final boolean acquiredQueued(Node node,int arg){//该方法其实就是不断循环尝试获取锁资源如果失败则进入阻塞状态
	    	boolean failed=true;
	    	try{
	    		boolean interrupted=false;
	    		for(;;){
	    			Node p=node.prev;
	    			//此处也实现了老头结点退出同步队列
	    			if(p==head.get()&&tryAcquire(arg)){//如果当前任节点是头结点并且能够获取状态，代表当前节点占有锁，那么设置当前节点为头结点
	    				head.set(node);
	    				p.next=null;
	    				failed=false;
	    				return interrupted;
	    			}else if(shouldParkAfterFailedAcquire(p, node)&&parkAndCheckInterrupt()){//如果失败则进入等待状态
	    				interrupted=true;
	    			}
	    		}
	    	}finally{
	    		if(failed)//到达这里，只能是当前节点已经获得锁资源或者发生异常
	    			cancelAcquire(node);
	    	}
	    }
	    
	    /**
	     * 取消一个试图获取锁资源的节点
	     * @param node
	     */
	    private void cancelAcquire(Node node){
	    	if(node==null)
	    		return ;
	    	node.thread=null;//将取消节点线程设置为null，便于gc
	    	Node pred=node.prev;
	    	//跳过已经取消的节点
	    	while(pred.waitStatus.get()>0){
	    		node.prev=pred=pred.prev;
	    	}
	    	//predNext是需要分离的节点
	    	Node predNext=pred.next.get();
	    	
	    	//原子性地将node设置为cancelled,设置完后其他节点可以以此来跳过该节点，在设置之前，这个节点和其他节点无任何关联
	    	node.waitStatus.set(Node.CANCELLED);
	    	
	    	if(node==tail.get()&&tail.compareAndSet(node, pred)){//如果node为tail，那么设置tail为pred，然后设置pred的next为null
	    		pred.next.compareAndSet(predNext, null);
	    	}else{
	    		int ws;
	    		//如果pred节点不为头结点且等待状态为signal或者可以设置为signal，并且线程不为null，那么设置pred的next为node的next，否则释放node的继任者锁资源
	    		if(pred!=head.get()&&((ws=pred.waitStatus.get())==Node.SIGNAL||pred.waitStatus.compareAndSet(ws, Node.SIGNAL))
	    				&&pred.thread!=null){
	    			Node next=node.next.get();
	    			if(next!=null&&next.waitStatus.get()<=0)//如果next.waitStatus不大于零，既没有被取消
	    			   pred.next.compareAndSet(predNext, next);
	    		}else{//如果pred为头结点或者waitStatus不为signal，那么继任者持有锁资源，需要释放
	    			unparkSuccessor(node);
	    		}
	    	}
	    	
	    	node.next.set(node);//help gc
	    }
	    /**
	     * 检查并更新获取锁失败的节点。如果当前线程需要阻塞，那么返回true。在整个acquire循环中，这是主要的signal控制。这里需要pred=node.prev
	     * @param pred
	     * @param node
	     * @return
	     */
	    private static boolean shouldParkAfterFailedAcquire(Node pred,Node node){
	    	int ws=pred.waitStatus.get();
	    	if(ws==Node.SIGNAL){//如果前任节点waitStatus为signal，那么当前节点可以阻塞
	    		/*说明当前节点已经设置状态了，需要一个release来signal它，于是可以将当前节点park*/
	    		return true;
	    	}
	    	if(ws>0){//如果前任节点已经取消，那么跳过该节点不断向前循环，直到找到没有取消的前任节点，将其设置为node的前任节点
	    		do{
	    		     node.prev=pred=pred.prev;
	    		     }while(pred.waitStatus.get()>0);
	    		pred.next.set(node);
	    	}else{
	    		/*waitStatus必须是0或者PROPAGATE。表明我们需要一个signal而不是park，需要重新尝试以确保在parking之前不能获取锁*/
	    		pred.waitStatus.compareAndSet(ws, Node.SIGNAL);//这段代码确定了同步队列中的节点状态基本上都是SIGNAL
	    	}
	    	/*这里需要返回false，是因为前任节点此时有可能已经释放锁了，但因其waitStatus在释放锁时还未被设置为SIGNAL而未触发唤醒等待线程操作
	    	 * 因此必须返回false来重新获取一次锁*/
	    	return false;
	    }
	    
	    /**
	     * 将当前线程阻塞，同时检查是否线程是否中断
	     * @return
	     */
	    private final boolean parkAndCheckInterrupt(){//首先阻塞当前线程，然后返回当前是否已经中断了，同时清除中断标志
	    	LockSupport.park(this);
	    	return Thread.interrupted();
	    }
	    
	    /**中断当前线程的方法*/
	    private static void selfInterrupt(){
	    	Thread.currentThread().interrupt();
	    }
	    
	    /**
	     * 以独占模式获取对象，忽略中断。通过至少调用一次 tryAcquire(int) 来实现此方法，并在成功时返回。
	     * 否则在成功之前，一直调用 tryAcquire(int) 将线程加入队列，线程可能重复被阻塞或不被阻塞。可以使用此方法来实现 Lock.lock() 方法。
	     * @param arg
	     */
	    public final void acquire(int arg){
	    	if(!tryAcquire(arg)&&acquiredQueued(addWaiter(Node.EXCLUSIVE), arg))//如果获取锁失败则添加等待节点到同步队列
	    		selfInterrupt();
	    }
	    /**
	     * 以独占模式获取对象，如果被中断则中止。
	     * 通过先检查中断状态，然后至少调用一次 tryAcquire(int) 来实现此方法，并在成功时返回。
	     * 否则在成功之前，或者线程被中断之前，一直调用 tryAcquire(int) 将线程加入队列，线程可能重复被阻塞或不被阻塞。
	     * 可以使用此方法来实现 Lock.lockInterruptibly() 方法。
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
		    		if(p==head.get()&&tryAcquire(arg)){//成功获取锁
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
		    		  if(p==head.get()&&tryAcquire(arg)){//成功获取锁
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
	     * 以共享模式获取对象，忽略中断。通过至少先调用一次 tryAcquireShared(int) 来实现此方法，并在成功时返回。
	     * 否则在成功之前，一直调用 tryAcquireShared(int) 将线程加入队列，线程可能重复被阻塞或不被阻塞。
	     * @param arg
	     */
	    public final void acquireShared(int arg){
	    	if(tryAcquireShared(arg)<0)//如果尝试获取共享锁失败
	    		doAcquireShared(arg);
	    }
	    
	    /**
	     * 以共享模式获取对象，如果被中断则中止。通过先检查中断状态，然后至少调用一次 tryAcquireShared(int) 来实现此方法，并在成功时返回。
	     * 否则在成功或线程被中断之前，一直调用 tryAcquireShared(int) 将线程加入队列，线程可能重复被阻塞或不被阻塞。 
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
	     * 以共享模式获取对象，如果被中断则中止。通过先检查中断状态，然后至少调用一次 tryAcquireShared(int) 来实现此方法，并在成功时返回。
	     * 否则在成功或线程被中断之前，一直调用 tryAcquireShared(int) 将线程加入队列，线程可能重复被阻塞或不被阻塞。 
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
	     * 以共享忽略中断的方式获取锁
	     * @param arg
	     */
	    private void doAcquireShared(int arg){
	    	boolean failed=true;
	    	Node node=addWaiter(Node.SHARED);//添加共享node
	    	try{
	    		boolean interrupted=false;
	    		  for(;;){
	    			  final Node p=node.predecessor();//获取前任节点
	    			  if(p==head.get()){//如果前任节点为头节点
	    				  int r=tryAcquireShared(arg);
	    				  if(r>=0){//获取到锁
	    					  setHeadAndPropagate(node, r);
	    					  p.next=null;
	    					  if(interrupted)//如果应该挂起，那么中断当前线程
	    						  selfInterrupt();
	    					  failed=false;
	    					  return ;
	    				  }
	    			  }
	    			  if(shouldParkAfterFailedAcquire(p, node)&&parkAndCheckInterrupt()){//检查是否应该挂起，进入阻塞
	    				  interrupted=true;
	    			  }
	    		  }
	    	}finally{
	    		if(failed)
	    			cancelAcquire(node);
	    	}
	    }
	    
	    /**
	     * 以共享但不忽略中断的方式获取锁
	     * @param arg
	     * @throws InterruptedException 
	     */
	    private void doAcquireSharedInterruptedly(int arg) throws InterruptedException{
	    	boolean failed=true;
	    	Node node=addWaiter(Node.SHARED);
	    	try{
	    	     final Node p=node.predecessor();//获取前任节点
	    	     for(;;){
	    	    	 if(p==head.get()){//判断头结点是否为该前任节点，如果是那么尝试获取锁
	    	    		 int r=tryAcquireShared(arg);//尝试获取锁
	    	    		 if(r>=0){//获取锁
	    	    			 setHeadAndPropagate(node, r);
	    	    			 p.next=null;//help gc
	    	    			 failed=false;
	    	    			 return;
	    	    		 }
	    	    		 //如果没有获取锁，那么将当前节点挂起，挂起后检测是否中断，如果中断抛出异常
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
	    	long lastTime=System.nanoTime();//获取时间
	    	try{
	    		final Node p=node.predecessor();
	    		for(;;){
	    			if(p==head.get()){//如果前任节点是head的话，那么尝试获取锁
		    			int r=tryAcquireShared(arg);
		    			if(r>=0){
		    				setHeadAndPropagate(p, r);//设置head并且传递唤醒下一个等待线程。
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
	     * 以独占模式释放对象。如果 tryRelease(int) 返回 true，则通过消除一个或多个线程的阻塞来实现此方法。可以使用此方法来实现 Lock.unlock() 方法
	     * @param arg
	     * @return
	     */
	    public final boolean release(int arg){
	    	if(tryRealease(arg)){//实现类中释放锁资源，释放成功，则唤醒head的下一个signal节点
	    		Node h=head.get();
	    		if(h!=null && h.waitStatus.get()!=0)//如果头结点ws为0，则不能唤醒下一个节点
	    			unparkSuccessor(h);
	    		return true;
	    	}
	    	return false;
	    }
	    
       /**
        * 以共享模式释放对象。如果 tryReleaseShared(int) 返回 true，则通过消除一个或多个线程的阻塞来实现该方法。
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
	     * 查询是否有正在等待获取的任何线程。注意，随时可能因为中断和超时而导致取消操作，返回 true 并不能保证其他任何线程都将获取对象。 
	     * 在此实现中，该操作是以固定时间返回的
	     * @return
	     */
	    public final boolean hasQueuedThreads(){
	    	return head.get()!=tail.get();
	    }
	    
	    /**
	     * 查询是否其他线程也曾争着获取此同步器；也就是说，是否某个 acquire 方法已经阻塞。 在此实现中，该操作是以固定时间返回的。 
	     * @return
	     */
	    public final boolean hasContended(){
	    	return head.get()!=null;
	    }
	    
	    /**
	     * 返回队列中第一个（等待时间最长的）线程，如果目前没有将任何线程加入队列，则返回 null. 
	     * 在此实现中，该操作是以固定时间返回的，但是，如果其他线程目前正在并发修改该队列，则可能出现循环争用。
	     * @return
	     */
	    public final Thread getFirstQueuedThread(){
	    	return (head.get()==tail.get())?null:fullGetFirstQueuedThread();
	    }
	    
	    private final Thread fullGetFirstQueuedThread(){
	    	  /*
	         * 第一个节点正常情况下是head.next，尝试获取该节点的Thread字段，必须保证读一致性：如果thread字段为null，或者s.prev不再指向head，
	         * 那么一些其他线程在我们读取过程中可能并发执行了setHead()方法。在遍历之前尝试两次
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
	         * Head的next字段可能还没有被设置，也可能在setHead()后导致的没有设置。因此我们必须检查tail是否是实际第一个节点。
	         * 如果不是，我们继续从tail到head遍历来寻找第一个节点
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
	     * 如果给定线程的当前已加入队列，则返回 true。 该实现将遍历队列，以确定给定线程是否存在。 
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
	     * 如果存在显而易见地第一个进入队列的线程是exclusive 模式的，那么返回true；
	     * 如果该方法返回true，并且该线程尝试以共享模式获取锁，那么可以保证该线程不是第一个进入队列的线程。
	     * 该方法只能用于ReentrantReadWriteLock中。
	     * @return
	     */
	    final boolean apparentlyFirstQueuedIsExcusive(){
	    	Node s,h;
	    	return (h=head.get())!=null&&(s=h.next.get())!=null
	    			   &&!s.isShared()&&s.thread!=null; 
	    }
	    
	    /**
	     * 从同步队列尾部向前查找该节点，如果不存在返回false
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
	    
	    /**该方法用来判断一个节点是否处于同步队列中*/
	    final boolean isOnSyncQueued(Node node){
	    	if(node.waitStatus.get()==Node.CONDITION||node.prev==null)
	    		return false;
	    	if(node.next.get()!=null)//如果next为null，不能说明该节点不存在于同步队列上，如果不为null，那么肯定存在这个队列上。
	    		return true;
	    	return findNodeFromTail(node);
	    }
	    
	    /**
	     * 用当前的状态激活releas方法，如果失败抛出IllegalMonitorException
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
	     * 该方法用于将condition队列中的节点转移到同步队列中。成功则返回true，失败说明该节点在接收signal之前已经canceled
	     * @param node
	     * @return
	     */
	    final boolean transferForSignal(Node node){
	    	if(!node.waitStatus.compareAndSet(Node.CONDITION, 0)){
	    		return false;
	    	}
	    	Node q=enq(node);//将node加入到同步队列，返回前驱节点
	    	int ws=q.waitStatus.get();
	    	/*如果前驱节点已经canceled或者设置waitStatus失败，那么将该前驱节点线程唤醒以重新同步。*/
	    	if(ws>0||!q.waitStatus.compareAndSet(ws, Node.SIGNAL))
	    		LockSupport.unpark(q.thread);
	    	return true;
	    }
	    
	    /**
	     * 如果必要的话，在被取消等待之后，将节点转移到同步队列中去。如果在接收signal之前转移成功，则返回true
	     * @param node
	     * @return
	     */
	    final boolean transferAfterCancelledWait(Node node){
	    	if(node.waitStatus.compareAndSet(Node.CONDITION, 0)){//取消等待，然后加入到同步队列，此时该节点waitStatus为0
	    		enq(node);
	    		return true;
	    	}
	    	
	    	/*如果我们失去一次signal，那么在该节点进入同步队列之前该线程做不了任何事。同样因为在未完成转移中取消是非常罕见和短暂的，因此不断自旋*/
	    	while(!isOnSyncQueued(node))
	    		Thread.yield();//挂起当前线程，唤醒其它线程
	    	return false;
	    }
	    /**
	     * 在Condition队列中，节点的waitStatus不为Condition，说明已经canceled
	     * @author heyw
	     *
	     */
	    public class ConditionObject implements java.io.Serializable,Condition{
			private static final long serialVersionUID = 5512533359500884566L;
			
			/*Condition队列中第一个节点*/
			private transient Node firstWaiter;
			/*Condition队列中最后一个节点*/
			private transient Node lastWaiter;
			
			public ConditionObject(){}
			
			/*添加一个新的节点到等待队列*/
			private Node addConditionWaiter(){
				Node t=lastWaiter;//获取最后一个等待节点
				if(t!=null&&t.waitStatus.get()!=Node.CONDITION){//如果lastWaiter已经取消了，那么剔除该节点
					unlinkCanceledWaiters();
					t=lastWaiter;
				}
				Node node=new Node(Thread.currentThread(),Node.CONDITION);//增加一个新等待节点
				 if(t==null){
					 firstWaiter=node;//如果lastWaiter等于null，说明队列为空
				 }else{
					 t.nextWaiter=node;//将node挂在最后面
				 }
				 lastWaiter=node;
				 return node;
			}
			/**
			 * 遍历整个Condition队列，将已经取消的节点剔除。
			 */
			private void unlinkCanceledWaiters(){
				Node t=firstWaiter;
				Node trail=null;//代表t的上一个等待节点，为null，说明t为第一个等待节点
				while(t!=null){
					Node next=t.nextWaiter;//获取下一个等待节点
					if(t.waitStatus.get()!=Node.CONDITION){//如果当前等待节点已经cancelled，那么剔除该节点
						t.nextWaiter=null;
						if(trail==null)
							firstWaiter=next;//如果firstWaiter已经取消，则将下一个等待节点next设置为firstWaiter
						else
							trail.nextWaiter=next;//将上一个节点的nextWaiter设置为下一个等待节点next
						if(next==null){//如果当前节点为lastWaiter，那么将lastWaiter设置为trail
						    lastWaiter=trail;	
						}
					}else
						trail=t;
			   t=next;
					
				}
				
			}
			/**
			 * 在等待状态时检查线程是否中断，如果没有中断，返回0，如果中断了且发生在signal之前，返回REINTERRUPT，发生在signal之后，返回THROW_IE
			 * @param node
			 * @return
			 */
			private  int checkInterruptWhileWaiting(Node node){
				return Thread.interrupted()?
						(transferAfterCancelledWait(node)?REINTERRUPT:THROW_IE):0;
			}
			
			/**
			 * 根据mode，中断线程，或者抛出异常，或者什么都不做
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
			 * 造成当前线程在接到信号或被中断之前一直处于等待状态。 
			 * 与此 Condition 相关的锁以原子方式释放，并且出于线程调度的目的，将禁用当前线程，且在发生以下四种情况之一 以前，当前线程将一直处于休眠状态： 
			 * 其他某个线程调用此 Condition 的 signal() 方法，并且碰巧将当前线程选为被唤醒的线程；或者 其他某个线程调用此 Condition 的 signalAll() 方法；
			 * 或者其他某个线程中断当前线程，且支持中断线程的挂起；或者 发生“虚假唤醒” 
			 * 在所有情况下，在此方法可以返回当前线程之前，都必须重新获取与此条件有关的锁。在线程返回时，可以保证 它保持此锁。 
			 * 如果当前线程： 在进入此方法时已经设置了该线程的中断状态；
			 * 或者在支持等待和中断线程挂起时，线程被中断， 则抛出 InterruptedException，并清除当前线程的中断状态。在第一种情况下，没有指定是否在释放锁之前发生中断测试。 
			 * 实现注意事项 
			 * 假定调用此方法时，当前线程保持了与此 Condition 有关联的锁。这取决于确定是否为这种情况以及不是时，如何对此作出响应的实现。通常，将抛出一个异常（比如 IllegalMonitorStateException）并且该实现必须对此进行记录。 
			 * 与响应某个信号而返回的普通方法相比，实现可能更喜欢响应某个中断。在这种情况下，实现必须确保信号被重定向到另一个等待线程（如果有的话）。 
			 */
			public void await() throws InterruptedException {
				if(Thread.interrupted())
					throw new InterruptedException();
				Node node=addConditionWaiter();//增加等待节点
				int savedState=fullyRelease(node);//释放锁上资源，返回锁状态，也就是移除同步队列中的节点，加入到condition队列中去
				int interruptMode=0;//中断模型
				while(!isOnSyncQueued(node)){//如果节点没有处于同步队列中，就没有资格竞争锁则中断该线程，进入队列一般是通过signal方法
					LockSupport.park(this);
					if((interruptMode=checkInterruptWhileWaiting(node))!=0){//如果等待过程中，中断了，那么跳出循环
						break;
					}
				}
				//当前线程唤醒后，从新竞争锁，如果竞争不到则陷入沉睡
				if(acquiredQueued(node, savedState)&&interruptMode!=THROW_IE){//
					interruptMode=REINTERRUPT;
				}
				if(node.nextWaiter!=null)
					unlinkCanceledWaiters();
				if(interruptMode!=0)
					reportInterrupteAfterWait(interruptMode);
			}

		    /**
		     * 造成当前线程在接到信号之前一直处于等待状态。 与此条件相关的锁以原子方式释放，并且出于线程调度的目的，将禁用当前线程，
		     * 且在发生以下三种情况之一 以前，当前线程将一直处于休眠状态： 
		     * 其他某个线程调用此 Condition 的 signal() 方法，并且碰巧将当前线程选为被唤醒的线程；
		     * 或者 其他某个线程调用此 Condition 的 signalAll() 方法；
		     * 或者 发生“虚假唤醒” 
		     * 在所有情况下，在此方法可以返回当前线程之前，都必须重新获取与此条件有关的锁。在线程返回时，可以保证 它保持此锁。 
		     * 如果在进入此方法时设置了当前线程的中断状态，或者在等待时，线程被中断，那么在接到signal之前，它将继续等待。当最终从此方法返回时，仍然将设置其中断状态。 
		     * 实现注意事项 
		     * 假定调用此方法时，当前线程保持了与此 Condition 有关联的锁。
		     * 这取决于确定是否为这种情况以及不是时，如何对此作出响应的实现。通常，将抛出一个异常（比如 IllegalMonitorStateException）并且该实现必须对此进行记录。
		     */
			public final void awaitUninterruptibly() {
				Node node=addConditionWaiter();//新增加一个等待节点
				boolean interrupted=false;//用来判断线程是否中断过
				int savedState=fullyRelease(node);//完全释放锁资源,返回锁状态
				while(!isOnSyncQueued(node)){//如果新增的等待节点不再同步队列上，则park当前线程
					LockSupport.park(this);
					if(Thread.interrupted())//如果线程中断过
						interrupted=true;
				}
				if(acquiredQueued(node, savedState)||interrupted)//根据当前节点和锁状态来获取锁
					selfInterrupt();//将自身线程标记为interrupted
				
			}
            /**
             * 造成当前线程在接到信号、被中断或到达指定等待时间之前一直处于等待状态。 
             * 与此条件相关的锁以原子方式释放，并且出于线程调度的目的，将禁用当前线程，且在发生以下五种情况之一 以前，当前线程将一直处于休眠状态： 
             * 其他某个线程调用此 Condition 的 signal() 方法，并且碰巧将当前线程选为被唤醒的线程；
             * 或者其他某个线程调用此 Condition 的 signalAll() 方法；
             * 或者其他某个线程中断当前线程，且支持中断线程的挂起；
             * 或者已超过指定的等待时间；
             * 或者发生“虚假唤醒”。 
             * 在所有情况下，在此方法可以返回当前线程之前，都必须重新获取与此条件有关的锁。在线程返回时，可以保证 它保持此锁。 
             * 如果当前线程： 在进入此方法时已经设置了该线程的中断状态；或者在支持等待和中断线程挂起时，线程被中断， 则抛出 InterruptedException，并且清除当前线程的已中断状态。
             * 在第一种情况下，没有指定是否在释放锁之前发生中断测试。 
             * 在返回时，该方法返回了所剩毫微秒数的一个估计值，以等待所提供的 nanosTimeout 值的时间，如果超时，则返回一个小于等于 0 的值。可以用此值来确定在等待返回但某一等待条件仍不具备的情况下，是否要再次等待，以及再次等待的时间。此方法的典型用法采用以下形式： 
             * synchronized boolean aMethod(long timeout, TimeUnit unit) {
             *    long nanosTimeout = unit.toNanos(timeout);
             *    while (!conditionBeingWaitedFor) {
             *          if (nanosTimeout > 0)
             *          nanosTimeout = theCondition.awaitNanos(nanosTimeout);
             *         else
             *          return false;  }
             * // ...  }
             * 设计注意事项：此方法需要一个 nanosecond 参数，以避免在报告剩余时间时出现截断错误。
             * 在发生重新等待时，这种精度损失使得程序员难以确保总的等待时间不少于指定等待时间。 
             * 实现注意事项 
             * 假定调用此方法时，当前线程保持了与此 Condition 有关联的锁。这取决于确定是否为这种情况以及不是时，如何对此作出响应的实现。
             * 通常会抛出一个异常（比如 IllegalMonitorStateException）并且该实现必须对此进行记录。 
             * 与响应某个信号而返回的普通方法相比，或者与指示所使用的指定等待时间相比，实现可能更喜欢响应某个中断。
             * 在任意一种情况下，实现必须确保信号被重定向到另一个等待线程（如果有的话）。 
             */
			public long awaitNanos(long nanosTime)throws InterruptedException {
				if(Thread.interrupted())
					throw new InterruptedException();
				Node node=addConditionWaiter();//添加condition等待节点
				int savedState=fullyRelease(node);//释放锁资源，并返回锁状态
				int interruptMode=0;//定义一个中断模型
				long lastTime=System.nanoTime();//获取当前时间，用来计算花费时间
				while(!isOnSyncQueued(node)){
					if(nanosTime<=0l){
						transferAfterCancelledWait(node);//取消当前节点，并转移到同步队列中
						break;//跳出循环
					}
					LockSupport.parkNanos(this,nanosTime);//最多中断给定时间
					if((interruptMode=checkInterruptWhileWaiting(node))!=0){//如果等待过程中中断过，那么跳出循环
						break;
					}
					long nowTime=System.nanoTime();
					nanosTime-=nowTime-lastTime;
					lastTime=nowTime;
				}
				if(acquiredQueued(node, savedState)&&interruptMode!=THROW_IE)
					interruptMode=REINTERRUPT;
				if(node.nextWaiter!=null)//如果下一个nextWaiter不为空，那么需要清理取消等待的节点
					unlinkCanceledWaiters();
				if(interruptMode!=0)//如果中断模型不为0，说明中断过
					reportInterrupteAfterWait(interruptMode);
				return nanosTime-(System.nanoTime()-lastTime);
			}
			/*该模式意味从等待状态退出时需要重新中断*/
            private final static int REINTERRUPT=1;
            /*该模式意味从等待状态退出时需要抛出InterruptedException*/
            private final static int THROW_IE=-1;
			
            /**
			 * 成当前线程在接到信号、被中断或到达指定等待时间之前一直处于等待状态。
			 * 此方法在行为上等效于：awaitNanos(unit.toNanos(time)) > 0
			 * @return 如果在从此方法返回前检测到等待时间超时，则返回 false，否则返回 true 
			 */
			public boolean await(long time, TimeUnit unit)throws InterruptedException {
				if(unit==null)
					throw new NullPointerException();
				if(Thread.interrupted())
					throw new InterruptedException();
				Node node =addConditionWaiter();//增加condition新等待节点
				int savedState=fullyRelease(node);
				boolean timeout=false;//用来判断是否超时
				int interruptMode=0;
				long lastTime=System.nanoTime();
				long nanoTime=unit.toNanos(time);
				while(!isOnSyncQueued(node)){
					if(nanoTime<=0L){
						timeout=transferAfterCancelledWait(node);//取消等待节点，并将其加入到同步队列中，返回的boolean用来判断是否超时,true为没有超时，false超时了
						break;
					}
					if(nanoTime>spinForTimeOutThreshold)
						LockSupport.parkNanos(this, nanoTime);//等待
					if((interruptMode=checkInterruptWhileWaiting(node))!=0){//如果在等待过程中中断过，那么跳出循环
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
			 * 造成当前线程在接到信号、被中断或到达指定最后期限之前一直处于等待状态。 
			 *与此条件相关的锁以原子方式释放，并且出于线程调度的目的，将禁用当前线程，且在发生以下五种情况之一 以前，当前线程将一直处于休眠状态： 
			 *   其他某个线程调用此 Condition 的 signal() 方法，并且碰巧将当前线程选为被唤醒的线程；
			 *   或者其他某个线程调用此 Condition 的 signalAll() 方法；
			 *   或者其他某个线程中断当前线程，且支持中断线程的挂起；
			 *   或者指定的最后期限到了；
			 *   或者发生“虚假唤醒”。 
			 *在所有情况下，在此方法可以返回当前线程之前，都必须重新获取与此条件有关的锁。在线程返回时，可以保证 它保持此锁。 
			 *如果当前线程： 
			 *   在进入此方法时已经设置了该线程的中断状态；
			 *   或者在支持等待和中断线程挂起时，线程被中断，则抛出 InterruptedException，并且清除当前线程的已中断状态。
			 * 在第一种情况下，没有指定是否在释放锁之前发生中断测试。 返回值指示是否到达最后期限，使用方式如下： 
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
			 * 实现注意事项 
			 * 假定调用此方法时，当前线程保持了与此 Condition 有关联的锁。这取决于确定是否为这种情况以及不是时，如何对此作出响应的实现。
			 * 通常，将抛出一个异常（比如 IllegalMonitorStateException）并且该实现必须对此进行记录。 
			 * 与响应某个信号而返回的普通方法相比，或者与指示是否到达指定最终期限相比，实现可能更喜欢响应某个中断。
			 * 在任意一种情况下，实现必须确保信号被重定向到另一个等待线程（如果有的话）。 
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
			 * 将等待队列中等待时间最长的节点转移到同步队列中
			 */
			public void signal() {
			  if(!isHeldExclusively()){//如果不是独占锁,则抛出异常
				  throw new IllegalMonitorStateException();
			  }
			  Node first=firstWaiter;
			  if(first!=null)
				  doSignal(first);//
			}
            
			/**
			 * 循环将等待队列中的canceled节点或者空节点移除
			 * @param first
			 */
			private final void doSignal(Node first){
				do{
					//将condition等待队列第一个节点的nextWaiter设置为等待队列头结点，因为现在的头结点要转移到同步队列中去
					if((firstWaiter=first.nextWaiter)==null)//修改头结点，完成旧头结点的移除
						lastWaiter=null;
				}while(!transferForSignal(first)&&(first=firstWaiter)!=null);//将旧头结点加入到aqs同步队列中，参与锁的竞争
			}
			/**
			 * 循环移除所有condition等待队列节点
			 * first，队列中第一个不为null的节点
			 */
			private final void doSignalAll(Node first){
				lastWaiter=firstWaiter=null;//将等待节点都设置为null
				do{
					Node next=first.nextWaiter;
					first.nextWaiter=null;//help gc
					transferForSignal(first);
					first=next;
				}while(first!=null);
			}
				
			/**
			 * 将condition队列中的等待节点全部转移到同步队列中去
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
