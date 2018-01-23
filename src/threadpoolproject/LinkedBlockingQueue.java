package threadpoolproject;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


/**
 * һ�����������ӽڵ�ġ���Χ����� blocking queue���˶��а� FIFO���Ƚ��ȳ�������Ԫ�ء����е�ͷ�����ڶ�����ʱ�����Ԫ�ء����е�β�� ���ڶ�����ʱ����̵�Ԫ�ء�
 * ��Ԫ�ز��뵽���е�β�������Ҷ��л�ȡ��������λ�ڶ���ͷ����Ԫ�ء�������е�������ͨ��Ҫ���ڻ�������Ķ��У������ڴ��������Ӧ�ó����У����Ԥ֪������Ҫ�͡� 
 * ��ѡ��������Χ�Ĺ��췽������������Ϊ��ֹ���й�����չ��һ�ַ��������δָ���������������������� Integer.MAX_VALUE��
 * ���ǲ���ڵ��ʹ���г�������������ÿ�β����ᶯ̬�ش������ӽڵ㡣 
 * ���༰�������ʵ�� Collection �� Iterator �ӿڵ����п�ѡ ������ ������ Java Collections Framework �ĳ�Ա��
 * @author heyw
 * @param <E>
 */
public class LinkedBlockingQueue<E>extends AbstractQueue<E> implements BlockingQueue<E>,java.io.Serializable{

	private static final long serialVersionUID = 1229311867736205222L;

	/*
	 * "two lock queue"�㷨��һ�ֱ��塣putLock��ֻ����put(offer)�������룬���Һ͵ȴ��Ľڵ��������ϵ��takeLockͬ�����ơ�
	 * putLock��takeLock��������count���Ա�����ΪAtomicInteger����Ϊ�˱����ڴ���������жԻ�ȡ����������Ҫ��
	 * ͬ������puts�Ի�ȡtakeLock����Ҫ��С�̶Ȼ���cascading notifies��ʹ���ˡ���һ��putע�⵽���Ѿ�ʹ��������һ��take����ô����֪ͨtaker��
	 * taker������Ҳ��֪ͨ�������������signals������items�����ˡ�ͬ��takeҲ��֪ͨputs������remove��iterators�Ĳ��������ȡ��������
	 *     ������writers��readers֮��Ŀɼ���:
	 *  ���ۺ�ʱһ�����������У���ô�����ȡputLockͬʱcount������¡�һ�������readers���ᱣ֤��Ҫô��ͨ����ȡputLockҪô��ͨ����ȡtakeLock�������
	 *  �Ľڵ�Ŀɼ��ԣ�Ȼ��ͨ��n=count.get()���⽫���ݿɼ��Ը���һ��n items��
	 *  Ϊ��ʵ����һ�µ�iterators������ȥ������Ҫ�������нڵ��ǰ���Ѿ������нڵ�GC-reachable���⽫�ᵼ�������������⣺
	 *  ������åIterator�������޵��ڴ�����������нڵ���ʱ��tenured�ģ���ô���������Ͻڵ�Ŀ�����ӣ�����������£�gc���Ѵ����Ӷ�������ظ��Ĵ󼯺ϡ�
	 *  Ȼ�������Գ��еĽڵ㣬ֻ��û�б�ɾ���Ľڵ����Ҫ���ֿɴ��ԣ�ͬʱ�ɴ���û�б�Ҫ��ΪGC�������ͣ����ǽ��ոճ��еĽڵ����ӵ�������
	 *  �������ҹ�����ζ��������head.next
	 */
	/**
	 * ����ڵ���
	 * @author heyw
	 *
	 * @param <E>
	 */
	static class Node<E>{
		E item;
		Node<E> next;
		Node(E e){
			this.item=e;
		} 
	}
	/**�������ޣ����null��ΪInteger.maxValue*/
	private final int capacity;
	/**Ԫ�صĸ���*/
	private final AtomicInteger count=new AtomicInteger(0);
	/**����ͷ�ڵ㣬���ɱ�����head.item=null
	 */
	private transient Node<E>head;
	/**����β�ڵ㣬���ɱ�����last.next=null
	 */
	private transient Node<E>last;
	/**takeLock,��ȡ��, ͨ��take������*/
	private final ReentrantLock takeLock=new ReentrantLock();
	/**�ȴ�takes�ĵȴ�����*/
	private final Condition notEmpty=takeLock.newCondition();
	/**putLock,��������ͨ��put����*/
	private final ReentrantLock putLock=new ReentrantLock();
	/**�ȴ�puts�ĵȴ�����*/
	private final Condition notFull=putLock.newCondition();
	
