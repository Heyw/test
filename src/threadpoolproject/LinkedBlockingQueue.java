package threadpoolproject;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


/**
 * 一个基于已链接节点的、范围任意的 blocking queue。此队列按 FIFO（先进先出）排序元素。队列的头部是在队列中时间最长的元素。队列的尾部 是在队列中时间最短的元素。
 * 新元素插入到队列的尾部，并且队列获取操作会获得位于队列头部的元素。链表队列的吞吐量通常要高于基于数组的队列，但是在大多数并发应用程序中，其可预知的性能要低。 
 * 可选的容量范围的构造方法参数可以作为防止队列过度扩展的一种方法。如果未指定容量参数，则容量等于 Integer.MAX_VALUE。
 * 除非插入节点会使队列超出容量，否则每次插入后会动态地创建链接节点。 
 * 此类及其迭代器实现 Collection 和 Iterator 接口的所有可选 方法。 此类是 Java Collections Framework 的成员。
 * @author heyw
 * @param <E>
 */
public class LinkedBlockingQueue<E>extends AbstractQueue<E> implements BlockingQueue<E>,java.io.Serializable{

	private static final long serialVersionUID = 1229311867736205222L;

	/*
	 * "two lock queue"算法的一种变体。putLock门只允许put(offer)方法进入，并且和等待的节点有相关联系。takeLock同样类似。
	 * putLock和takeLock都依赖的count属性被设置为AtomicInteger，是为了避免在大多数场合中对获取两种锁的需要。
	 * 同样，将puts对获取takeLock的需要最小程度化，cascading notifies被使用了。当一个put注意到它已经使能了至少一个take，那么它将通知taker。
	 * taker反过来也会通知其它，如果由于signals，更多items进入了。同样take也将通知puts。类似remove和iterators的操作将会获取两种锁。
	 *     如下是writers和readers之间的可见性:
	 *  无论何时一个对象进入队列，那么将会获取putLock同时count将会更新。一个后序的readers将会保证对要么是通过获取putLock要么是通过获取takeLock进入队列
	 *  的节点的可见性，然后通过n=count.get()，这将传递可见性给第一批n items。
	 *  为了实现弱一致的iterators，看上去我们需要保持所有节点对前任已经出队列节点GC-reachable。这将会导致下面两个问题：
	 *  允许流氓Iterator引起无限地内存滞留，如果有节点存活时是tenured的，那么将导致新老节点的跨代链接，在这种情况下，gc很难处理，从而造成了重复的大集合。
	 *  然而，来自出列的节点，只有没有被删除的节点才需要保持可达性，同时可达性没有必要成为GC理解的类型，我们将刚刚出列的节点链接到其自身。
	 *  这样自我关联意味着提升至head.next
	 */
	/**
	 * 链表节点类
	 * @author heyw
	 *
	 * @param <E>
	 */
	static class Node<E>{
		E item;
		Node<E> next;
		Node(E e){
			this.item=e;
		} 
	}
	/**容量界限，如果null则为Integer.maxValue*/
	private final int capacity;
	/**元素的个数*/
	private final AtomicInteger count=new AtomicInteger(0);
	/**链表头节点，不可变量，head.item=null
	 */
	private transient Node<E>head;
	/**链表尾节点，不可变量，last.next=null
	 */
	private transient Node<E>last;
	/**takeLock,获取锁, 通过take而加锁*/
	private final ReentrantLock takeLock=new ReentrantLock();
	/**等待takes的等待队列*/
	private final Condition notEmpty=takeLock.newCondition();
	/**putLock,插入锁，通过put加锁*/
	private final ReentrantLock putLock=new ReentrantLock();
	/**等待puts的等待队列*/
	private final Condition notFull=putLock.newCondition();
	
