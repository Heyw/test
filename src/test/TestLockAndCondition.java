package test;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TestLockAndCondition {
        private static final ReentrantLock lock=new ReentrantLock();
        private static final Condition empty=lock.newCondition();
        private static final Condition full=lock.newCondition();
        private static int num=0;
        public static void main(String[] args) throws InterruptedException {
			Thread t1=createT1();
			Thread t2=createT2();
			Thread t3=createT1();
			Thread t4=createT2();
			t1.start();
			t2.start();
			t3.start();
			t4.start();
		}
       static Thread createT1(){
        	Thread t1=new Thread(){
				@Override
				public void run(){
					try{
						lock.lock();
						System.out.println(Thread.currentThread().getName()+" :��ȡ��");
						while(true){
							try {
								empty.signal();
								if(num>=10){
									System.out.println(Thread.currentThread().getName()+" :��־Ϊ10����Ҫ���٣���");
									full.await();//��ǰ�߳�������ͬʱ����ͬ���������߳�
								}
							} catch (InterruptedException e) {
							}
							num++;
						}
					}finally{
						lock.unlock();
					}
				};
			};
			return t1;
        }
        
        static Thread createT2(){
        	Thread t2=new Thread(){
				@Override
				public void run(){
					try{
						lock.lock();
						System.out.println(Thread.currentThread().getName()+" :��ȡ��");
							while(true){
								full.signal();
									if(num==0){
										try {
											System.out.println(Thread.currentThread().getName()+" :��־Ϊ0����Ҫ��ӣ���");
											empty.await();
										} catch (InterruptedException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
									}
								num--;
						}
					}finally{
						lock.unlock();
					}
				};
			};
			return t2;
        }
        
        static Thread createT3(){
        	Thread t3=new Thread(){
				@Override
				public void run(){
							while(true){
								try {
									if(num==0){
										empty.await();
									}
								} catch (InterruptedException e) {
								}
								num--;
						}
				};
			};
			return t3;
        }
        
        static Thread createT4(){
        	Thread t4=new Thread(){
				@Override
				public void run(){
							while(true){
								try {
									if(num==10){
										full.await();
									}
								} catch (InterruptedException e) {
								}
								num++;
						}
				};
			};
			return t4;
        }
}
