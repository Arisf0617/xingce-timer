package cn.xingce.timer;

/** All durations are milliseconds. Only monotonic time is used for active sessions. */
public final class TimerEngine {
    public interface Clock { long now(); }
    private final Clock clock;
    public long[] spent = new long[6];
    public long[] budgets = {600000,600000,2100000,900000,1800000,1200000};
    public int active = 0;
    public boolean running = false;
    public long anchor, startedAt = 0;
    public TimerEngine(Clock clock) { this.clock = clock; }
    public synchronized long[] times() {
        long[] result = spent.clone();
        if(running) result[active] += Math.max(0, clock.now()-anchor);
        return result;
    }
    private void settle() { long now=clock.now(); if(running)spent[active]+=Math.max(0,now-anchor); anchor=now; }
    public synchronized void start(long wallTime) { if(running)return; if(startedAt==0)startedAt=wallTime; anchor=clock.now(); running=true; }
    public synchronized void pause() { settle(); running=false; }
    public synchronized void select(int index) { if(index<0||index>5)throw new IllegalArgumentException(); settle(); active=index; }
    public synchronized void reset() { running=false; spent=new long[6]; active=0; startedAt=0; anchor=clock.now(); }
    public synchronized void configure(long[] values) {
        if(startedAt!=0)throw new IllegalStateException("请先结束本次计时");
        if(values.length!=6)throw new IllegalArgumentException();
        for(long v:values)if(v<60000||v>36000000)throw new IllegalArgumentException();
        budgets=values.clone();
    }
    public synchronized long totalBudget() { long n=0; for(long v:budgets)n+=v; return n; }
    public synchronized long elapsed() { long n=0; for(long v:times())n+=v; return n; }
}