	/**
	 * 通知一个等待take。该方法只能在put、offer方法中调用
	 */
	private void signalNotEmpty(){
		final ReentrantLock lock=this.takeLock;
		lock.lock();
		try{
			notEmpty.signal();
		}finally{
			lock.unlock();
		}
	}
	/**
	 * 通知一个wait的put(往队列中添加元素)。该方法只能在take，poll方法中使用
	 */
	private void signalNotFull(){
		final ReentrantLock lock=this.takeLock;
		lock.lock();
		try{
			notFull.signal();
		}finally{
			lock.unlock();
		}
	}
	/**
	 * 创建一个无界的阻塞队列
	 */
	public LinkedBlockingQueue(){
		this(Integer.MAX_VALUE);
	}
	/**
	 * 根据指定值创建阻塞队列
	 * @param c
	 */
	public LinkedBlockingQueue(int c){
		if(c<0)throw new IllegalArgumentException();
		this.capacity=c;
		last=head=new Node<E>(null);
	}
	
	/**
	 * 创建一个初始容量为Integer.MAX_VALUE并且包含指定集合大小元素的阻塞队列
	 * @param c
	 */
	public LinkedBlockingQueue(Collection<? extends E> c){
		this.capacity=Integer.MAX_VALUE;
		final ReentrantLock lock=this.putLock;
		lock.lock();//没有竞态，只是为了可见性的必要
		try{
			int n=0;
			for(E e:c){
				if(e==null) throw new NullPointerException();
				if(n>=capacity) throw new IllegalStateException("queue is full");
				enqueue(new Node<E>(e));
				n++;
			}
			count.set(n);
		}finally{
			lock.unlock();
		}
	}
	
	/**
	 * 将node链接到list的尾部
	 * @param node
	 */
	private void enqueue(Node<E> node){
		last=last.next=node;
	}
	
	/**
	 * 移除list的头结点，并返回下一个节点的item，因为head的item永远为null
	 * 然后将head指向下一个节点
	 * @return
	 */
	private E dequeue(){
//		Node<E> p=head.next;
//		Node<E> next=p.next;
//		head.next=next;
//		E item=p.item;
//		p.item=null;
//		p.next=p;//help gc p与p.next形成一个环，便于回收
//		return item;
		Node<E>h=head;
		Node<E>first=head.next;
		h.next=h;//help gc p与p.next形成一个环，便于回收
		E item=first.item;
		first.item=null;
		head=first;
		return item;
	}
	
	/**
	 * 利用节点q的前任节点来删除当前节点q
	 * @param pred
	 * @param q
	 */
	private void unlink(Node<E> pred,Node<E> p){
		//调用该方法必须在is FullyLock()情况下
		//p.next不会被改变，允许遍历p的iterators维持弱一致性
		//将pred的next指向p.next，这样就将p节点孤立了
		//判断p是否是last，如果是就将last指向pred
		pred.next=p.next;
		p.item=null;//利于gc
		if(last==p)
			last=pred;
      if(count.get()==capacity)
    	  signalNotFull();
	}
	
	/**
	 * 从此队列移除指定元素的单个实例（如果存在）。更确切地讲，如果此队列包含一个或多个满足 o.equals(e) 的元素 e，
	 *  则移除一个这样的元素。如果此队列包含指定元素，则返回 true（或者此队列由于调用而发生更改，则返回 true）。 
	 */
   public boolean remove(Object o){
	   if(o==null) return false;
	   fullyLock();
	   try{
			  for(Node<E>pred=head,p=head.next;p!=null;pred=p,p=p.next){
				  if(o.equals(p.item)){
					  unlink(pred, p);
					  return true;
				  }
			  }
			  return false;
	   }finally{
		   unfullyLock();
	   }
   }
	private void fullyLock(){
		this.takeLock.lock();
		this.putLock.lock();
	}
	private void unfullyLock(){
		this.takeLock.unlock();
		this.putLock.unlock();
	}
	/**
	 * 将指定的元素插入队列尾部，如果立即可行，且不会超出容量。插入成功返回true，否则返回false
	 * 当使用容量限制的队列时，该方法优于add方法，因为add方法插入元素失败，只会抛出一个异常
	 * 与put的差别在于，offer如果发现容量已满，就立即退出并返回一个false，而put会阻塞
	 */
	public boolean offer(E e) {
		if(e==null)throw new NullPointerException();
		int c=-1;
		final AtomicInteger count=this.count;
		if(count.get()==capacity)//如果容量已满，退出返回false
			return false;
		final ReentrantLock putLock=this.putLock;
		Node<E>node =new Node<>(e);
		putLock.lock();
		try{
			if(count.get()<capacity){
				enqueue(node);
				c=count.getAndIncrement();
				if(c+1<capacity){
					notFull.signal();//通知其他put方法
				}
			}
		}finally{
			putLock.unlock();
		}
		if(c==0)
			signalNotEmpty();
		return c>=0;
	}

