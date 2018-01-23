package test;

public class TestLeftShift {
	 private final static int COUNT_BITS=Integer.SIZE-3;
	 private static final int CAPACITY=(1<<COUNT_BITS)-1;
	 //运行状态Running为 -1左移29位，即11111111111111111111111111111111左移29位为11100000000000000000000000000000
	 private static final int  RUNNING=-1<<COUNT_BITS;
	 //运行状态SHUTDOWN为0左移29位，还是为00000000000000000000000000000000
	 private static final int SHUTDOWN=0<<COUNT_BITS;
	 //运行状态STOP的值为1左移29位，位00100000000000000000000000000000
	 private static final int STOP=1<<COUNT_BITS;
	 //运行状态TIDYING的值为2左移29位，位01000000000000000000000000000000
	 private static final int TIDYING=2<<COUNT_BITS;	
	 //运行状态TERMINATED的值为3左移29位，位01100000000000000000000000000000
	 private static final int TERMINATED=3<<COUNT_BITS;
	 
	 static final int SHARED_SHIFT   = 16;
     static final int SHARED_UNIT    = (1 << SHARED_SHIFT);
     static final int MAX_COUNT      = (1 << SHARED_SHIFT) - 1;
     static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;

     /** Returns the number of shared holds represented in count  */
     static int sharedCount(int c)    { 
    	 return c >>> SHARED_SHIFT; 
     }
     /** Returns the number of exclusive holds represented in count  */
     static int exclusiveCount(int c) {
    	 return c & EXCLUSIVE_MASK; 
    	 }
	   private static int ctlOf(int rs,int wc){
		   return rs | wc;
	   }

      public static void main(String[] args) {
//          System.out.println(Integer.toBinaryString(CAPACITY));
//          System.out.println(Integer.toBinaryString(-1<<COUNT_BITS-1));
//          System.out.println(Integer.toBinaryString(ctlOf(RUNNING, 0)));
//          System.out.println(2>>>1);
    	  System.out.println(exclusiveCount(65536*2));//如果c大于65535，则都为0，小于65534返回原值，从而实现了只要有共享锁就没有互斥锁
    	  System.out.println(sharedCount(65536*2));
	}
}
