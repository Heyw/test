package threadpoolproject;


import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Executor �ṩ�˹�����ֹ�ķ������Լ���Ϊ����һ�������첽����ִ��״�������� Future �ķ�����
 * 
 * ���Թر� ExecutorService���⽫������ܾ��������ṩ�����������ر� ExecutorService��shutdown()��shutdownNow()
 * shutdown() ��������ֹǰ����ִ����ǰ�ύ�����񣬶� shutdownNow() ������ֹ�ȴ�������������ͼֹͣ��ǰ����ִ�е�����
 * ����ֹʱ��ִ�г���û��������ִ�У�Ҳû�������ڵȴ�ִ�У������޷��ύ������Ӧ�ùر�δʹ�õ� ExecutorService �������������Դ��
 * 
 * ͨ������������һ��������ȡ��ִ�к�/��ȴ���ɵ� Future������ submit ��չ�˻������� Executor.execute(java.lang.Runnable)��
 * ���� invokeAny �� invokeAll ������ִ�е������ʽ������ִ������ collection��
 * Ȼ��ȴ�����һ������ȫ��������ɣ���ʹ�� ExecutorCompletionService ������д��Щ�������Զ�����壩�� 
 * 
 * �ڴ�һ����Ч�����߳����� ExecutorService �ύ Runnable �� Callable ����֮ǰ�Ĳ��� happen-before �ɸ���������ȡ�����в�����
 * �������� happen-before ͨ�� Future.get() ��ȡ�Ľ����
 * @author heyw
 *
 */
public interface ExecutorService extends Executor {
    /**
     * �ύһ��Runnable ��������ִ�У�ͬʱ�᷵��һ�������task��Future
     * Future�ķ���{@code get}���᷵��{@code null}�����������ɹ������
     * @param r
     * @return
     */
	Future<?> submit(Runnable r);
	/**
	 * �ύһ��Runnable ��������ִ�У�ͬʱ�᷵��һ�������task��FutureTask
	 * Future�ķ���{@code get}���᷵��ָ����result,���������ɹ������
	 * @param r
	 * @param result
	 * @return
	 */
     <T> Future<T> submit(Runnable r,T result);
     
     /**
      * �ύһ������ֵ����������ִ�У�����һ����ʾ�����δ������� Future���� Future �� get �����ڳɹ����ʱ���᷵�ظ�����Ľ����
      * �����������������ĵȴ��������ʹ�� result = exec.submit(aCallable).get()��ʽ�Ĺ��졣
      * ע��Executors �������һ�鷽��������ת��ĳЩ���������������ڱհ��Ķ������磬�� PrivilegedAction ת��Ϊ Callable ��ʽ�������Ϳ����ύ�����ˡ�
      * @param task
      * @return
      */
     <T> Future<T> submit(Callable<T> task);
     
     /**
      * ִ�и��������񣬵������������ʱ�����س�������״̬�ͽ���� Future �б������б������Ԫ�ص� Future.isDone() Ϊ true��
      * ע�⣬���������ػ�ͨ���׳��쳣����ֹ����� ����������ڽ��д˲���ʱ�޸��˸����� collection����˷����Ľ���ǲ�ȷ���ġ� 
      * @param tasks
      * @return
      * @throws InterruptedException
      */
//     <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException;
     
    /**
     * ����һ��˳��رա�ִ����ǰ�ύ��task���ܾ����ύ���������ExecutorService�Ѿ�ʵ�ʹرգ������û������Ч����
     */
     void shutDown();
     
     /**
      * ��ͼֹͣ��������ִ�еĻ������ͣ�������ڵȴ������񣬲����صȴ�ִ�е������б�
      * �޷���֤�ܹ�ֹͣ���ڴ���Ļִ�����񣬵��ǻᾡ�����ԡ����磬ͨ�� Thread.interrupt() ��ȡ�����͵�ʵ�֣������κ������޷���Ӧ�ж϶�������Զ�޷���ֹ��
      * @return
      */
     List<Runnable> shutDownNow();
     
     /**
      * �����ִ�г����ѹرգ��򷵻� true.
      * @return
      */
     boolean isShutDown();
     
     /**
      * ����رպ�������������ɣ��򷵻� true��ע�⣬�������ȵ��� shutdown �� shutdownNow������ isTerminated ����Ϊ true��
      * @return
      */
     boolean isTerminated();
     
     /**
      * ����رա�������ʱ���ߵ�ǰ�߳��жϣ�������һ�����ȷ���֮�󣬶�������������ֱ�������������ִ�С�
      * @param timeout
      * @param unit
      * @return
      * @throws InterruptedException
      */
     boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;
     
     /**
      * ִ�и��������񣬵�����������ɻ�ʱ����ʱ�������ĸ����ȷ����������ر�������״̬�ͽ���� Future �б������б������Ԫ�ص� Future.isDone() Ϊ true��
      * һ�����غ󣬼�ȡ����δ��ɵ�����ע�⣬���������ػ�ͨ���׳��쳣����ֹ����� ��������˲������ڽ���ʱ�޸��˸����� collection����˷����Ľ���ǲ�ȷ���ġ� 
      * @param tasks
      * @param timeout
      * @param unit
      * @return
      * @throws InterruptedException
      */
//     <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)throws InterruptedException;
     
     /**
      * ִ�и������������ĳ�������ѳɹ���ɣ�Ҳ����δ�׳��쳣�����򷵻�������
      * һ���������쳣���غ���ȡ����δ��ɵ���������˲������ڽ���ʱ�޸��˸����� collection����˷����Ľ���ǲ�ȷ���ġ� 
      * @param tasks
      * @return
      * @throws InterruptedException
      * @throws ExecutionException
      */
//     <T> T invokeAny(Collection<? extends Callable<T>> tasks)throws InterruptedException, ExecutionException;
     
     /**
      * ִ�и�������������ڸ����ĳ�ʱ����ǰĳ�������ѳɹ���ɣ�Ҳ����δ�׳��쳣�����򷵻�������
      * һ���������쳣���غ���ȡ����δ��ɵ���������˲������ڽ���ʱ�޸��˸����� collection����˷����Ľ���ǲ�ȷ���ġ� 
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