	/**
	 * 获取并移除队头元素，无需等待
	 * @return
	 */
	public E poll() {
	   if(count.get()==0) return null;
	   final ReentrantLock takelock=this.takeLock;
	   final AtomicInteger count=this.count;
	   E x=null;
	   int c=-1;
	   takelock.lock();
	   try{
		   if(count.get()>0){//这一步必须要判断
		     x=dequeue();
		     c=count.getAndDecrement();
		     if(c>1) notEmpty.signal();
		   }
	   }finally{
		   takelock.unlock();
	   }
	   if(c==capacity){
		   signalNotFull();
	   }
		return x;
	}

	/**
	 * 获取队列头部元素，但不移除
	 * @return
	 */
	public E peek() {
        if(count.get()==0) return null;
        E x;
        final ReentrantLock takelock=this.takeLock;
        takelock.lock();//需要加锁
        try{
        	Node<E>first=head.next;
        	x=first.item;
        	if(x==null)return null;
        	else return x;
        }finally{
        	takelock.unlock();
        }
	}

	/**
	 * 返回该队列的iterator
	 */
	public Iterator<E> iterator() {
		return new Itr();
	}

   /**
    * 返回队列中元素的大小
    * @return
    */
	public int size() {
		return count.get();
	}

   /**
    * 在队列尾部插入一个元素，如果必要，等待空间变得可用
    */
	public void put(E e) throws InterruptedException {
		if(e==null)throw new NullPointerException();
	     Node<E> node=new Node<>(e);
	     //在put，offer方法中会竞争设置这个变量，初始设置为负数是为了防止该变量没有被设置而产生的失败
	     int c=-1;
	     final AtomicInteger count=this.count;
	     final ReentrantLock putlock=this.putLock;
	     putlock.lockInterruptibly();//如果当前线程没有被中断则获取锁
	     try{
	    	 while(count.get()==capacity){//如果容量已满，则等待可用
	    		 notFull.await();
	    	 }
	    	 //注意到count是在wait guard中使用的，即使没有被锁保护。这样可行，是因为count只能在这点才能减少(其他puts都被关在门外)
	    	 //如果capacity发生了变化，我们和其他一些waiting put将会被唤醒。在其他wait guard中的count的使用者也是一样的
	    	 enqueue(node);
	    	 c=count.getAndIncrement();
	    	 if(c+1<capacity)
	    		 notFull.signal();
	     }finally{
	    	 putlock.unlock();
	     }
		 if(c==0){
			 signalNotEmpty();
		 }
	}


	public boolean offer(E e, long timeout, TimeUnit unit)
			throws InterruptedException {
        if(e==null)throw new NullPointerException();
        timeout=unit.toNanos(timeout);
        final ReentrantLock putLock=this.putLock;
       final AtomicInteger count=this.count;
       int c=-1;
       putLock.lock();
       try{
    	   while(count.get()==capacity){
        	  if(timeout<=0)return false;
        	   timeout=notFull.awaitNanos(timeout);//等待timeout，等待结束返回剩余时间
    	   }
    		   Node<E>node=new Node<>(e);
    		   enqueue(node);
    		   c=count.getAndIncrement();
    		   if(c+1<capacity){
    			   notFull.signal();
    		   }
       }finally{
    	   putLock.unlock();
       }
       if(c==0)
    	   signalNotEmpty();
		return true;
	}

