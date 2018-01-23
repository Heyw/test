package test;

public class TestMultiState {
    static  class Father {
    	   public void run(){
    		   start();//��̬����ʱ�������������
    	   }
    	   public void start(){
    		   System.out.println("this is the father");
    	   }
       }
     static class Son extends Father{
    	   @Override
    	   public void start(){
    		   System.out.println("this is the son");
    	   }
       }
       public static void main(String[] args) {
		Father son=new Son();
		son.run();
	}
}
