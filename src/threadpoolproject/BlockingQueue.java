package threadpoolproject;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * 提供了两个附加操作的Queue,这两个附加操作分别是:(1)获取元素时需要等待队列不为空(2)保存元素时需要等待队列空间可用；
 * BlockingQueue的方法以四种形式出现，对于不能立即满足但可能在将来某一时刻满足的操作，这四种形式的处理方式不同:
 * 第一种是抛出异常，第二种是返回一个特殊值null或者false，第三种是在成功前，无限期阻塞线程，第四种是在给定的时间内阻塞线程，超过时限则放弃
 * (1)throws exception的方法:add(e),remove(),element()
 * (2)返回特殊值的方法:offer(e),poll(),peek()
 * (3)无限期阻塞的方法:put(),take()
 * (4)超时的方法:offer(e,time,unit),poll(time,unit)
 *   BlockingQueue不接受空元素，当试图利用方法add(),offer(),put()添加空元素时，实现类会抛出NULlPointerException.null用来当作标识poll()操作失败的警戒值。
 *   BlockingQueue是可以被容量限定的。在任意时刻，它都会有一个remaingCapacity，超出此容量，那么附加的元素就无法阻塞地put进来。没有内部容量限制的
 * BlockingQueue总是报告remainingCapacity的值为Integer.MAX_VALUE。
 *   BlockingQueue的实现类主要是为了生产者-消费者模型进行设计的，但是它也实现了Collection接口，因此利用remove从队列中删除任意元素是可行的。然而通常这种操作不会被执行，
 * 只有有计划地偶尔使用，比如在取消排队信息时。
 *   BlockingQueue的实现类都是线程安全的。所有的queue方法都使用内部锁或者其它并发方法来达到它们的操作原子性的目的。然而大量的Collection方法比如addAll(),retainAll()，containAll()
 *  removeAll()都没有原子性执行，除非在一个实现类中专门提出。因此举例来说，只在c中添加了一些元素之后，那么addAll(c)很可能会失败
 *    BlockingQueue 实质上不 支持使用任何一种“close”或“shutdown”操作来指示不再添加任何项。这种功能的需求和使用有依赖于实现的倾向。
 *  例如，一种常用的策略是：对于生产者，插入特殊的 end-of-stream 或 poison 对象，并根据使用者获取这些对象的时间来对它们进行解释。 
 *  内存一致性效果：将对象放入到BlockingQueue之前的线程操作happen-before 那些在另一个线程中从BlockingQueue获取或者删除元素之后的操作
 *  (简单点说就是，一个线程往BlockingQueue添加元素的操作happen-before另一个线程获取或者删除该BlockingQueue的操作)
 * @author heyw
 *
 * @param <E>
 */
public interface BlockingQueue<E> extends Queue<E> {
    /** 
     * 将元素插入到指定BlockingQueue中（如果立即可行并且不会违反容量限制）。如果成功添加则返回true，如果可用空间不足的throw new IllegalStateException.
     * 如果使用容量限制的队列，那么推荐使用offer()方法来插入元素
     */
	boolean add(E e) ;
	
	/**
	 * 将指定元素插入此队列中（如果立即可行且不会违反容量限制），成功时返回 true，如果当前没有可用的空间，则返回 false。
	 * 当使用有容量限制的队列时，此方法通常要优于 add(E)，后者可能无法插入元素，而只是抛出一个异常
	 */
	boolean offer(E e);
	
	/**
	 * 将指定元素插入此队列中，将等待可用的空间（如果有必要）。 
	 * @param e
	 * @throws InterruptedException 如果等待时发生中断则抛出该异常
	 */
	void put(E e) throws InterruptedException;
	
	/**
	 * 将指定的元素插入此队列中，在给定的等待时间到达之前等待可用空间(如果必要)
	 * @param e
	 * @param timeout 在放弃等待之前等待事件，unit为单位
	 * @param uint
	 * @return
	 * @throws InterruptedException 如果等待时发生中断
	 */
	boolean offer(E e,long timeout,TimeUnit uint)throws InterruptedException;
	
	/**
	 * 获取并移除队列头部的对象，在得到可用对象之前一直等待(如果必要地话)
	 * @return
	 * @throws InterruptedException
	 */
	E take()throws InterruptedException;
	
	/**
	 * 获取并移除队列头部对象，在得到可用对象之前等待指定时间
	 * @param timeout
	 * @param unit
	 * @return
	 * @throws InterruptedException
	 */
	E poll(long timeout,TimeUnit unit) throws InterruptedException;
    
	/**
	 * 返回在无阻塞的理想情况下(没有内存和资源限制)此队列能接受的附加元素数量，如果没有内部限制，则返回Integer.MAX_VALUE
	 * 注意，不能总是通过检查 remainingCapacity 来判断插入元素的尝试是否成功，因为可能出现这样的情况：其他线程即将插入或移除一个元素。
	 * @return
	 */
	int remainingCapacity();
	
	/**
	 * 从此队列中移除指定元素的单个实例（如果存在）。更确切地讲，如果此队列包含一个或多个满足 o.equals(e) 的元素 e，则移除该元素。
	 * 如果此队列包含指定元素（或者此队列由于调用而发生更改），则返回 true
	 * @param o,即将从队列中删除的元素，如果存在地话
	 * @throws NullPointerException 如果指定元素为null则返回该异常
	 */
	boolean remove(Object o);
	
	/**
	 * 如果队列中包含指定元素o，则返回true。更确切地说，当且仅当队列中至少有一个满足o.equals(e)的元素时才返回true
	 */
	boolean contains(Object o);
	
	/**
	 * 移除此队列中所有可用的元素，并将它们添加到给定 collection 中。此操作可能比反复轮询此队列更有效。
	 * 在试图向 collection c 中添加元素没有成功时，可能导致在抛出相关异常时，元素会同时在两个 collection 中出现，或者在其中一个 collection 中出现，也可能在两个 collection 中都不出现。
	 * 如果试图将一个队列放入自身队列中，则会导致 IllegalArgumentException 异常。此外，如果正在进行此操作时修改指定的 collection，则此操作行为将是不可预料的。 
	 * @param c
	 * @return  转移的元素数量
	 */
	int drainTo(Collection<? super E> c);
	
	/**最多从此队列中移除给定数量的可用元素，并将这些元素添加到给定 collection 中。在试图向 collection c 中添加元素没有成功时，
	 * 可能导致在抛出相关异常时，元素会同时在两个 collection 中出现，或者在其中一个 collection 中出现，也可能在两个 collection 中都不出现。
	 * 如果试图将一个队列放入自身队列中，则会导致 IllegalArgumentException 异常。此外，如果正在进行此操作时修改指定的 collection，则此操作行为是不确定的。
	 * 
	 * @param c
	 * @param maxnum 最大转移数量
	 * @return 转移的元素数量
	 */
	int drainTo(Collection<?super E>c ,int maxnum);
}