	 public boolean contains(Object o){
		 if(o==null) return false;
		 fullyLock();
		 try{
			 for(Node<E>p=head.next;p!=null;p=p.next){
				 if(o.equals(p.item))
					 return true;
			 }
			 return false;
		 }finally{
			 unfullyLock();
		 }
	 }
	/**
	 * 
	 */
	public E take() throws InterruptedException {
		final ReentrantLock takelock=this.takeLock;
		final AtomicInteger count=this.count;
		E item=null;
		int c=-1;
		takelock.lockInterruptibly();
		try{
			while(count.get()==0){
				notEmpty.await();//队列为空，进入notEmpty等待
			}
			 item=dequeue();
			c=count.getAndDecrement();
			if(c-1>=0){//如果还有元素，那么notEmpty通知等待的take
				notEmpty.signal();
			}
		}finally{
			takelock.unlock();
		}
		if(c==capacity){
			signalNotFull();
		}
		return item;
	}


	public E poll(long timeout, TimeUnit unit) throws InterruptedException {
		E x;
		final ReentrantLock takelock=this.takeLock;
		final AtomicInteger count = this.count;
		long nanos=unit.toNanos(timeout);
		int c=-1;
		takelock.lockInterruptibly();
		try{
			while(count.get()==0){
				if(nanos<=0) return null;
				notEmpty.awaitNanos(nanos);
			}
			x=dequeue();
			c=count.getAndDecrement();
			if(c-1>0){
				notEmpty.signal();
			}
		}finally{
			takelock.unlock();
		}
		if(c==capacity){
			signalNotFull();
		}
		return x;
	}

	/**
	 * 返回理想情况下（没有内存和资源约束）此队列可接受并且不会被阻塞的附加元素数量。该数量总是等于此队列的初始容量减去队列的当前 size。
	 *  注意，不能 总是通过检查 remainingCapacity 来断定试图插入一个元素是否成功，因为可能是另一个线程将插入或移除某个元素
	 */
	public int remainingCapacity() {
		return capacity-count.get();
	}

	@Override
	public int drainTo(Collection<? super E> c) {
		return drainTo(c,count.get());
	}

	/**最多从此队列中移除给定数量的可用元素，并将这些元素添加到给定 collection 中。
	 * 在试图向 collection c 中添加元素没有成功时，可能导致在抛出相关异常时，元素会同时在两个 collection 中出现，
	 * 或者在其中一个 collection 中出现，也可能在两个 collection 中都不出现。如果试图将一个队列放入自身队列中，则会导致 IllegalArgumentException 异常。
	 * 此外，如果正在进行此操作时修改指定的 collection，则此操作行为是不确定的。 
	 */
		public int drainTo(Collection<? super E> c, int maxnum) {
		if(c==null) throw new NullPointerException();//c为空，抛出NullPointerException
		if(c==this) throw new IllegalArgumentException();
		final ReentrantLock takeLock=this.takeLock;
		AtomicInteger count=this.count;
		boolean signalNotEmpty=false;
        takeLock.lock();
        try{
        	int n=Math.min(maxnum, count.get());//获取较小值作为添加元素的数目
        	Node<E>h=head;
        	int i=0;
        	try{//此处利用try--finally为了处理c.add()其他类型元素时抛出的异常，如果不处理可能导致head节点为空
        		while(i<n){
        			Node<E>p=h.next;//获取下一个节点
        			c.add(p.item);//添加元素
        			p.item=null;//help gc
        			h.next=h;//删除h所指向节点
        			h=p;//将h指向下一个节点
        			i++;
        		}
        		return n;
        	}finally{
        		if(i>0){
        			head=h;//将h节点指向头结点，h.item必为null
        			signalNotEmpty=(count.getAndAdd(-i))==capacity;//如果count减去i之前为capacity，那么队列中仍然有数，就调用signalNotEmpty
        		}
        	}
        }finally{
        	takeLock.unlock();
        	if(signalNotEmpty)
        		signalNotEmpty();
        }
	}
	/**
	 * 返回按适当顺序包含此队列中所有元素的数组。  由于此队列不维护对返回数组的任何引用，因而它是“安全的”。
	 * （换句话说，此方法必须分配一个新数组）。因此，调用者可以随意修改返回的数组。 
	 * 此方法充当基于数组的 API 与基于 collection 的 API 之间的桥梁。
	 */
	public Object[] toArray(){
		int size=count.get();
	    fullyLock();
	    try{
	    	Object[] objs=new Object[size];
	    	int k=0;
	    	for(Node<E>p=head.next;p!=null;p=p.next){
	    			objs[k++]=p.item;
	    	}
	    	return objs;
	    }finally{
	    	unfullyLock();
	    }
	}
	/**
	 * 返回按适当顺序包含此队列中所有元素的数组；返回数组的运行时类型是指定数组的运行时类型。
	 * 如果指定数组能容纳该队列，则在其中返回它。否则，将分配一个具有指定数组的运行时类型和此队列大小的新数组。 
	 * 如果指定的数组能容纳队列，并有剩余的空间（即数组的元素比队列的多），那么将数组中紧接队列尾部的元素设置为 null。 
	 * 像 toArray() 方法一样，此方法充当基于数组的 API 与基于 collection 的 API 之间的桥梁。
	 * 更进一步说，此方法允许对输出数组的运行时类型进行精确控制，在某些情况下，可以用来节省分配开销。 
	 * 假定 x 是只包含字符串的一个已知队列。以下代码用来将该队列转储到一个新分配的 String 数组： 
	 *       String[] y = x.toArray(new String[0]);注意，toArray(new Object[0]) 和 toArray() 在功能上是相同的。
	 */
	@SuppressWarnings("unchecked")
	public <T>T[] toArray(T[]a){
		fullyLock();
		try{
			int size=count.get();
			a=(T[])java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
			int k=0;
			for(Node<E>p=head.next;p!=null;p=p.next){
					a[k++]=(T)p.item;
			}
			if(a.length>k)
				a[k]=null;
			return a;
		}finally{
			unfullyLock();
		}
	}
	/**
	 * private 修饰内部类，那么只能允许外部类访问该内部类，并且只能允许在外部类中使用
	 * @author heyw
	 *
	 */
    private class Itr implements Iterator<E>{
    	 /*
    	  * 基本的弱一致iterator,在任何时刻都有可以交接的下一个item，以便于在take中失去竞争时仍然可以将其返回
    	  */
         private Node<E> current;//表示下一次next要返回的节点
         private  E currentItem;
         private Node<E> lastNode;//lastNode用来表示当前next()抛出的节点
         
