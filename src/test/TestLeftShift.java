package test;

public class TestLeftShift {
	 private final static int COUNT_BITS=Integer.SIZE-3;
	 private static final int CAPACITY=(1<<COUNT_BITS)-1;
	 //����״̬RunningΪ -1����29λ����11111111111111111111111111111111����29λΪ11100000000000000000000000000000
	 private static final int  RUNNING=-1<<COUNT_BITS;
	 //����״̬SHUTDOWNΪ0����29λ������Ϊ00000000000000000000000000000000
	 private static final int SHUTDOWN=0<<COUNT_BITS;
	 //����״̬STOP��ֵΪ1����29λ��λ00100000000000000000000000000000
	 private static final int STOP=1<<COUNT_BITS;
	 //����״̬TIDYING��ֵΪ2����29λ��λ01000000000000000000000000000000
	 private static final int TIDYING=2<<COUNT_BITS;	
	 //����״̬TERMINATED��ֵΪ3����29λ��λ01100000000000000000000000000000
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
    	  System.out.println(exclusiveCount(65536*2));//���c����65535����Ϊ0��С��65534����ԭֵ���Ӷ�ʵ����ֻҪ�й�������û�л�����
    	  System.out.println(sharedCount(65536*2));
	}
}
