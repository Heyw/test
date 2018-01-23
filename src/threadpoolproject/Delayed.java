package threadpoolproject;

import java.util.concurrent.TimeUnit;

/**
 * 一种混合风格的接口，用来标记那些应该在给定延迟时间之后执行的对象。 
 * 此接口的实现必须定义一个 compareTo 方法，该方法提供与此接口的 getDelay 方法一致的排序
 * @author heyw
 *
 */
public interface Delayed extends Comparable<Delayed>{
	/**
	 * 返回与此对象相关的剩余延迟时间，以给定的时间单位表示。
	 * @param unit
	 * @return
	 */
	long getDelay(TimeUnit unit);

}
