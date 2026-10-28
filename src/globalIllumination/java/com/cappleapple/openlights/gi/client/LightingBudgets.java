package com.cappleapple.openlights.gi.client;

import com.cappleapple.openlights.gi.config.GlobalIlluminationConfig;

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
        GlobalIlluminationConfig.WORLD_SAMPLES_PER_TICK.set(samples);GlobalIlluminationConfig.COLOR_SAMPLES_PER_TICK.set(samples);
        GlobalIlluminationConfig.AGGREGATE_CELLS_PER_TICK.set(cells);GlobalIlluminationConfig.AGGREGATE_BUDGET_MILLIS.set(captureMillis);
        GlobalIlluminationConfig.AGGREGATE_APPLY_SECTIONS.set(apply);GlobalIlluminationConfig.LIGHT_UPLOAD_SECTIONS.set(uploads);GlobalIlluminationConfig.LIGHT_UPLOAD_MILLIS.set(uploadMillis);
        GlobalIlluminationConfig.WORLD_SAMPLES_PER_TICK.save();
    }
    public static LightingBudgets current(){
        for(var value:values())if(GlobalIlluminationConfig.WORLD_SAMPLES_PER_TICK.get()==value.samples&&GlobalIlluminationConfig.COLOR_SAMPLES_PER_TICK.get()==value.samples
                &&GlobalIlluminationConfig.AGGREGATE_CELLS_PER_TICK.get()==value.cells&&GlobalIlluminationConfig.AGGREGATE_BUDGET_MILLIS.get()==value.captureMillis
                &&GlobalIlluminationConfig.AGGREGATE_APPLY_SECTIONS.get()==value.apply&&GlobalIlluminationConfig.LIGHT_UPLOAD_SECTIONS.get()==value.uploads&&GlobalIlluminationConfig.LIGHT_UPLOAD_MILLIS.get()==value.uploadMillis)return value;
        return null;
    }
}
