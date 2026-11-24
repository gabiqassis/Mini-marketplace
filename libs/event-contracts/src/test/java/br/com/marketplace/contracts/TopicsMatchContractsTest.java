package br.com.marketplace.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** TRAVA SDD: codigo, spec e infraestrutura nao podem divergir. */
class TopicsMatchContractsTest {

    private static final Path ROOT = Path.of("../..");

    @Test
    void topicsInCodeAreExactlyTheChannelsInTheSpec() throws Exception {
        String yaml = Files.readString(ROOT.resolve("docs/contracts/asyncapi.yaml"));
        Set<String> inSpec = new HashSet<>();
        Matcher m = Pattern.compile("(?m)^  ([a-z][a-z.-]*\\.v\\d+):\\s*$").matcher(yaml);
        while (m.find()) {
            inSpec.add(m.group(1));
        }
        assertThat(new HashSet<>(Topics.ALL)).isEqualTo(inSpec);
    }

    @Test
    void topicCreationScriptCreatesEveryTopic() throws Exception {
        String script = Files.readString(ROOT.resolve("infra/kafka/create-topics.sh")).replace("\r\n", "\n");
        for (String topic : Topics.ALL) {
            assertThat(script).as("create-topics.sh deve criar %s", topic).contains("\n" + topic + "\n");
        }
    }

    @Test
    void rabbitTopologyDeclaresEveryQueueWithRetryAndParking() throws Exception {
        String definitions = Files.readString(ROOT.resolve("infra/rabbitmq/definitions.json"));
        for (String queue : new String[] {Queues.INVOICE_ISSUE, Queues.NOTIFICATION_SEND}) {
            assertThat(definitions).contains("\"" + queue + "\"");
            assertThat(definitions).contains("\"" + Queues.retryOf(queue) + "\"");
            assertThat(definitions).contains("\"" + Queues.parkingOf(queue) + "\"");
        }
    }

    @Test
    void allListCoversEveryConstant() throws Exception {
        Set<String> constants = new HashSet<>();
        for (Field f : Topics.class.getFields()) {
            if (f.getType() == String.class) {
                constants.add((String) f.get(null));
            }
        }
        assertThat(new HashSet<>(Topics.ALL)).isEqualTo(constants);
    }
}
