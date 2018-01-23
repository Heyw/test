package concurrent.practise;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * 该类用来实现缓存复杂计算结果
 * @author heyw
 *
 */
public class CacheCompute {
	    /*Future用来包装保存着的计算结果*/
		private static final ConcurrentMap<Integer ,Future> map=new ConcurrentHashMap<>();
        
        public static int compute(final int arg){
        	Future<Integer> f=map.get(arg);
        	/*如果为空*/
        	if(f==null){
        		FutureTask<Integer> ftaks=new FutureTask<>(new Callable<Integer>(){//匿名内部类，<>中必须设置类型
        			public Integer call(){
        				return Compute.compute(arg);
        			}
        		});
        	    f=map.putIfAbsent(arg, ftaks);
        	    if(f==null){
        	    	f=ftaks;
        	    }
        	    ftaks.run();
        	}
        	int result=0;
            try {
				result=f.get();
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}catch(ExecutionException e){
				e.printStackTrace();
			}
           return result;
        }
        
        public static void main(String[] args) {
			Thread t1=new Thread(){
				@Override
				public void run(){
					System.out.println(CacheCompute.compute(10));
				}
			};
			Thread t2=new Thread(){
				@Override
				public void run(){
					System.out.println(CacheCompute.compute(10));
				}
			};
			t1.start();
			t2.start();
		}
}
class Compute {
	public static int compute(int arg){
		return arg*arg;
	}
}