package threadpoolproject;

public interface Executor {
	/**
	 * �ڽ���ĳ��ʱ���ִ�и��������񣬿��ǵ�{@code Executor}}��ʵ���࣬���������һ�����߳���ִ�У�������һ���̳߳���ִ�У����ߵ���
	 * һ���߳���ִ��
	 * @param command the run task
	 * @param RejectedExecutionException if the task cannot be accpeted for execution 
	 */
       void execute(Runnable command);
}
