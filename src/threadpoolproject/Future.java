package threadpoolproject;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Future�����첽����Ľ�����ṩ�˷��������������Ƿ��Ѿ���ɣ��Ա�ȴ�������ɣ�����ȡ��������
 * ���������ʱ��ֻ��ͨ������get()����ȡ����Ľ�������б�Ҫ�ڼ������ǰ(if necessary until it is ready)���������÷�����
 * ȡ��������ͨ������cancell()��ִ�е�(performed)���ṩ���������������������Ƿ�������ɻ����Ƿ��Ѿ�ȡ����
 * ���Ϊ��׷���ȡ���Բ��Ҳ����ṩ���õĽ�������Խ���������ΪFuture<? >,���ҽ�returned null������Ǳ�����񷵻ء�
 * FutureTaskʵ����Future��ͬʱҲʵ����Runnable,���FutureTask���Ա�Executorִ�С�
 * �ڴ�һ���ԣ��첽�����ȡ�Ĳ��� happen-before����������һ���߳�����ز���Future.get()֮��
 * @author heyw
 *
 */
public interface Future<V> {
	/**
	 * ����ȡ�������ִ�С���������Ѿ���ɣ������Ѿ�ȡ���ˣ�������Ϊ����һЩԭ���ܱ�ȡ������ô��γ��Խ���ʧ�ܷ���null��
	 * ���ȡ���ɹ��˲��ҵ�����cancel()����ʱ������û��ʼ����ô��������Զ����ִ�С�
	 * ����������Ѿ������ˣ���ô���ݲ���mayInterruptIfRunning�ж��Ƿ�Ӧ���Գ���ֹͣ����ķ�ʽ���ж�ִ��������̡߳�
	 * ����÷������غ���ô��������isDone()������ʼ�շ���true.������÷�������true,��ô�������÷���isCancelled()Ҳ������true��
	 * @param mayInterruptIfRunning ���Ϊtrue����ô�����ж�ִ��������̣߳�����������ֳ�������ɡ�
	 * @return false ˵���������޷�ȡ����ͨ������Ϊ�������Ѿ�����ˡ�
	 */
     boolean cancel(boolean mayInterruptIfRunning);
     /**
      * ����������������ǰ�ͱ�ȡ���ˣ���ô����true
      * @return
      */
     boolean isCancelled();
     /**
      * ���������ɣ�������true���������ɿ�������Ϊ��ֹ���쳣����ȡ������ɣ���Щ�������isDone()������������true��
      * @return
      */
     boolean isDone();
     /**
      * �����Ҫʱ�ȴ��������ɣ�����ȡ������
      * @return ��ɼ���Ľ��
      * @throws InterruptedException �����ǰ�߳�threw an new InterruptedException
      * @throws ExecutionException �����ǰ�߳��ڵȴ�ʱ���ж���
      */
     V get() throws InterruptedException,ExecutionException;
     /**
      *�����Ҫ�ȴ��������ɣ������ȴ�������timeoutʱ�䣬Ȼ��������еĻ�����ȡ���
      * @param timeout �ȴ�������ɵ�ʱ������
      * @param unit   ʱ�䵥λ
      * @return
      */
     V get(long timeout,TimeUnit unit) throws InterruptedException,ExecutionException,TimeoutException;
}
