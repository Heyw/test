package threadpoolproject;


/**
 * 同时是Runnable的Future。成功执行run()方法将导致计算的完成并且允许获取计算结果
 * @author heyw
 *
 * @param <V>方法get()返回的参数类型
 */
public interface RunnableFuture<V> extends Future<V>,Runnable{
   /**
    * 设置该Future获取它的计算结果，除非已经被取消。
    */
	void run();
}
