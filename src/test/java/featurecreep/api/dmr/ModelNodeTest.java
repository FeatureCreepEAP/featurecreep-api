package featurecreep.api.dmr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModelNodeTest {

    @Test
    void parsesAndSerializesNestedJson() throws Exception {
        ModelNode node = ModelNode.fromJSONString("{\"name\":\"fc\",\"enabled\":true,\"values\":[1,2,3]}");
        assertEquals("fc", node.get("name").asString());
        assertTrue(node.get("enabled").asBoolean());
        assertEquals(3, node.get("values").asList().size());

        ModelNode roundTrip = ModelNode.fromJSONString(node.toJSONString(false));
        assertEquals("fc", roundTrip.get("name").asString());
        assertEquals(2, roundTrip.get("values").asList().get(1).asInt());
    }

    @Test
    void objectAndListBuildersPreserveValues() {
        ModelNode node = new ModelNode();
        node.get("count").set(7);
        node.get("items").add("a").add("b");
        assertEquals(7, node.get("count").asInt());
        assertEquals("b", node.get("items").asList().get(1).asString());
    }
}
