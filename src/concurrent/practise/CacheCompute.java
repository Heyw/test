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
 * ��������ʵ�ֻ��渴�Ӽ�����
 * @author heyw
 *
 */
public class CacheCompute {
	    /*Future������װ�����ŵļ�����*/
		private static final ConcurrentMap<Integer ,Future> map=new ConcurrentHashMap<>();
        
        public static int compute(final int arg){
        	Future<Integer> f=map.get(arg);
        	/*���Ϊ��*/
        	if(f==null){
        		FutureTask<Integer> ftaks=new FutureTask<>(new Callable<Integer>(){//�����ڲ��࣬<>�б�����������
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