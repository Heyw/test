package threadpoolproject;


/**
 * ��Ϊ Runnable �� ScheduledFuture���ɹ�ִ�� run ����������� Future ���������������
 * @author heyw
 *
 * @param <V>
 */
public interface RunnableScheduledFuture<V>extends RunnableFuture<V>,ScheduledFuture<V> {
    /**
     * ��������������Եģ�������true�������Ե�������ܻ���ݼƻ��ظ�ִ�У�������������ֻ��ִ��һ��
     * @return
     */
	boolean isPeriodic();
}
