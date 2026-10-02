package com.cappleapple.openlights.client.scene;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AggregateSchedulingTest {
    private static Object get(Object owner,String name) throws Exception {
        Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    private static void set(Object owner,String name,Object value) throws Exception {
        Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.set(owner,value);
    }
    private static void collect(AggregateLightCache cache) throws Exception {
        Method method=AggregateLightCache.class.getDeclaredMethod("collectCompleted");method.setAccessible(true);method.invoke(cache);
    }
    @SuppressWarnings("unchecked")
    private static LightingTask<AggregatePropagation.Result> task(AggregateLightCache cache) throws Exception {
        return (LightingTask<AggregatePropagation.Result>)get(cache,"task");
    }
    private static AggregateLightCache cache() throws Exception {
        var cache=new AggregateLightCache();set(cache,"radius",2);set(cache,"jobRadius",2);
        set(cache,"broker",new LightMaterialSnapshots.Broker(Map.of(),64));return cache;
    }
    private static AggregatePropagation.Result result() {
        var field=new Long2IntOpenHashMap();field.put(BlockPos.asLong(0,0,0),0xffffffff);
        return new AggregatePropagation.Result(field,Map.of(0L,new byte[18*18*18*4]),Set.of(0L),false,"test",1,Map.of(),true);
    }
    private static void awaitDone(LightingTask<?> task) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(!task.done()&&System.nanoTime()<until)Thread.sleep(1);
        assertTrue(task.done(),"Worker must finish despite edit traffic");
    }
    @Test void continuousEditsAndDiscoveryCannotStarveRunningOrCompletedResult() throws Exception {
        var cache=cache();var task=task(cache);var release=new CountDownLatch(1);
        try {
            task.submit(0,()->{release.await();return result();});
            for(int i=0;i<1000;i++) {
                cache.invalidate(10,0,10,10,0,10);
                cache.replaceSources(0,List.of(new AggregateLightCache.Source(BlockPos.asLong(0,0,0),1+i%15,0xffffff)));
            }
            assertTrue(task.active());assertEquals(0,cache.discarded());
            release.countDown();awaitDone(task);
            // The same notification after completion previously threw away the finished future.
            cache.invalidate(10,0,10,10,0,10);collect(cache);
            assertEquals(1,cache.revision());assertEquals(0xffffffff,cache.sample(BlockPos.ZERO));
            assertTrue(cache.analytic());assertEquals(true,get(cache,"dirty"),"Edits must schedule a follow-up");
        } finally {release.countDown();cache.clear();}
    }
    @Test void unrelatedRegionsDoNotScheduleWorkAndDirtyNoticesDoNotDeleteSources() throws Exception {
        var cache=cache();var source=new AggregateLightCache.Source(0,15,0xffffff);
        try {
            cache.replaceSources(0,List.of(source));set(cache,"dirty",false);
            cache.invalidate(10000,0,10000,10000,0,10000);
            assertEquals(false,get(cache,"dirty"));
            cache.invalidate(0,0,0,0,0,0);
            assertEquals(List.of(source),((Map<?,?>)get(cache,"sourceSections")).get(0L));
            cache.replaceSources(0,List.of());
            assertTrue(((Map<?,?>)get(cache,"sourceSections")).isEmpty(),"Authoritative scans must remove emitters");
        } finally {cache.clear();}
    }
    @Test void completedApplicationSurvivesEditsAndBlocksLaterPublicationUntilDrained() throws Exception {
        var cache=cache();var task=task(cache);
        try {
            task.submit(0,AggregateSchedulingTest::result);awaitDone(task);collect(cache);
            var pending=(Map<?,?>)get(cache,"applying");assertEquals(1,pending.size());
            cache.invalidate(0,0,0,0,0,0);cache.replaceSources(0,List.of());
            assertEquals(1,pending.size(),"An edit must not abandon calculated lighting");
            set(cache,"broker",new LightMaterialSnapshots.Broker(Map.of(),64));
            task.submit(0,()->new AggregatePropagation.Result(new Long2IntOpenHashMap(),Map.of(),Set.of(),false,"test",1,Map.of(),true));
            awaitDone(task);collect(cache);assertEquals(1,cache.revision());assertTrue(task.active());
            pending.clear();collect(cache);
            assertEquals(2,cache.revision());assertEquals(0,cache.cells());
        } finally {cache.clear();}
    }
    @Test void invalidatingPartialSnapshotMarksItUnfitForReuseWithoutCancelingJob() throws Exception {
        var cache=cache();var capture=new LightMaterialSnapshots.Capture(SectionPos.asLong(0,0,0));
        try {
            set(cache,"capture",capture);cache.invalidate(1,1,1,1,1,1);
            assertEquals(true,get(cache,"captureInvalidated"));assertSame(capture,get(cache,"capture"));
        } finally {cache.clear();}
    }
    @Test void worldResetStillDiscardsOldWork() throws Exception {
        var cache=cache();var task=task(cache);var release=new CountDownLatch(1);
        try {
            task.submit(0,()->{release.await();return result();});cache.clear();
            assertFalse(task.active());assertEquals(0,cache.cells());assertFalse(cache.analytic());assertFalse(cache.pending());
        } finally {release.countDown();cache.clear();}
    }
}
