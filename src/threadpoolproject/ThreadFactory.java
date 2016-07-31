package threadpoolproject;

public interface ThreadFactory {
      Thread newThread(Runnable command);
}
