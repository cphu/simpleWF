package com.sfeir.simplewf.fit;

import java.util.Date;

public class FitData {
    private Date start;
    private Date end;
    private long steps;

    public FitData() {
    }

    public FitData(Date dteStart, Date dteEnd, long intValue) {
        start = dteStart;
        end = dteEnd;
        steps = intValue;
    }

    public void setFitData(FitData fitData) {
        this.start = fitData.start;
        this.end = fitData.end;
        this.steps = fitData.steps;
    }

    public Date getStart() {
        return start;
    }

    public Date getEnd() {
        return end;
    }

    public long getSteps() {
        return steps;
    }

    @Override
    public String toString() {
        return "FitData{" + "start=" + start + ", end=" + end + ", steps=" + steps + '}';
    }
}
