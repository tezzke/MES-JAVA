package com.mes.core.hosting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 后台常驻服务基类(对应 .NET 的 BackgroundService)。
 * <p>
 * 提供统一的"独立线程 + 优雅停机"骨架,子类只实现 {@link #execute()} 里的循环体:
 * <ul>
 *   <li>{@link #isStopping()} 为 true 时子类应尽快退出循环;</li>
 *   <li>{@link #delay(long)} 代替 Thread.sleep,停机时立即返回 false;</li>
 *   <li>阻塞在 IO 上无法被中断唤醒的子类,可重写 {@link #onStopRequested()} 主动关闭 socket。</li>
 * </ul>
 * 由组合根(mes-api 的 BackgroundServiceRunner)按顺序统一启停,
 * 因此本类不依赖 Spring,保持领域核心零框架依赖。
 */
public abstract class BackgroundService {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private volatile boolean stopping;
    private Thread worker;

    /** 服务名称(线程名与日志用)。 */
    public String serviceName() {
        return getClass().getSimpleName();
    }

    /** 启动后台线程;重复调用无副作用。 */
    public final synchronized void start() {
        if (worker != null) {
            return;
        }
        stopping = false;
        worker = new Thread(this::runGuarded, serviceName());
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 请求停机并等待线程退出。
     *
     * @param joinMillis 等待线程结束的最长时间(毫秒)。
     */
    public final synchronized void stop(long joinMillis) {
        if (worker == null) {
            return;
        }
        stopping = true;
        try {
            onStopRequested();
        } catch (Exception ex) {
            log.warn("{} 停机预处理异常:{}", serviceName(), ex.toString());
        }
        worker.interrupt();
        try {
            worker.join(joinMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        worker = null;
    }

    /** 是否已收到停机请求。 */
    protected final boolean isStopping() {
        return stopping || Thread.currentThread().isInterrupted();
    }

    /**
     * 可中断的等待。
     *
     * @return true = 正常等待结束;false = 收到停机请求,子类应立即退出循环。
     */
    protected final boolean delay(long millis) {
        if (isStopping()) {
            return false;
        }
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return !isStopping();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 停机时的额外处理(如关闭监听 socket 以唤醒阻塞的 accept)。 */
    protected void onStopRequested() {
    }

    /** 子类实现的服务主体;返回即视为服务结束。 */
    protected abstract void execute() throws Exception;

    private void runGuarded() {
        try {
            execute();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // 正常停机
        } catch (Exception ex) {
            log.error("{} 异常退出", serviceName(), ex);
        }
    }
}
