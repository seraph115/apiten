package cn.iocoder.yudao.module.flow.consumer;

import cn.apiten.common.flow.FlowEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "apiten.org-flow",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class FlowEventConsumerTest {

    @Autowired KafkaTemplate<String, String> template;
    @Autowired FlowEventConsumer consumer;

    @Test
    void onEvent_deserializesAndStores() throws Exception {
        FlowEvent e = new FlowEvent();
        e.setFlowNo("123");
        e.setProductCode("P1001001");
        e.setPlatformCode("0000");
        e.setCharged(true);
        e.setCostTimeMs(50);
        e.setRequestTimeEpochMs(System.currentTimeMillis());
        template.send("apiten.org-flow", new ObjectMapper().writeValueAsString(e));

        await().atMost(10, SECONDS).untilAsserted(() ->
                assertThat(consumer.received()).extracting(FlowEvent::getFlowNo).contains("123"));
    }
}
