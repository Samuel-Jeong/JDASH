package util.fsm.module.base;

/**
 * @author jamesj
 * @class public abstract class AbstractTaskUnit implements Runnable
 * @brief Task Unit Abstract Class
 */
public abstract class AbstractStateTaskUnit implements Runnable {

    private int interval;

    ////////////////////////////////////////////////////////////////////////////////

    protected AbstractStateTaskUnit(int interval) {
        this.interval = interval;
    }

    ////////////////////////////////////////////////////////////////////////////////

    public int getInterval( ) {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }
}
