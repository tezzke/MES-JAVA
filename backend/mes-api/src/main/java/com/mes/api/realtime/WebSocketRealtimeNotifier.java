package com.mes.api.realtime;

import com.mes.core.contract.RealtimeNotifier;
import com.mes.core.entity.AlarmRecord;
import com.mes.core.entity.BarcodeRecord;
import com.mes.core.model.DeviceSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link RealtimeNotifier} 的 WebSocket 实现:把处理层的推送请求广播给所有已连接的前端。
 * <p>
 * 处理层只依赖 RealtimeNotifier 抽象,因此推送技术(WebSocket / MQTT / SSE)可整体替换,
 * 而 mes-infrastructure 不需要引入任何 Web 依赖。
 */
@Component
public class WebSocketRealtimeNotifier implements RealtimeNotifier {

    /** 事件名与 .NET + SignalR 版本保持一致,前端代码无需区分后端实现。 */
    private static final String EVENT_SNAPSHOTS = "OnSnapshots";
    private static final String EVENT_ALARM = "OnAlarm";
    private static final String EVENT_BARCODE = "OnBarcode";

    private final RealtimeWebSocketHandler handler;

    public WebSocketRealtimeNotifier(RealtimeWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void pushSnapshots(List<DeviceSnapshot> snapshots) {
        handler.broadcast(EVENT_SNAPSHOTS, snapshots);
    }

    @Override
    public void pushAlarm(AlarmRecord alarm) {
        handler.broadcast(EVENT_ALARM, alarm);
    }

    @Override
    public void pushBarcode(BarcodeRecord barcode) {
        handler.broadcast(EVENT_BARCODE, barcode);
    }
}