	/**
	 * ֪ͨһ���ȴ�take���÷���ֻ����put��offer�����е���
	 */
	private void signalNotEmpty(){
		final ReentrantLock lock=this.takeLock;
		lock.lock();
		try{
			notEmpty.signal();
		}finally{
			lock.unlock();
		}
	}
	/**
	 * ֪ͨһ��wait��put(�����������Ԫ��)���÷���ֻ����take��poll������ʹ��
	 */
	private void signalNotFull(){
		final ReentrantLock lock=this.takeLock;
		lock.lock();
		try{
			notFull.signal();
		}finally{
			lock.unlock();
		}
	}
	/**
	 * ����һ���޽����������
	 */
	public LinkedBlockingQueue(){
		this(Integer.MAX_VALUE);
	}
	/**
	 * ����ָ��ֵ������������
	 * @param c
	 */
	public LinkedBlockingQueue(int c){
		if(c<0)throw new IllegalArgumentException();
		this.capacity=c;
		last=head=new Node<E>(null);
	}
	
	/**
	 * ����һ����ʼ����ΪInteger.MAX_VALUE���Ұ���ָ�����ϴ�СԪ�ص���������
	 * @param c
	 */
	public LinkedBlockingQueue(Collection<? extends E> c){
		this.capacity=Integer.MAX_VALUE;
		final ReentrantLock lock=this.putLock;
		lock.lock();//û�о�̬��ֻ��Ϊ�˿ɼ��Եı�Ҫ
		try{
			int n=0;
			for(E e:c){
				if(e==null) throw new NullPointerException();
				if(n>=capacity) throw new IllegalStateException("queue is full");
				enqueue(new Node<E>(e));
				n++;
			}
			count.set(n);
		}finally{
			lock.unlock();
		}
	}
	
	/**
	 * ��node���ӵ�list��β��
	 * @param node
	 */
	private void enqueue(Node<E> node){
		last=last.next=node;
	}
	
	/**
	 * �Ƴ�list��ͷ��㣬��������һ���ڵ��item����Ϊhead��item��ԶΪnull
	 * Ȼ��headָ����һ���ڵ�
	 * @return
	 */
	private E dequeue(){
//		Node<E> p=head.next;
//		Node<E> next=p.next;
//		head.next=next;
//		E item=p.item;
//		p.item=null;
//		p.next=p;//help gc p��p.next�γ�һ���������ڻ���
//		return item;
		Node<E>h=head;
		Node<E>first=head.next;
		h.next=h;//help gc p��p.next�γ�һ���������ڻ���
		E item=first.item;
		first.item=null;
		head=first;
		return item;
	}
	
	/**
	 * ���ýڵ�q��ǰ�νڵ���ɾ����ǰ�ڵ�q
	 * @param pred
	 * @param q
	 */
	private void unlink(Node<E> pred,Node<E> p){
		//���ø÷���������is FullyLock()�����
		//p.next���ᱻ�ı䣬�������p��iteratorsά����һ����
		//��pred��nextָ��p.next�������ͽ�p�ڵ������
		//�ж�p�Ƿ���last������Ǿͽ�lastָ��pred
		pred.next=p.next;
		p.item=null;//����gc
		if(last==p)
			last=pred;
      if(count.get()==capacity)
    	  signalNotFull();
	}
	
