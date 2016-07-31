package threadpoolproject;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * �ṩ���������Ӳ�����Queue,���������Ӳ����ֱ���:(1)��ȡԪ��ʱ��Ҫ�ȴ����в�Ϊ��(2)����Ԫ��ʱ��Ҫ�ȴ����пռ���ã�
 * BlockingQueue�ķ�����������ʽ���֣����ڲ����������㵫�����ڽ���ĳһʱ������Ĳ�������������ʽ�Ĵ���ʽ��ͬ:
 * ��һ�����׳��쳣���ڶ����Ƿ���һ������ֵnull����false�����������ڳɹ�ǰ�������������̣߳����������ڸ�����ʱ���������̣߳�����ʱ�������
 * (1)throws exception�ķ���:add(e),remove(),element()
 * (2)��������ֵ�ķ���:offer(e),poll(),peek()
 * (3)�����������ķ���:put(),take()
 * (4)��ʱ�ķ���:offer(e,time,unit),poll(time,unit)
 *   BlockingQueue�����ܿ�Ԫ�أ�����ͼ���÷���add(),offer(),put()��ӿ�Ԫ��ʱ��ʵ������׳�NULlPointerException.null����������ʶpoll()����ʧ�ܵľ���ֵ��
 *   BlockingQueue�ǿ��Ա������޶��ġ�������ʱ�̣���������һ��remaingCapacity����������������ô���ӵ�Ԫ�ؾ��޷�������put������û���ڲ��������Ƶ�
 * BlockingQueue���Ǳ���remainingCapacity��ֵΪInteger.MAX_VALUE��
 *   BlockingQueue��ʵ������Ҫ��Ϊ��������-������ģ�ͽ�����Ƶģ�������Ҳʵ����Collection�ӿڣ��������remove�Ӷ�����ɾ������Ԫ���ǿ��еġ�Ȼ��ͨ�����ֲ������ᱻִ�У�
 * ֻ���мƻ���ż��ʹ�ã�������ȡ���Ŷ���Ϣʱ��
 *   BlockingQueue��ʵ���඼���̰߳�ȫ�ġ����е�queue������ʹ���ڲ����������������������ﵽ���ǵĲ���ԭ���Ե�Ŀ�ġ�Ȼ��������Collection��������addAll(),retainAll()��containAll()
 *  removeAll()��û��ԭ����ִ�У�������һ��ʵ������ר���������˾�����˵��ֻ��c�������һЩԪ��֮����ôaddAll(c)�ܿ��ܻ�ʧ��
 *    BlockingQueue ʵ���ϲ� ֧��ʹ���κ�һ�֡�close����shutdown��������ָʾ��������κ�����ֹ��ܵ������ʹ����������ʵ�ֵ�����
 *  ���磬һ�ֳ��õĲ����ǣ����������ߣ���������� end-of-stream �� poison ���󣬲�����ʹ���߻�ȡ��Щ�����ʱ���������ǽ��н��͡� 
 *  �ڴ�һ����Ч������������뵽BlockingQueue֮ǰ���̲߳���happen-before ��Щ����һ���߳��д�BlockingQueue��ȡ����ɾ��Ԫ��֮��Ĳ���
 *  (�򵥵�˵���ǣ�һ���߳���BlockingQueue���Ԫ�صĲ���happen-before��һ���̻߳�ȡ����ɾ����BlockingQueue�Ĳ���)
 * @author heyw
 *
 * @param <E>
 */
public interface BlockingQueue<E> extends Queue<E> {
    /** 
     * ��Ԫ�ز��뵽ָ��BlockingQueue�У�����������в��Ҳ���Υ���������ƣ�������ɹ�����򷵻�true��������ÿռ䲻���throw new IllegalStateException.
     * ���ʹ���������ƵĶ��У���ô�Ƽ�ʹ��offer()����������Ԫ��
     */
	boolean add(E e) ;
	
	/**
	 * ��ָ��Ԫ�ز���˶����У�������������Ҳ���Υ���������ƣ����ɹ�ʱ���� true�������ǰû�п��õĿռ䣬�򷵻� false��
	 * ��ʹ�����������ƵĶ���ʱ���˷���ͨ��Ҫ���� add(E)�����߿����޷�����Ԫ�أ���ֻ���׳�һ���쳣
	 */
	boolean offer(E e);
	
