package test;

public class TestFather {
     public  void people(){
    	 System.out.println("i am father");
     }
      class Son extends TestFather{
    	@Override
    	  public  void people(){
    		 System.out.println("i am son");
    	 }
     }
}