	/**
	 * �Ӵ˶����Ƴ�ָ��Ԫ�صĵ���ʵ����������ڣ�����ȷ�еؽ�������˶��а���һ���������� o.equals(e) ��Ԫ�� e��
	 *  ���Ƴ�һ��������Ԫ�ء�����˶��а���ָ��Ԫ�أ��򷵻� true�����ߴ˶������ڵ��ö��������ģ��򷵻� true���� 
	 */
   public boolean remove(Object o){
	   if(o==null) return false;
	   fullyLock();
	   try{
			  for(Node<E>pred=head,p=head.next;p!=null;pred=p,p=p.next){
				  if(o.equals(p.item)){
					  unlink(pred, p);
					  return true;
				  }
			  }
			  return false;
	   }finally{
		   unfullyLock();
	   }
   }
	private void fullyLock(){
		this.takeLock.lock();
		this.putLock.lock();
	}
	private void unfullyLock(){
		this.takeLock.unlock();
		this.putLock.unlock();
	}
	/**
	 * ��ָ����Ԫ�ز������β��������������У��Ҳ��ᳬ������������ɹ�����true�����򷵻�false
	 * ��ʹ���������ƵĶ���ʱ���÷�������add��������Ϊadd��������Ԫ��ʧ�ܣ�ֻ���׳�һ���쳣
	 * ��put�Ĳ�����ڣ�offer������������������������˳�������һ��false����put������
	 */
	public boolean offer(E e) {
		if(e==null)throw new NullPointerException();
		int c=-1;
		final AtomicInteger count=this.count;
		if(count.get()==capacity)//��������������˳�����false
			return false;
		final ReentrantLock putLock=this.putLock;
		Node<E>node =new Node<>(e);
		putLock.lock();
		try{
			if(count.get()<capacity){
				enqueue(node);
				c=count.getAndIncrement();
				if(c+1<capacity){
					notFull.signal();//֪ͨ����put����
				}
			}
		}finally{
			putLock.unlock();
		}
		if(c==0)
			signalNotEmpty();
		return c>=0;
	}

	/**
	 * ��ȡ���Ƴ���ͷԪ�أ�����ȴ�
	 * @return
	 */
	public E poll() {
	   if(count.get()==0) return null;
	   final ReentrantLock takelock=this.takeLock;
	   final AtomicInteger count=this.count;
	   E x=null;
	   int c=-1;
	   takelock.lock();
	   try{
		   if(count.get()>0){//��һ������Ҫ�ж�
		     x=dequeue();
		     c=count.getAndDecrement();
		     if(c>1) notEmpty.signal();
		   }
	   }finally{
		   takelock.unlock();
	   }
	   if(c==capacity){
		   signalNotFull();
	   }
		return x;
	}

	/**
	 * ��ȡ����ͷ��Ԫ�أ������Ƴ�
	 * @return
	 */
	public E peek() {
        if(count.get()==0) return null;
        E x;
        final ReentrantLock takelock=this.takeLock;
        takelock.lock();//��Ҫ����
        try{
        	Node<E>first=head.next;
        	x=first.item;
        	if(x==null)return null;
        	else return x;
        }finally{
        	takelock.unlock();
        }
	}

	/**
	 * ���ظö��е�iterator
	 */
	public Iterator<E> iterator() {
		return new Itr();
	}

   /**
    * ���ض�����Ԫ�صĴ�С
    * @return
    */
	public int size() {
		return count.get();
	}

