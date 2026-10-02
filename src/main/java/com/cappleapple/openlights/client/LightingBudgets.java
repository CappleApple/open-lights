package com.cappleapple.openlights.client;

import com.cappleapple.openlights.config.ClientConfig;

/** Presets raise client-side feed/apply limits; the background worker is not time-throttled. */
public enum LightingBudgets {
    ECONOMY(1024,2048,.35,4,2,.25), BALANCED(4096,8192,1,16,8,.5),
    FAST(16384,32768,3,64,32,2), RAPID(32768,65536,8,256,128,8);
    private final int samples,cells,apply,uploads;
    private final double captureMillis,uploadMillis;
    LightingBudgets(int samples,int cells,double captureMillis,int apply,int uploads,double uploadMillis){
        this.samples=samples;this.cells=cells;this.captureMillis=captureMillis;this.apply=apply;this.uploads=uploads;this.uploadMillis=uploadMillis;
    }
    public void apply(){
        ClientConfig.WORLD_SAMPLES_PER_TICK.set(samples);ClientConfig.COLOR_SAMPLES_PER_TICK.set(samples);
        ClientConfig.AGGREGATE_CELLS_PER_TICK.set(cells);ClientConfig.AGGREGATE_BUDGET_MILLIS.set(captureMillis);
        ClientConfig.AGGREGATE_APPLY_SECTIONS.set(apply);ClientConfig.LIGHT_UPLOAD_SECTIONS.set(uploads);ClientConfig.LIGHT_UPLOAD_MILLIS.set(uploadMillis);
        ClientConfig.WORLD_SAMPLES_PER_TICK.save();
    }
    public static LightingBudgets current(){
        for(var value:values())if(ClientConfig.WORLD_SAMPLES_PER_TICK.get()==value.samples&&ClientConfig.COLOR_SAMPLES_PER_TICK.get()==value.samples
                &&ClientConfig.AGGREGATE_CELLS_PER_TICK.get()==value.cells&&ClientConfig.AGGREGATE_BUDGET_MILLIS.get()==value.captureMillis
                &&ClientConfig.AGGREGATE_APPLY_SECTIONS.get()==value.apply&&ClientConfig.LIGHT_UPLOAD_SECTIONS.get()==value.uploads&&ClientConfig.LIGHT_UPLOAD_MILLIS.get()==value.uploadMillis)return value;
        return null;
    }
}
