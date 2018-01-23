package test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class TestReadAndWriteLock {
            public static List<String> list=new ArrayList<>();
           static  ReentrantReadWriteLock lock=new ReentrantReadWriteLock();
           static Lock rl=lock.readLock();
           static Lock wl=lock.writeLock();
           static Thread createReadThread(){
            	Thread t=new Thread(){
            		@Override
            		public void run(){
            			rl.lock();
            			System.out.println("获取了读锁！！！！");
            			try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
               		    for(Iterator<String> it=list.iterator();it.hasNext();)
               		    	System.out.println(it.next());
               		    list.add(Thread.currentThread().getName());
               			rl.unlock();
               			System.out.println("释放了了读锁！！！！");
            		}
            	};
            	return t;
            }
           
           static Thread createWriteThread(){
           	Thread t=new Thread(){
           		@Override
           		public void run(){
           			wl.lock();
        			list.add(Thread.currentThread().getName());
        			wl.unlock();
           		}
           	};
           	return t;
           }
           
           public static void main(String[] args) {
				Thread r1=createReadThread();
				Thread r2=createReadThread();
				Thread r3=createReadThread();
				Thread w1=createWriteThread();
				Thread w2=createWriteThread();
				r1.start();
				r2.start();
				r3.start();
				w1.start();
				w2.start();
		}
}