   /**
    * �ڶ���β������һ��Ԫ�أ������Ҫ���ȴ��ռ��ÿ���
    */
	public void put(E e) throws InterruptedException {
		if(e==null)throw new NullPointerException();
	     Node<E> node=new Node<>(e);
	     //��put��offer�����лᾺ�����������������ʼ����Ϊ������Ϊ�˷�ֹ�ñ���û�б����ö�������ʧ��
	     int c=-1;
	     final AtomicInteger count=this.count;
	     final ReentrantLock putlock=this.putLock;
	     putlock.lockInterruptibly();//�����ǰ�߳�û�б��ж����ȡ��
	     try{
	    	 while(count.get()==capacity){//���������������ȴ�����
	    		 notFull.await();
	    	 }
	    	 //ע�⵽count����wait guard��ʹ�õģ���ʹû�б����������������У�����Ϊcountֻ���������ܼ���(����puts������������)
	    	 //���capacity�����˱仯�����Ǻ�����һЩwaiting put���ᱻ���ѡ�������wait guard�е�count��ʹ����Ҳ��һ����
	    	 enqueue(node);
	    	 c=count.getAndIncrement();
	    	 if(c+1<capacity)
	    		 notFull.signal();
	     }finally{
	    	 putlock.unlock();
	     }
		 if(c==0){
			 signalNotEmpty();
		 }
	}


	public boolean offer(E e, long timeout, TimeUnit unit)
			throws InterruptedException {
        if(e==null)throw new NullPointerException();
        timeout=unit.toNanos(timeout);
        final ReentrantLock putLock=this.putLock;
       final AtomicInteger count=this.count;
       int c=-1;
       putLock.lock();
       try{
    	   while(count.get()==capacity){
        	  if(timeout<=0)return false;
        	   timeout=notFull.awaitNanos(timeout);//�ȴ�timeout���ȴ���������ʣ��ʱ��
    	   }
    		   Node<E>node=new Node<>(e);
    		   enqueue(node);
    		   c=count.getAndIncrement();
    		   if(c+1<capacity){
    			   notFull.signal();
    		   }
       }finally{
    	   putLock.unlock();
       }
       if(c==0)
    	   signalNotEmpty();
		return true;
	}

	 public boolean contains(Object o){
		 if(o==null) return false;
		 fullyLock();
		 try{
			 for(Node<E>p=head.next;p!=null;p=p.next){
				 if(o.equals(p.item))
					 return true;
			 }
			 return false;
		 }finally{
			 unfullyLock();
		 }
	 }
	/**
	 * 
	 */
	public E take() throws InterruptedException {
		final ReentrantLock takelock=this.takeLock;
		final AtomicInteger count=this.count;
		E item=null;
		int c=-1;
		takelock.lockInterruptibly();
		try{
			while(count.get()==0){
				notEmpty.await();//����Ϊ�գ�����notEmpty�ȴ�
			}
			 item=dequeue();
			c=count.getAndDecrement();
			if(c-1>=0){//�������Ԫ�أ���ônotEmpty֪ͨ�ȴ���take
				notEmpty.signal();
			}
		}finally{
			takelock.unlock();
		}
		if(c==capacity){
			signalNotFull();
		}
		return item;
	}


	public E poll(long timeout, TimeUnit unit) throws InterruptedException {
		E x;
		final ReentrantLock takelock=this.takeLock;
		final AtomicInteger count = this.count;
		long nanos=unit.toNanos(timeout);
		int c=-1;
		takelock.lockInterruptibly();
		try{
			while(count.get()==0){
				if(nanos<=0) return null;
				notEmpty.awaitNanos(nanos);
			}
			x=dequeue();
			c=count.getAndDecrement();
			if(c-1>0){
				notEmpty.signal();
			}
		}finally{
			takelock.unlock();
		}
		if(c==capacity){
			signalNotFull();
		}
		return x;
	}

	/**
	 * ������������£�û���ڴ����ԴԼ�����˶��пɽ��ܲ��Ҳ��ᱻ�����ĸ���Ԫ�����������������ǵ��ڴ˶��еĳ�ʼ������ȥ���еĵ�ǰ size��
	 *  ע�⣬���� ����ͨ����� remainingCapacity ���϶���ͼ����һ��Ԫ���Ƿ�ɹ�����Ϊ��������һ���߳̽�������Ƴ�ĳ��Ԫ��
	 */
	public int remainingCapacity() {
		return capacity-count.get();
	}

