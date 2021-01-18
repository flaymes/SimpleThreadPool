import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleThreadPool implements ThreadPool {

    private int count = -1;
    private int priority = Thread.NORM_PRIORITY;

    private boolean isShutdown = false;
    private boolean handoffPending = false;
    private final Object nextRunnableLock = new Object();
    private List<WorkerThread> workers;
    private LinkedList<WorkerThread> availWorkers = new LinkedList<>();
    private LinkedList<WorkerThread> busyWorkers = new LinkedList<>();

    private ThreadGroup threadGroup;
    private String threadNamePrefix;
    private boolean inheritLoader = false;
    private boolean inheritGroup = true;
    private boolean makeThreadsDaemons = false;

    public SimpleThreadPool() {
    }

    public SimpleThreadPool(int count, int priority) {
        setCount(count);
        setThreadPriority(priority);
    }

    @Override
    public boolean runInThread(Runnable runnable) {
        if (runnable == null) {
            return false;
        }

        synchronized (nextRunnableLock) {
            handoffPending = true;

            while ((availWorkers.size() < 1) && !isShutdown) {
                try {
                    nextRunnableLock.wait(500);
                } catch (InterruptedException ignored) {
                }
            }

            if (!isShutdown) {
                WorkerThread wt = availWorkers.removeFirst();
                busyWorkers.add(wt);
                wt.run(runnable);
            } else {
                WorkerThread wt = new WorkerThread(this, threadGroup,
                        "WorkerThread-LastJob", priority, isMakeThreadsDaemons(), runnable);
                busyWorkers.add(wt);
                workers.add(wt);
                wt.start();
            }
            nextRunnableLock.notifyAll();
            handoffPending = false;
        }
        return true;
    }

    @Override
    public int blockForAvailableThreads() {
        synchronized (nextRunnableLock) {
            while ((availWorkers.size() < 1 || handoffPending) && !isShutdown) {
                try {
                    nextRunnableLock.wait(500);
                } catch (InterruptedException ignore) {
                }
            }
        }
        return availWorkers.size();
    }

    @Override
    public void initialize() {
        if (workers != null && workers.size() > 0) {
            System.out.println("WARN:already initialized....");
            return;
        }
        if (count <= 0 || priority > 9) {
            throw new IllegalArgumentException("ThreadPool argument must available.");
        }

        if (isThreadsInheritGroupOfInitializingThread()) {
            threadGroup = Thread.currentThread().getThreadGroup();
        } else {
            threadGroup = Thread.currentThread().getThreadGroup();
            ThreadGroup parent = threadGroup;
            while (!parent.getName().equals("main")) {
                threadGroup = parent;
                parent = threadGroup.getParent();
            }
            threadGroup = new ThreadGroup(parent, "ThreadGroup-SimpleThreadPool");
            if (isMakeThreadsDaemons()) {
                threadGroup.setDaemon(true);
            }
        }

        if (isThreadsInheritContextClassLoaderOfInitializingThread()) {
            System.out.println("Job execution threads will use class loader of thread: "
                    + Thread.currentThread().getName());
        }

        for (WorkerThread wt : createWorkerThreads(count)) {
            wt.start();
            availWorkers.add(wt);
        }

    }

    protected List<WorkerThread> createWorkerThreads(int createCount) {
        workers = new LinkedList<>();
        for (int i = 0; i <= createCount; i++) {
            String threadPrefix = getThreadNamePrefix();
            if (threadPrefix == null) {
                threadPrefix = "SimpleThreadPool_Worker";
            }
            WorkerThread wt = new WorkerThread(
                    this,
                    threadGroup,
                    threadPrefix + "-" + i,
                    getThreadPriority(),
                    isMakeThreadsDaemons());
            if (isThreadsInheritContextClassLoaderOfInitializingThread()) {
                wt.setContextClassLoader(Thread.currentThread().getContextClassLoader());
            }
            workers.add(wt);
        }
        return workers;
    }

    public boolean isThreadsInheritGroupOfInitializingThread() {
        return inheritGroup;
    }

    public void setThreadsInheritGroupOfInitializingThread(
            boolean inheritGroup) {
        this.inheritGroup = inheritGroup;
    }

    public boolean isThreadsInheritContextClassLoaderOfInitializingThread() {
        return inheritLoader;
    }

    /**
     * @param inheritLoader The threadsInheritContextClassLoaderOfInitializingThread to
     *                      set.
     */
    public void setThreadsInheritContextClassLoaderOfInitializingThread(
            boolean inheritLoader) {
        this.inheritLoader = inheritLoader;
    }

    public void setThreadNamePrefix(String prfx) {
        this.threadNamePrefix = prfx;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public boolean isMakeThreadsDaemons() {
        return makeThreadsDaemons;
    }

    /**
     * @param makeThreadsDaemons The value of makeThreadsDaemons to set.
     */
    public void setMakeThreadsDaemons(boolean makeThreadsDaemons) {
        this.makeThreadsDaemons = makeThreadsDaemons;
    }

    public void shutdown() {
        shutdown(true);
    }

    @Override
    public void shutdown(boolean waitForJobsToComplete) {
        synchronized (nextRunnableLock) {
            System.out.println("Shutting down threadPool...");
            isShutdown = true;
            if (workers == null) {
                return;
            }
            for (WorkerThread wt : workers) {
                wt.shutdown();
                availWorkers.remove(wt);
            }
            nextRunnableLock.notifyAll();
            if (waitForJobsToComplete) {
                boolean interrupted = false;
                try {

                    while (handoffPending) {
                        try {
                            nextRunnableLock.wait(100);
                        } catch (InterruptedException e_) {
                            interrupted = true;
                        }
                    }

                    // Wait until all worker threads are shut down
                    while (busyWorkers.size() > 0) {
                        WorkerThread wt = busyWorkers.getFirst();
                        System.out.println("Waiting for thread " + wt.getName()
                                + " to shut down");
                        try {
                            nextRunnableLock.wait(2000);
                        } catch (InterruptedException e_) {
                            interrupted = true;
                        }
                    }


                    Iterator<WorkerThread> workerThreads = workers.iterator();
                    while (workerThreads.hasNext()) {
                        WorkerThread wt = workerThreads.next();
                        try {
                            wt.join();
                            workerThreads.remove();
                        } catch (InterruptedException e_) {
                            interrupted = true;
                        }
                    }
                } finally {
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
                System.out.println("No executing jobs remaining, all threads stopped.");
            }
            System.out.println("Shutdown of threadPool complete.");
        }
    }

    @Override
    public int getPoolSize() {
        return 0;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void setThreadPriority(int prio) {
        this.priority = prio;
    }

    /**
     * <p>
     * Get the thread priority of worker threads in the pool.
     * </p>
     */
    public int getThreadPriority() {
        return priority;
    }


    class WorkerThread extends Thread {
        private final Object lock = new Object();

        private AtomicBoolean run = new AtomicBoolean(true);
        private SimpleThreadPool threadPool;
        private Runnable runnable = null;
        private boolean runOnce = false;

        public WorkerThread(SimpleThreadPool pool, ThreadGroup group, String name, int prio, boolean isDaemon) {
            this(pool, threadGroup, name, prio, isDaemon, null);
        }

        WorkerThread(SimpleThreadPool pool, ThreadGroup threadGroup, String name, int prio, boolean isDaemon, Runnable runnable) {
            super(threadGroup, name);
            this.threadPool = pool;
            this.runnable = runnable;
            if (runnable != null) {
                runOnce = true;
            }
            setPriority(prio);
            setDaemon(isDaemon);
        }

        void shutdown() {
            run.set(false);
        }

        public void run(Runnable newRunnable) {
            synchronized (lock) {
                if (runnable != null) {
                    throw new IllegalStateException("Already running a Runnable!");
                }
                runnable = newRunnable;
                lock.notifyAll();
            }
        }

        @Override
        public void run() {
            boolean ran = false;
            while (run.get()) {

                try {
                    synchronized (lock) {
                        while (runnable == null && run.get()) {
                            lock.wait(500);
                        }

                        if (runnable != null) {
                            ran = true;
                            runnable.run();
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    System.err.println("Worker thread was interrupt()'ed." + e.getMessage());
                } finally {
                    synchronized (lock) {
                        runnable = null;
                    }
                    if (getPriority() != threadPool.getThreadPriority()) {
                        setPriority(threadPool.getThreadPriority());
                    }

                    if (runOnce) {
                        run.set(false);
                        clearFromBusyWorkersList(this);
                    } else if (ran) {
                        ran = false;
                        makeAvailable(this);
                    }
                }
            }
            System.err.println("WorkerThread is shut down.");
        }
    }


    protected void makeAvailable(WorkerThread wt) {
        synchronized (nextRunnableLock) {

            if (!isShutdown) {
                availWorkers.add(wt);
            }

            busyWorkers.remove(wt);
            nextRunnableLock.notifyAll();
        }
    }

    protected void clearFromBusyWorkersList(WorkerThread wt) {
        synchronized (nextRunnableLock) {
            busyWorkers.remove(wt);
            nextRunnableLock.notifyAll();
        }
    }

}
