package com.mes.core.contract;

import com.mes.core.entity.AlarmRecord;
import com.mes.core.entity.BarcodeRecord;
import com.mes.core.model.DeviceSnapshot;

import java.util.List;

/**
 * 实时推送接口:处理层通过它把数据推给前端,但不感知具体推送技术。
 * 实现:{@code com.mes.api.realtime.WebSocketRealtimeNotifier}(基于 WebSocket 广播)。
 * 这样处理/存储层不依赖 Web 框架,保持分层隔离 —— 推送技术换成 MQTT/SSE 也不影响业务代码。
 */
public interface RealtimeNotifier {

    /** 推送一批设备快照(每个轮询节拍推一次)。 */
    void pushSnapshots(List<DeviceSnapshot> snapshots);

    /** 推送新触发或已恢复的报警。 */
    void pushAlarm(AlarmRecord alarm);

    /** 推送新的扫码记录。 */
    void pushBarcode(BarcodeRecord barcode);
}
