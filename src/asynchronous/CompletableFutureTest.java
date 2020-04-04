package asynchronous;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;

public class CompletableFutureTest {
	

	/**
	 * 无返回值异步执行，默认使用ForkJoinPool.commonPool作为线程池
	 */
//	@Test
	public void runAsync() {

		CompletableFuture<Void> runAsync = CompletableFuture.runAsync(() -> {

			System.out.println("this is a void-return asyncTask");
		});
		try {
			System.out.println(runAsync.get());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * 有返回值异步执行，默认使用ForkJoinPool.commonPool作为线程池，可以指定线程池执行异步代码
	 */
//	@Test
	public void supplyAsync() {

		CompletableFuture<String> supplyAsync = CompletableFuture.supplyAsync(() -> {

			return "Hello";
		});
		try {
			System.out.println(supplyAsync.get());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * complete方法会终止异步任务，如果异步任务尚未执行完，则get()返回指定结果，如果执行完了那么get()返回异步任务结果；
	 */
//	@Test
	public void complete() {
		CompletableFuture<String> supplyAsync = CompletableFuture.supplyAsync(() -> {

			return "Hello";
		});
		supplyAsync.complete("World");
		try {
			System.out.println(supplyAsync.get());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * complete方法会尝试终止异步任务，如果异步任务尚未执行，则get()将返回一个异常，如果执行完了那么get()返回异步任务结果；
	 */
//	@Test
	public void completeExceptionally() {
		CompletableFuture<String> supplyAsync = CompletableFuture.supplyAsync(() -> {

			return "Hello";
		});
		supplyAsync.completeExceptionally(new Exception("执行异常"));
		try {
			System.out.println(supplyAsync.get());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * thenApply用来处理异步任务执行结果
	 */
//	@Test
	public void thenApply() {
		CompletableFuture<String> supplyAsync = CompletableFuture.supplyAsync(() ->{
			System.out.println("currentThread:"+Thread.currentThread().getName());
			return "Hello";
		}).thenApply(s -> {
			System.out.println("currentThread:"+Thread.currentThread().getName());
		     return s+" world";
		}).thenApply(s->{
			System.out.println("currentThread:"+Thread.currentThread().getName());
			return s.toUpperCase();
		});
		try {
			System.out.println(supplyAsync.get());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	/**
	 * thenApply用来处理异步任务执行结果
	 */
//	@Test
	public void thenApplyAsync() {
		CompletableFuture<Double> supplyAsync = CompletableFuture.supplyAsync(() -> {
			System.out.println("currentThread:"+Thread.currentThread().getName());
			return "Hello";
		}).thenApplyAsync(s->{  
			System.out.println("currentThread:"+Thread.currentThread().getName());
			return s + " World";}).thenApplyAsync(s->{
				System.out.println("currentThread:"+Thread.currentThread().getName());
				return 10.0;
			});
		try {
			System.out.println(supplyAsync.get());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * thenCompose用来组合多个异步操作，将前一个的输出当成后一个异步操作的输入，get()方法将获取最后一个异步操作返回的结果
	 */
//	@Test
	public void thenCompose() {
		CompletableFuture<String> supplyAsync = CompletableFuture.supplyAsync(() -> {
			System.out.println("currentThread:"+Thread.currentThread().getName());
			return "Hello";
		}).thenCompose(s->CompletableFuture.supplyAsync(()->{
			System.out.println("currentThread:"+Thread.currentThread().getName());
			return s+ "World";
		}));
		try {
			System.out.println(supplyAsync.get());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Test
	public void thenCombine() {

		CompletableFuture<Double> supplyAsync = CompletableFuture.supplyAsync(() -> {
			System.out.println("currentThread:"+Thread.currentThread().getName());
			Object obj=init(null);
			return obj.getClass().toString();
		}).thenCombine(CompletableFuture.supplyAsync(()->23.00), (t,v)->{
			
		System.out.println("thenCombine:"+t);
		return v*v;
		});
		try {
			System.out.println(supplyAsync.get());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
//	@Test
	public void whenComplete() {
		
		CompletableFuture<String> supplyAsync = CompletableFuture.supplyAsync(() -> {
			System.out.println("currentThread:"+Thread.currentThread().getName());
			return "Hello";
		}).whenComplete((r,t)->{
			
			if(t!=null) {
				
				System.out.println("whenComplete:"+t.getMessage());
			}else {
				System.out.println("whenComplete:"+r);
			}
		});
		try {
			System.out.println(supplyAsync.get());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
//	@Test
	public void whenCompleteExceptionally() {
		
		CompletableFuture<String> supplyAsync = CompletableFuture.supplyAsync(() -> {
			System.out.println("currentThread:"+Thread.currentThread().getName());
			Object obj=init(null);
			return obj.getClass().toString();
		}).whenComplete((r,t)->{
			
			if(t!=null) {
				
				System.out.println("whenComplete:"+t.getMessage());
			}else {
				System.out.println("whenComplete:"+r);
			}
		});
		try {
			System.out.println(supplyAsync.get());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
//	@Test
	public void handleExceptionally() {
		
		CompletableFuture<String> supplyAsync = CompletableFuture.supplyAsync(() -> {
			System.out.println("currentThread:"+Thread.currentThread().getName());
			Object obj=init(null);
			return obj.getClass().toString();
		}).handle((r,t)->{
			
			if(t!=null) {
				
				System.out.println("handle:"+t.getMessage());
				return "exception happened";
			}else {
				System.out.println("handle:"+r);
				return "execute success";
			}
		});
		try {
			System.out.println(supplyAsync.get());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
//	@Test
	public void exceptionally() {
		
		CompletableFuture<String> supplyAsync = CompletableFuture.supplyAsync(() -> {
			System.out.println("currentThread:"+Thread.currentThread().getName());
			Object obj=init(null);
			return obj.getClass().toString();
		}).exceptionally(t->{
			
			if(t!=null) {
				
				System.out.println("handle:"+t.getMessage());
				return "exception happened";
			}
			return "exceptionally: Hello";
		});
		try {
			System.out.println(supplyAsync.get());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private Object init(String str) {
		if(str=="Hello") {
			
			return new Object();
		}else {
			
			return null;
		}
		
	}
}