	@Override
	public int drainTo(Collection<? super E> c) {
		return drainTo(c,count.get());
	}

	/**���Ӵ˶������Ƴ����������Ŀ���Ԫ�أ�������ЩԪ����ӵ����� collection �С�
	 * ����ͼ�� collection c �����Ԫ��û�гɹ�ʱ�����ܵ������׳�����쳣ʱ��Ԫ�ػ�ͬʱ������ collection �г��֣�
	 * ����������һ�� collection �г��֣�Ҳ���������� collection �ж������֡������ͼ��һ�����з�����������У���ᵼ�� IllegalArgumentException �쳣��
	 * ���⣬������ڽ��д˲���ʱ�޸�ָ���� collection����˲�����Ϊ�ǲ�ȷ���ġ� 
	 */
		public int drainTo(Collection<? super E> c, int maxnum) {
		if(c==null) throw new NullPointerException();//cΪ�գ��׳�NullPointerException
		if(c==this) throw new IllegalArgumentException();
		final ReentrantLock takeLock=this.takeLock;
		AtomicInteger count=this.count;
		boolean signalNotEmpty=false;
        takeLock.lock();
        try{
        	int n=Math.min(maxnum, count.get());//��ȡ��Сֵ��Ϊ���Ԫ�ص���Ŀ
        	Node<E>h=head;
        	int i=0;
        	try{//�˴�����try--finallyΪ�˴���c.add()��������Ԫ��ʱ�׳����쳣�������������ܵ���head�ڵ�Ϊ��
        		while(i<n){
        			Node<E>p=h.next;//��ȡ��һ���ڵ�
        			c.add(p.item);//���Ԫ��
        			p.item=null;//help gc
        			h.next=h;//ɾ��h��ָ��ڵ�
        			h=p;//��hָ����һ���ڵ�
        			i++;
        		}
        		return n;
        	}finally{
        		if(i>0){
        			head=h;//��h�ڵ�ָ��ͷ��㣬h.item��Ϊnull
        			signalNotEmpty=(count.getAndAdd(-i))==capacity;//���count��ȥi֮ǰΪcapacity����ô��������Ȼ�������͵���signalNotEmpty
        		}
        	}
        }finally{
        	takeLock.unlock();
        	if(signalNotEmpty)
        		signalNotEmpty();
        }
	}
	/**
	 * ���ذ��ʵ�˳������˶���������Ԫ�ص����顣  ���ڴ˶��в�ά���Է���������κ����ã�������ǡ���ȫ�ġ���
	 * �����仰˵���˷����������һ�������飩����ˣ������߿��������޸ķ��ص����顣 
	 * �˷����䵱��������� API ����� collection �� API ֮���������
	 */
	public Object[] toArray(){
		int size=count.get();
	    fullyLock();
	    try{
	    	Object[] objs=new Object[size];
	    	int k=0;
	    	for(Node<E>p=head.next;p!=null;p=p.next){
	    			objs[k++]=p.item;
	    	}
	    	return objs;
	    }finally{
	    	unfullyLock();
	    }
	}
	/**
	 * ���ذ��ʵ�˳������˶���������Ԫ�ص����飻�������������ʱ������ָ�����������ʱ���͡�
	 * ���ָ�����������ɸö��У��������з����������򣬽�����һ������ָ�����������ʱ���ͺʹ˶��д�С�������顣 
	 * ���ָ�������������ɶ��У�����ʣ��Ŀռ䣨�������Ԫ�رȶ��еĶࣩ����ô�������н��Ӷ���β����Ԫ������Ϊ null�� 
	 * �� toArray() ����һ�����˷����䵱��������� API ����� collection �� API ֮���������
	 * ����һ��˵���˷��������������������ʱ���ͽ��о�ȷ���ƣ���ĳЩ����£�����������ʡ���俪���� 
	 * �ٶ� x ��ֻ�����ַ�����һ����֪���С����´����������ö���ת����һ���·���� String ���飺 
	 *       String[] y = x.toArray(new String[0]);ע�⣬toArray(new Object[0]) �� toArray() �ڹ���������ͬ�ġ�
	 */
	@SuppressWarnings("unchecked")
	public <T>T[] toArray(T[]a){
		fullyLock();
		try{
			int size=count.get();
			a=(T[])java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
			int k=0;
			for(Node<E>p=head.next;p!=null;p=p.next){
					a[k++]=(T)p.item;
			}
			if(a.length>k)
				a[k]=null;
			return a;
		}finally{
			unfullyLock();
		}
	}
	/**
	 * private �����ڲ��࣬��ôֻ�������ⲿ����ʸ��ڲ��࣬����ֻ���������ⲿ����ʹ��
	 * @author heyw
	 *
	 */
    private class Itr implements Iterator<E>{
    	 /*
    	  * ��������һ��iterator,���κ�ʱ�̶��п��Խ��ӵ���һ��item���Ա�����take��ʧȥ����ʱ��Ȼ���Խ��䷵��
    	  */
         private Node<E> current;//��ʾ��һ��nextҪ���صĽڵ�
         private  E currentItem;
         private Node<E> lastNode;//lastNode������ʾ��ǰnext()�׳��Ľڵ�
         