	/**
	 * ��ָ��Ԫ�ز���˶����У����ȴ����õĿռ䣨����б�Ҫ���� 
	 * @param e
	 * @throws InterruptedException ����ȴ�ʱ�����ж����׳����쳣
	 */
	void put(E e) throws InterruptedException;
	
	/**
	 * ��ָ����Ԫ�ز���˶����У��ڸ����ĵȴ�ʱ�䵽��֮ǰ�ȴ����ÿռ�(�����Ҫ)
	 * @param e
	 * @param timeout �ڷ����ȴ�֮ǰ�ȴ��¼���unitΪ��λ
	 * @param uint
	 * @return
	 * @throws InterruptedException ����ȴ�ʱ�����ж�
	 */
	boolean offer(E e,long timeout,TimeUnit uint)throws InterruptedException;
	
	/**
	 * ��ȡ���Ƴ�����ͷ���Ķ����ڵõ����ö���֮ǰһֱ�ȴ�(�����Ҫ�ػ�)
	 * @return
	 * @throws InterruptedException
	 */
	E take()throws InterruptedException;
	
	/**
	 * ��ȡ���Ƴ�����ͷ�������ڵõ����ö���֮ǰ�ȴ�ָ��ʱ��
	 * @param timeout
	 * @param unit
	 * @return
	 * @throws InterruptedException
	 */
	E poll(long timeout,TimeUnit unit) throws InterruptedException;
    
	/**
	 * �����������������������(û���ڴ����Դ����)�˶����ܽ��ܵĸ���Ԫ�����������û���ڲ����ƣ��򷵻�Integer.MAX_VALUE
	 * ע�⣬��������ͨ����� remainingCapacity ���жϲ���Ԫ�صĳ����Ƿ�ɹ�����Ϊ���ܳ�������������������̼߳���������Ƴ�һ��Ԫ�ء�
	 * @return
	 */
	int remainingCapacity();
	
	/**
	 * �Ӵ˶������Ƴ�ָ��Ԫ�صĵ���ʵ����������ڣ�����ȷ�еؽ�������˶��а���һ���������� o.equals(e) ��Ԫ�� e�����Ƴ���Ԫ�ء�
	 * ����˶��а���ָ��Ԫ�أ����ߴ˶������ڵ��ö��������ģ����򷵻� true
	 * @param o,�����Ӷ�����ɾ����Ԫ�أ�������ڵػ�
	 * @throws NullPointerException ���ָ��Ԫ��Ϊnull�򷵻ظ��쳣
	 */
	boolean remove(Object o);
	
	/**
	 * ��������а���ָ��Ԫ��o���򷵻�true����ȷ�е�˵�����ҽ���������������һ������o.equals(e)��Ԫ��ʱ�ŷ���true
	 */
	boolean contains(Object o);
	
	/**
	 * �Ƴ��˶��������п��õ�Ԫ�أ�����������ӵ����� collection �С��˲������ܱȷ�����ѯ�˶��и���Ч��
	 * ����ͼ�� collection c �����Ԫ��û�гɹ�ʱ�����ܵ������׳�����쳣ʱ��Ԫ�ػ�ͬʱ������ collection �г��֣�����������һ�� collection �г��֣�Ҳ���������� collection �ж������֡�
	 * �����ͼ��һ�����з�����������У���ᵼ�� IllegalArgumentException �쳣�����⣬������ڽ��д˲���ʱ�޸�ָ���� collection����˲�����Ϊ���ǲ���Ԥ�ϵġ� 
	 * @param c
	 * @return  ת�Ƶ�Ԫ������
	 */
	int drainTo(Collection<? super E> c);
	
	/**���Ӵ˶������Ƴ����������Ŀ���Ԫ�أ�������ЩԪ����ӵ����� collection �С�����ͼ�� collection c �����Ԫ��û�гɹ�ʱ��
	 * ���ܵ������׳�����쳣ʱ��Ԫ�ػ�ͬʱ������ collection �г��֣�����������һ�� collection �г��֣�Ҳ���������� collection �ж������֡�
	 * �����ͼ��һ�����з�����������У���ᵼ�� IllegalArgumentException �쳣�����⣬������ڽ��д˲���ʱ�޸�ָ���� collection����˲�����Ϊ�ǲ�ȷ���ġ�
	 * 
	 * @param c
	 * @param maxnum ���ת������
	 * @return ת�Ƶ�Ԫ������
	 */
	int drainTo(Collection<?super E>c ,int maxnum);
}
