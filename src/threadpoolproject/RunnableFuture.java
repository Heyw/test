package threadpoolproject;


/**
 * ͬʱ��Runnable��Future���ɹ�ִ��run()���������¼������ɲ��������ȡ������
 * @author heyw
 *
 * @param <V>����get()���صĲ�������
 */
public interface RunnableFuture<V> extends Future<V>,Runnable{
   /**
    * ���ø�Future��ȡ���ļ������������Ѿ���ȡ����
    */
	void run();
}
