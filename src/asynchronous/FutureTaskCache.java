package asynchronous;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class FutureTaskCache<T,V> implements Compute<T,V>{

	private final ConcurrentHashMap<T, FutureTask<V>> cache = new ConcurrentHashMap<>();
	private final ThreadPoolExecutor taskRunner=new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(),
			1000, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>(10000));
	
	private Compute<T,V> c;
	
	public FutureTaskCache(Compute<T,V> c) {
		
		this.c=c;
	}
	@Override
	public V compute(T a) {
		// TODO Auto-generated method stub
		
		if(!cache.contains(a)) {
			
			FutureTask<V> ft=new FutureTask<>(new Callable<V>() {

				@Override
				public V call() throws Exception {
					// TODO Auto-generated method stub
					return c.compute(a);
				}
				
			});
			FutureTask<V> result = cache.putIfAbsent(a,ft);
			if(result!=null) {
				
				try {
					return result.get();
				} catch (InterruptedException | ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}else {
				//尚未被缓存，则开始执行该任务
				taskRunner.submit(ft);
			}
		}
		return null;
	}


	

}


