package threadpoolproject;

import java.util.concurrent.TimeUnit;

/**
 * һ�ֻ�Ϸ��Ľӿڣ����������ЩӦ���ڸ����ӳ�ʱ��֮��ִ�еĶ��� 
 * �˽ӿڵ�ʵ�ֱ��붨��һ�� compareTo �������÷����ṩ��˽ӿڵ� getDelay ����һ�µ�����
 * @author heyw
 *
 */
public interface Delayed extends Comparable<Delayed>{
	/**
	 * ������˶�����ص�ʣ���ӳ�ʱ�䣬�Ը�����ʱ�䵥λ��ʾ��
	 * @param unit
	 * @return
	 */
	long getDelay(TimeUnit unit);

}
