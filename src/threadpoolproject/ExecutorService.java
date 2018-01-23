package threadpoolproject;


import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Executor 提供了管理终止的方法，以及可为跟踪一个或多个异步任务执行状况而生成 Future 的方法。
 * 
 * 可以关闭 ExecutorService，这将导致其拒绝新任务。提供两个方法来关闭 ExecutorService。shutdown()和shutdownNow()
 * shutdown() 方法在终止前允许执行以前提交的任务，而 shutdownNow() 方法阻止等待任务启动并试图停止当前正在执行的任务。
 * 在终止时，执行程序没有任务在执行，也没有任务在等待执行，并且无法提交新任务。应该关闭未使用的 ExecutorService 以允许回收其资源。
 * 
 * 通过创建并返回一个可用于取消执行和/或等待完成的 Future，方法 submit 扩展了基本方法 Executor.execute(java.lang.Runnable)。
 * 方法 invokeAny 和 invokeAll 是批量执行的最常用形式，它们执行任务 collection，
 * 然后等待至少一个，或全部任务完成（可使用 ExecutorCompletionService 类来编写这些方法的自定义变体）。 
 * 
 * 内存一致性效果：线程中向 ExecutorService 提交 Runnable 或 Callable 任务之前的操作 happen-before 由该任务所提取的所有操作，
 * 后者依次 happen-before 通过 Future.get() 获取的结果。
 * @author heyw
 *
 */
public interface ExecutorService extends Executor {
    /**
     * 提交一个Runnable 任务用来执行，同时会返回一个代表该task的Future
     * Future的方法{@code get}将会返回{@code null}，如果该任务成功完成了
     * @param r
     * @return
     */
	Future<?> submit(Runnable r);
	/**
	 * 提交一个Runnable 任务用来执行，同时会返回一个代表该task的FutureTask
	 * Future的方法{@code get}将会返回指定的result,如果该任务成功完成了
	 * @param r
	 * @param result
	 * @return
	 */
     <T> Future<T> submit(Runnable r,T result);
     
     /**
      * 提交一个返回值的任务用于执行，返回一个表示任务的未决结果的 Future。该 Future 的 get 方法在成功完成时将会返回该任务的结果。
      * 如果想立即阻塞任务的等待，则可以使用 result = exec.submit(aCallable).get()形式的构造。
      * 注：Executors 类包括了一组方法，可以转换某些其他常见的类似于闭包的对象，例如，将 PrivilegedAction 转换为 Callable 形式，这样就可以提交它们了。
      * @param task
      * @return
      */
     <T> Future<T> submit(Callable<T> task);
     
     /**
      * 执行给定的任务，当所有任务完成时，返回持有任务状态和结果的 Future 列表。返回列表的所有元素的 Future.isDone() 为 true。
      * 注意，可以正常地或通过抛出异常来终止已完成 任务。如果正在进行此操作时修改了给定的 collection，则此方法的结果是不确定的。 
      * @param tasks
      * @return
      * @throws InterruptedException
      */
//     <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException;
     
    /**
     * 启动一次顺序关闭。执行以前提交的task，拒绝新提交的任务。如果ExecutorService已经实际关闭，则调用没有其它效果。
     */
     void shutDown();
     
     /**
      * 试图停止所有正在执行的活动任务，暂停处理正在等待的任务，并返回等待执行的任务列表。
      * 无法保证能够停止正在处理的活动执行任务，但是会尽力尝试。例如，通过 Thread.interrupt() 来取消典型的实现，所以任何任务无法响应中断都可能永远无法终止。
      * @return
      */
     List<Runnable> shutDownNow();
     
     /**
      * 如果此执行程序已关闭，则返回 true.
      * @return
      */
     boolean isShutDown();
     
     /**
      * 如果关闭后所有任务都已完成，则返回 true。注意，除非首先调用 shutdown 或 shutdownNow，否则 isTerminated 永不为 true。
      * @return
      */
     boolean isTerminated();
     
     /**
      * 请求关闭、发生超时或者当前线程中断，无论哪一个首先发生之后，都将导致阻塞，直到所有任务完成执行。
      * @param timeout
      * @param unit
      * @return
      * @throws InterruptedException
      */
     boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;
     
     /**
      * 执行给定的任务，当所有任务完成或超时期满时（无论哪个首先发生），返回保持任务状态和结果的 Future 列表。返回列表的所有元素的 Future.isDone() 为 true。
      * 一旦返回后，即取消尚未完成的任务。注意，可以正常地或通过抛出异常来终止已完成 任务。如果此操作正在进行时修改了给定的 collection，则此方法的结果是不确定的。 
      * @param tasks
      * @param timeout
      * @param unit
      * @return
      * @throws InterruptedException
      */
//     <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)throws InterruptedException;
     
     /**
      * 执行给定的任务，如果某个任务已成功完成（也就是未抛出异常），则返回其结果。
      * 一旦正常或异常返回后，则取消尚未完成的任务。如果此操作正在进行时修改了给定的 collection，则此方法的结果是不确定的。 
      * @param tasks
      * @return
      * @throws InterruptedException
      * @throws ExecutionException
      */
//     <T> T invokeAny(Collection<? extends Callable<T>> tasks)throws InterruptedException, ExecutionException;
     
     /**
      * 执行给定的任务，如果在给定的超时期满前某个任务已成功完成（也就是未抛出异常），则返回其结果。
      * 一旦正常或异常返回后，则取消尚未完成的任务。如果此操作正在进行时修改了给定的 collection，则此方法的结果是不确定的。 
      * @param tasks
      * @param timeout
      * @param unit
      * @return
      * @throws InterruptedException
      * @throws ExecutionException
      * @throws TimeoutException
      */
//     <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)throws InterruptedException, ExecutionException, TimeoutException;
}
