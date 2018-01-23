package test;

public class TestShift {
      public static void main(String[] args) {
		int k=10;
		int j=(k-1)>>>1;
		System.out.println(j+" "+k);
		jian(k);
		System.out.println(k);
	}
      public static void jian(int k){
    	  k=k-1;
      }
}
