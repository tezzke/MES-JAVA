package com.mes.acquisition.hosting;

import com.mes.core.contract.BarcodeRepository;
import com.mes.core.contract.RealtimeNotifier;
import com.mes.core.entity.BarcodeRecord;
import com.mes.core.hosting.BackgroundService;
import com.mes.core.options.AcquisitionOptions;
import com.mes.core.options.ScannerOptions;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 扫码枪接入服务(后台常驻)。
 * <p>
 * 真实模式:开一个 TCP 监听端口(默认 6001),网口扫码枪以 TCP Client 方式连入,
 * 每扫一枪推送一行文本(条码 + 换行),本服务按行解析后入库并实时推送;
 * 通过来源 IP 在 scannerBindings 中映射到绑定的设备/工位。
 * <p>
 * 模拟模式:定时生成假条码,走完全相同的入库/推送链路,便于前端联调。
 */
@Component
@Order(40)
public class BarcodeScannerHostedService extends BackgroundService {

    private static final DateTimeFormatter BARCODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ScannerOptions options;
    private final boolean simulation;
    private final BarcodeRepository repository;
    private final RealtimeNotifier notifier;
    private final Random random = new Random();

    /** 真实模式下的监听 socket:停机时需要主动关闭以唤醒阻塞的 accept()。 */
    private volatile ServerSocket listener;

    public BarcodeScannerHostedService(ScannerOptions options,
                                       AcquisitionOptions acquisitionOptions,
                                       BarcodeRepository repository,
                                       RealtimeNotifier notifier) {
        this.options = options;
        this.simulation = acquisitionOptions.isSimulation();
        this.repository = repository;
        this.notifier = notifier;
    }

    @Override
    protected void execute() throws Exception {
        if (!options.isEnabled()) {
            log.info("扫码枪接入已禁用");
            return;
        }
        if (simulation) {
            runSimulation();
        } else {
            runTcpListener();
        }
    }

    @Override
    protected void onStopRequested() {
        ServerSocket current = listener;
        if (current != null) {
            try {
                current.close(); // 唤醒阻塞在 accept() 上的线程
            } catch (IOException ignored) {
                // 停机路径,忽略
            }
        }
    }

    // ---------------- 模拟模式 ----------------

    /** 定时生成假条码:轮流从各绑定的扫码枪"扫出"一个序列号。 */
    private void runSimulation() {
        log.info("扫码枪模拟已启动,间隔 {}ms", options.getSimulateIntervalMs());

        List<String> scannerIds = new ArrayList<>(options.getScannerBindings().keySet());
        if (scannerIds.isEmpty()) {
            scannerIds.add("SIM-SCANNER-01");
        }

        int index = 0;
        while (!isStopping()) {
            if (!delay(options.getSimulateIntervalMs())) {
                break;
            }
            try {
                String scannerId = scannerIds.get(index++ % scannerIds.size());
                // 假条码格式:SN + 日期 + 随机序号,模拟产品序列号
                String barcode = "SN" + LocalDate.now().format(BARCODE_DATE)
                        + "-" + (100000 + random.nextInt(900000));
                ingest(scannerId, barcode);
            } catch (Exception ex) {
                log.error("扫码模拟异常", ex);
            }
        }
    }

    // ---------------- 真实模式:TCP 监听 ----------------

    /** 监听 TCP 端口,接收多把扫码枪的并发连接。 */
    private void runTcpListener() throws IOException {
        ExecutorService clientPool = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "scanner-client");
            thread.setDaemon(true);
            return thread;
        });

        try (ServerSocket server = new ServerSocket(options.getListenPort())) {
            listener = server;
            log.info("扫码枪 TCP 监听已启动,端口 {}", options.getListenPort());

            while (!isStopping()) {
                try {
                    Socket client = server.accept();
                    // 每把扫码枪一个独立接收线程
                    clientPool.submit(() -> handleScannerClient(client));
                } catch (IOException ex) {
                    if (isStopping()) {
                        break; // 停机时关闭监听导致的异常,属正常路径
                    }
                    log.warn("接受扫码枪连接失败:{}", ex.getMessage());
                }
            }
        } finally {
            listener = null;
            clientPool.shutdownNow();
            log.info("扫码枪 TCP 监听已停止");
        }
    }

    /** 处理单把扫码枪的连接:按行读取条码文本。 */
    private void handleScannerClient(Socket client) {
        String scannerId = client.getInetAddress() != null
                ? client.getInetAddress().getHostAddress()
                : "unknown";
        log.info("扫码枪已连接:{}", scannerId);

        try (Socket socket = client;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!isStopping() && (line = reader.readLine()) != null) {
                String barcode = line.trim();
                if (!barcode.isEmpty()) {
                    ingest(scannerId, barcode);
                }
            }
        } catch (Exception ex) {
            log.warn("扫码枪 {} 连接异常断开:{}", scannerId, ex.getMessage());
        }
        log.info("扫码枪已断开:{}", scannerId);
    }

    // ---------------- 公共入库链路(两种模式共用) ----------------

    /** 条码入库 + 实时推送。 */
    private void ingest(String scannerId, String barcode) {
        BarcodeRecord record = new BarcodeRecord(
                scannerId, options.deviceOf(scannerId), barcode, Instant.now());

        repository.insert(record);
        notifier.pushBarcode(record);
        log.debug("收到条码 {}(来源 {})", barcode, scannerId);
    }
}