          Itr(){
        	  fullyLock();
        	  try{
        		  current=head.next;//current一开始指向head的next
        		  if(current!=null)
             	   currentItem=current.item;
        	  }finally{
        		  unfullyLock();
        	  }
        	
         }
	
		public boolean hasNext() {
			return current!=null;
		}
        /**
         * 寻找node的下一个live successor
         * live sucessor 条件：item不为null或者s为null
         * 注意如果s==node说明该node已经孤立了，那么返回head
         * @param node
         * @return
         */
		private Node<E> nextNode(Node<E>node){
			for(;;){
				Node<E>s=node.next;
				if(s==node)
					return head.next;//如果node已经离开队列，那么返回head.next，head.next保存第一个元素
				if(s==null || s.item!=null){//s==null 说明已经没有live successor，s.item！=null说明这已经是live successor，这两种情况都是可以返回的
					return s;
				}
				node=s;
			}
		}
       /**
        * 返回当前值时，需要为current寻找下一个节点，这样current始终可以交接node.item
        */
		public E next() {
			fullyLock();
			try{
				if(current==null) throw new IllegalStateException();//如果current为null，则抛出非法状态异常，因为hasNext通过才能next
				E x=currentItem;
				lastNode=current;
				current=nextNode(current);
				currentItem=current==null? null:current.item;
				return x;
			}finally{
				unfullyLock();
			}
		}

		/**
		 * 从head开始遍历找到lastNode的pred，然后unlink它们
		 */
		public void remove() {
			if(lastNode==null) throw new IllegalStateException();//非法状态异常 IllegalStateException
           Node<E> rmvNode=lastNode;
           fullyLock();
           try{
        	   lastNode=null;//help gc，在清理完某个节点时，一定要判断某些引用是否还指向它，那么就要设置为null
        	   for(Node<E>pred=head,p=head.next;p!=null;pred=p,p=p.next){
        		   if(p==rmvNode){
        			   unlink(pred, p);
        			   break;
        			   }
        	   }
           }finally{
        	   unfullyLock();
           }
		}
    }
}
