package com.cappleapple.openlights.client.scene;

import java.util.concurrent.*;

/** One replaceable job. Client-thread owner; cancellation never waits for the worker. */
final class LightingTask<T> {
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), runnable -> {
                Thread thread = new Thread(runnable,"OpenLights-lighting");
                thread.setDaemon(true); thread.setPriority(Thread.NORM_PRIORITY-1); return thread;
            });
    private final ExecutorService executor;
    private Future<T> future;
    private long version;
    LightingTask() { this(WORKER); }
    LightingTask(ExecutorService executor) { this.executor=executor; }
    void submit(long version, Callable<T> work) {
        cancel(); this.version=version; future=executor.submit(work);
    }
    T poll(long currentVersion) {
        if(future==null)return null;
        if(version!=currentVersion){cancel();return null;}
        if(!future.isDone())return null;
        Future<T> completed=future;future=null;
        try{return completed.get();}
        catch(CancellationException ignored){return null;}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
        catch(ExecutionException failure){throw new IllegalStateException("Lighting worker failed",failure.getCause());}
    }
    boolean active(){return future!=null;}
    boolean done(){return future!=null&&future.isDone();}
    void cancel() {
        if(future==null)return;
        future.cancel(true);
        if(executor instanceof ThreadPoolExecutor pool && future instanceof Runnable runnable)pool.remove(runnable);
        future=null;
    }
}
