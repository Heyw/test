package threadpoolproject;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * 一种ExecutorService,可安排在给定延迟后运行或者定期执行的指令。
 * 
 * schedule方法可以使用各种延迟来创建任务，并返回可以取消或者检查执行的任务对象。scheduleAtFixedRate()或者scheduleWithFixedDelay()
 * 方法可以创建并执行在取消前定期运行的任务。
 * 
 * 用 Executor.execute(java.lang.Runnable) 和 ExecutorService 的 submit 方法所提交的命令，通过所请求的 0 延迟进行安排。
 * schedule 方法中允许出现 0 和负数延迟（但不是周期），并将这些视为一种立即执行的请求。
 * 
 * 所有的 schedule 方法都接受相对 延迟和周期作为参数，而不是绝对的时间或日期。将以 Date 所表示的绝对时间转换成要求的形式很容易。
 * 例如，要安排在某个以后的 Date 运行，可以使用：schedule(task, date.getTime() - System.currentTimeMillis(), TimeUnit.MILLISECONDS)。
 * 但是要注意，由于网络时间同步协议、时钟漂移或其他因素的存在，因此相对延迟的期满日期不必与启用任务的当前 Date 相符。
 *  Executors 类为此包中所提供的 ScheduledExecutorService 实现提供了便捷的工厂方法。
 * @author heyw
 *
 */
public interface ScheduledExecutorService extends ExecutorService{
	/**
	 * 创建并执行给定延迟后启用的(Enabled)一次性操作
	 * @param command
	 * @param delay
	 * @param unit
	 * @return
	 */
    public ScheduledFuture<?> schedule(Runnable command,long delay,TimeUnit unit);
  
    /**
    * 创建并执行给定延迟后启用的ScheduledFuture
    * @param callable
    * @param delay
    * @param unit
    * @return
    */
    public <V>ScheduledFuture<V> schedule(Callable<V>callable,long delay,TimeUnit unit);
    
    /**
     * 创建并执行在给定初始延迟(initDelay)后初次启用该任务,随后将周期性的执行该任务
     * 也就是将在 initialDelay 后开始执行，然后在 initialDelay+period 后执行，接着在 initialDelay + 2 * period 后执行，依此类推。
     * 如果任务的任何一个执行遇到异常，则后续执行都会被取消。否则，只能通过执行程序的取消或终止方法来终止该任务。
     * 如果此任务的任何一个执行要花费比其周期更长的时间，则将推迟后续执行，但不会同时执行。 
     * @param command
     * @param initDelay
     * @param period
     * @param unit
     * @return
     */
    public ScheduledFuture<?>scheduleAtFixedRate(Runnable command,long initDelay,long period,TimeUnit unit);
    
    /**
     * 创建并执行一个在给定初始延迟后首次启用的定期操作，随后，在每一次执行终止和下一次执行开始之间都存在给定的延迟。
     * 如果任务的任一执行遇到异常，就会取消后续执行。否则，只能通过执行程序的取消或终止方法来终止该任务。 
     * @param commmand
     * @param initDelay
     * @param dealy
     * @param unit
     * @return
     */
    public ScheduledFuture<?>scheduleWithFixedDelay(Runnable commmand,long initDelay,long dealy,TimeUnit unit);
}
