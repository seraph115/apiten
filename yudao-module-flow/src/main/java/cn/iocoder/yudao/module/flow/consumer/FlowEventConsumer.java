package cn.iocoder.yudao.module.flow.consumer;

import cn.apiten.common.flow.FlowEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class FlowEventConsumer {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<FlowEvent> received = new CopyOnWriteArrayList<>();

    @KafkaListener(topics = "apiten.org-flow", groupId = "flow-server")
    public void onEvent(String json) throws Exception {
        received.add(mapper.readValue(json, FlowEvent.class));
    }

    public List<FlowEvent> received() {
        return received;
    }
}
