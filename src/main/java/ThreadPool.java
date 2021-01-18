public interface ThreadPool {

    /**
     * 在可用的线程中执行 runnable。如果当前没有可用线程返回 false，否则 true。
     * @param runnable
     * @return
     */
    boolean runInThread(Runnable runnable);

    /**
     * 线程池中当前有多少可用线程
     * @return
     */
    int blockForAvailableThreads();

    void initialize();

    void shutdown(boolean waitForJobsToComplete);

    int getPoolSize();
}
