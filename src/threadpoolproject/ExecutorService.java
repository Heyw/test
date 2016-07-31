package threadpoolproject;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

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
     <T> FutureTask<T> submit(Runnable r,T result);
}
