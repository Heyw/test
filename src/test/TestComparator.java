package test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TestComparator {
    public static void main(String[] args) {
		   List<ComTest> list=new ArrayList<>();
		   for(int i=0;i<10;i++){
			   int index=(int) (Math.random()*100);
			   list.add(new ComTest(index));
		   }
		   Collections.sort(list, new Comparator<ComTest>(){
			   public int compare(ComTest t1,ComTest t2){
				   if( t1.index>t2.index)
					   return -1;//-1��ʾt1����t2ǰ��
				   if(t1.index==t2.index)
					   return 0;
				   if(t1.index<t2.index)
					   return 1;//1��ʾ����������˳������������
				   return 0;
			   }
		   });
		  for(ComTest str:list){
			  System.out.println(str.index);
		  }
	}
}

class ComTest{
	int index;
	public ComTest(int index){
		this.index=index;
	}
}
