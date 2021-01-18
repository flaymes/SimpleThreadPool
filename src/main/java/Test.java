public class Test {
    public static void main(String[] args) {
        SimpleThreadPool pool=new SimpleThreadPool(10,5);
        pool.initialize();
        for (int i = 0; i <20 ; i++) {
            pool.runInThread(new TestRunnable());
        }
        pool.shutdown();
    }
}

class TestRunnable implements Runnable{

    @Override
    public void run() {
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(Thread.currentThread().getName()+" Hello World!");
    }
}
