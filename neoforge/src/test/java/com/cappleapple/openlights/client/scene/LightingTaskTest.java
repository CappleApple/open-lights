package com.cappleapple.openlights.client.scene;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class LightingTaskTest {
    @Test void canceledOldJobCannotReplaceNewerResult() throws Exception {
        var executor=Executors.newSingleThreadExecutor();
        var task=new LightingTask<String>(executor);
        CountDownLatch started=new CountDownLatch(1),released=new CountDownLatch(1),fresh=new CountDownLatch(1);
        try {
            task.submit(1,()->{started.countDown();try{released.await();}catch(InterruptedException ignored){}return "stale";});
            assertTrue(started.await(2,TimeUnit.SECONDS));
            task.submit(2,()->{fresh.countDown();return "current";});
            released.countDown();assertTrue(fresh.await(2,TimeUnit.SECONDS));
            String value=null;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(value==null&&System.nanoTime()<deadline){value=task.poll(2);Thread.yield();}
            assertEquals("current",value);assertFalse(task.active());
        }finally{released.countDown();task.cancel();executor.shutdownNow();}
    }
    @Test void completedResultFromPreviousGenerationIsDiscarded() throws Exception {
        var executor=Executors.newSingleThreadExecutor();var task=new LightingTask<Integer>(executor);
        CountDownLatch done=new CountDownLatch(1);
        try{task.submit(7,()->{done.countDown();return 7;});assertTrue(done.await(2,TimeUnit.SECONDS));assertNull(task.poll(8));assertFalse(task.active());}
        finally{task.cancel();executor.shutdownNow();}
    }
    @Test void workerFailureIsReportedWithoutBlockingOwner() throws Exception {
        var executor=Executors.newSingleThreadExecutor();var task=new LightingTask<Integer>(executor);
        try{
            task.submit(1,()->{throw new IllegalArgumentException("fixture");});
            executor.shutdown();assertTrue(executor.awaitTermination(2,TimeUnit.SECONDS));
            var failure=assertThrows(IllegalStateException.class,()->task.poll(1));assertInstanceOf(IllegalArgumentException.class,failure.getCause());
        }finally{task.cancel();executor.shutdownNow();}
    }
}