          Itr(){
        	  fullyLock();
        	  try{
        		  current=head.next;//currentһ��ʼָ��head��next
        		  if(current!=null)
             	   currentItem=current.item;
        	  }finally{
        		  unfullyLock();
        	  }
        	
         }
	
		public boolean hasNext() {
			return current!=null;
		}
        /**
         * Ѱ��node����һ��live successor
         * live sucessor ������item��Ϊnull����sΪnull
         * ע�����s==node˵����node�Ѿ������ˣ���ô����head
         * @param node
         * @return
         */
		private Node<E> nextNode(Node<E>node){
			for(;;){
				Node<E>s=node.next;
				if(s==node)
					return head.next;//���node�Ѿ��뿪���У���ô����head.next��head.next�����һ��Ԫ��
				if(s==null || s.item!=null){//s==null ˵���Ѿ�û��live successor��s.item��=null˵�����Ѿ���live successor��������������ǿ��Է��ص�
					return s;
				}
				node=s;
			}
		}
       /**
        * ���ص�ǰֵʱ����ҪΪcurrentѰ����һ���ڵ㣬����currentʼ�տ��Խ���node.item
        */
		public E next() {
			fullyLock();
			try{
				if(current==null) throw new IllegalStateException();//���currentΪnull�����׳��Ƿ�״̬�쳣����ΪhasNextͨ������next
				E x=currentItem;
				lastNode=current;
				current=nextNode(current);
				currentItem=current==null? null:current.item;
				return x;
			}finally{
				unfullyLock();
			}
		}

		/**
		 * ��head��ʼ�����ҵ�lastNode��pred��Ȼ��unlink����
		 */
		public void remove() {
			if(lastNode==null) throw new IllegalStateException();//�Ƿ�״̬�쳣 IllegalStateException
           Node<E> rmvNode=lastNode;
           fullyLock();
           try{
        	   lastNode=null;//help gc����������ĳ���ڵ�ʱ��һ��Ҫ�ж�ĳЩ�����Ƿ�ָ��������ô��Ҫ����Ϊnull
        	   for(Node<E>pred=head,p=head.next;p!=null;pred=p,p=p.next){
        		   if(p==rmvNode){
        			   unlink(pred, p);
        			   break;
        			   }
        	   }
           }finally{
        	   unfullyLock();
           }
		}
    }
}
