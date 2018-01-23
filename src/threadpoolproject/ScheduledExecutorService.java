package threadpoolproject;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * һ��ExecutorService,�ɰ����ڸ����ӳٺ����л��߶���ִ�е�ָ�
 * 
 * schedule��������ʹ�ø����ӳ����������񣬲����ؿ���ȡ�����߼��ִ�е��������scheduleAtFixedRate()����scheduleWithFixedDelay()
 * �������Դ�����ִ����ȡ��ǰ�������е�����
 * 
 * �� Executor.execute(java.lang.Runnable) �� ExecutorService �� submit �������ύ�����ͨ��������� 0 �ӳٽ��а��š�
 * schedule ������������� 0 �͸����ӳ٣����������ڣ���������Щ��Ϊһ������ִ�е�����
 * 
 * ���е� schedule ������������� �ӳٺ�������Ϊ�����������Ǿ��Ե�ʱ������ڡ����� Date ����ʾ�ľ���ʱ��ת����Ҫ�����ʽ�����ס�
 * ���磬Ҫ������ĳ���Ժ�� Date ���У�����ʹ�ã�schedule(task, date.getTime() - System.currentTimeMillis(), TimeUnit.MILLISECONDS)��
 * ����Ҫע�⣬��������ʱ��ͬ��Э�顢ʱ��Ư�ƻ��������صĴ��ڣ��������ӳٵ��������ڲ�������������ĵ�ǰ Date �����
 *  Executors ��Ϊ�˰������ṩ�� ScheduledExecutorService ʵ���ṩ�˱�ݵĹ���������
 * @author heyw
 *
 */
public interface ScheduledExecutorService extends ExecutorService{
	/**
	 * ������ִ�и����ӳٺ����õ�(Enabled)һ���Բ���
	 * @param command
	 * @param delay
	 * @param unit
	 * @return
	 */
    public ScheduledFuture<?> schedule(Runnable command,long delay,TimeUnit unit);
  
    /**
    * ������ִ�и����ӳٺ����õ�ScheduledFuture
    * @param callable
    * @param delay
    * @param unit
    * @return
    */
    public <V>ScheduledFuture<V> schedule(Callable<V>callable,long delay,TimeUnit unit);
    
    /**
     * ������ִ���ڸ�����ʼ�ӳ�(initDelay)��������ø�����,��������Ե�ִ�и�����
     * Ҳ���ǽ��� initialDelay ��ʼִ�У�Ȼ���� initialDelay+period ��ִ�У������� initialDelay + 2 * period ��ִ�У��������ơ�
     * ���������κ�һ��ִ�������쳣�������ִ�ж��ᱻȡ��������ֻ��ͨ��ִ�г����ȡ������ֹ��������ֹ������
     * �����������κ�һ��ִ��Ҫ���ѱ������ڸ�����ʱ�䣬���Ƴٺ���ִ�У�������ͬʱִ�С� 
     * @param command
     * @param initDelay
     * @param period
     * @param unit
     * @return
     */
    public ScheduledFuture<?>scheduleAtFixedRate(Runnable command,long initDelay,long period,TimeUnit unit);
    
    /**
     * ������ִ��һ���ڸ�����ʼ�ӳٺ��״����õĶ��ڲ����������ÿһ��ִ����ֹ����һ��ִ�п�ʼ֮�䶼���ڸ������ӳ١�
     * ����������һִ�������쳣���ͻ�ȡ������ִ�С�����ֻ��ͨ��ִ�г����ȡ������ֹ��������ֹ������ 
     * @param commmand
     * @param initDelay
     * @param dealy
     * @param unit
     * @return
     */
    public ScheduledFuture<?>scheduleWithFixedDelay(Runnable commmand,long initDelay,long dealy,TimeUnit unit);
}
