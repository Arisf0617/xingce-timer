import cn.xingce.timer.TimerEngine;
import java.util.Arrays;
public class TimerEngineTest {
    static long now;
    static void check(boolean valid,String text){if(!valid)throw new AssertionError(text);}
    public static void main(String[] args){
        TimerEngine e=new TimerEngine(()->now);
        check(e.totalBudget()==7200000,"default 120 minutes");
        e.start(100000);now+=1200;e.select(1);now+=2300;
        check(Arrays.equals(e.times(),new long[]{1200,2300,0,0,0,0}),"switch allocates each segment exactly");
        e.pause();now+=500000;check(e.elapsed()==3500,"pause excludes elapsed time");
        e.start(200000);now+=1700;e.select(0);now+=800;
        check(Arrays.equals(e.times(),new long[]{2000,4000,0,0,0,0}),"returning accumulates");
        e.start(999999);check(e.startedAt==100000,"repeated start preserves origin");
        now+=8000000;check(e.elapsed()>e.totalBudget(),"overtime continues");
        boolean denied=false;try{e.configure(new long[]{60000,60000,60000,60000,60000,60000});}catch(IllegalStateException ex){denied=true;}check(denied,"active config rejected");
        // Simulate process recreation during the same boot using a persisted snapshot.
        TimerEngine recovered=new TimerEngine(()->now);recovered.spent=e.times();recovered.anchor=now;recovered.running=true;recovered.active=e.active;recovered.startedAt=e.startedAt;
        now+=120000;check(recovered.elapsed()==e.elapsed(),"process recovery counts background time once");
        recovered.pause();now+=10000;check(!recovered.running,"pause clears running");
        e.reset();check(e.elapsed()==0&&!e.running&&e.startedAt==0,"reset clears session");
        e.configure(new long[]{60000,120000,180000,240000,300000,360000});check(e.totalBudget()==1260000,"custom budget");
        now=9000000000L;e.start(300000);now+=3600000000L;check(e.elapsed()==3600000000L,"long duration avoids integer overflow");
        e.pause();System.out.println("PASS: timer switching, pause/resume, accumulation, overtime, recovery, validation, reset and long durations");
    }
}
