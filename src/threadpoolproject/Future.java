package threadpoolproject;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Future代表异步计算的结果。提供了方法用来检查计算是否已经完成，以便等待计算完成，并获取计算结果。
 * 当计算完成时，只能通过方法get()来获取计算的结果，如有必要在计算完成前(if necessary until it is ready)可以阻塞该方法。
 * 取消操作是通过方法cancell()来执行的(performed)。提供了其他方法用来检查计算是否正常完成或者是否已经取消。
 * 如果为了追求可取消性并且不想提供可用的结果，可以将类型声明为Future<? >,并且将returned null来当作潜在任务返回。
 * FutureTask实现了Future的同时也实现了Runnable,因此FutureTask可以被Executor执行。
 * 内存一致性：异步计算采取的操作 happen-before操作跟在另一个线程中相关操作Future.get()之后
 * @author heyw
 *
 */
public interface Future<V> {
	/**
	 * 尝试取消任务的执行。如果任务已经完成，或者已经取消了，或者因为其它一些原因不能被取消，那么这次尝试将会失败返回null；
	 * 如果取消成功了并且当调用cancel()方法时该任务还没开始，那么该任务将永远不会执行。
	 * 如果该任务已经运行了，那么根据参数mayInterruptIfRunning判断是否应该以尝试停止任务的方式来中断执行任务的线程。
	 * 如果该方法返回后，那么后续调用isDone()方法将始终返回true.。如果该方法返回true,那么后续调用方法isCancelled()也将返回true。
	 * @param mayInterruptIfRunning 如果为true，那么尝试中断执行任务的线程，否则允许该现成运行完成。
	 * @return false 说明该任务无法取消，通常是因为该任务已经完成了。
	 */
     boolean cancel(boolean mayInterruptIfRunning);
     /**
      * 如果在任务正常完成前就被取消了，那么返回true
      * @return
      */
     boolean isCancelled();
     /**
      * 如果计算完成，将返回true。计算的完成可能是因为终止，异常或者取消而完成，这些情况调用isDone()方法都将返回true。
      * @return
      */
     boolean isDone();
     /**
      * 如果必要时等待计算的完成，并获取计算结果
      * @return 完成计算的结果
      * @throws InterruptedException 如果当前线程threw an new InterruptedException
      * @throws ExecutionException 如果当前线程在等待时被中断了
      */
     V get() throws InterruptedException,ExecutionException;
     /**
      *如果必要等待计算的完成，但最多等待给定的timeout时间，然后如果可行的化，获取结果
      * @param timeout 等待计算完成的时间限制
      * @param unit   时间单位
      * @return
      */
     V get(long timeout,TimeUnit unit) throws InterruptedException,ExecutionException,TimeoutException;
}
