package test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import threadpoolproject.ExecutorService;
import threadpoolproject.Executors;
import threadpoolproject.Future;
import threadpoolproject.FutureTask;
import threadpoolproject.ThreadPoolExecutor;

public class TestThreadPool {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
		   ExecutorService executor=Executors.newFixedThreadPool(10);
//		FutureTask<String> task=new FutureTask<>(new Callable<String>(){
//			@Override
//			public String call() throws Exception {
//				// TODO Auto-generated method stub
//				return "000000";
//			}
//		   });
     Future<String> future= executor.submit(new Callable<String>(){
			@Override
			public String call() throws Exception {
				// TODO Auto-generated method stub
				return "000000";
			}
		   });
     System.out.println(future.get());
	}
}
